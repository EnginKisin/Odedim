package com.example.odedim

import android.content.Context
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PaymentAppState(
    val payments: List<Payment>,
    val salaryDay: Int,
    val notificationsEnabled: Boolean,
    val notificationHistory: List<NotificationRecord> = emptyList()
)

class PaymentStore private constructor(context: Context) {
    private val db = PaymentDatabase.create(context)
    private val dao = db.dao()
    private val scheduler = ReminderScheduler(context.applicationContext)
    private val mutex = Mutex()

    val state = combine(dao.observePayments(), dao.observeSettings(), dao.observeNotifications()) {
            payments, settings, notifications ->
        val preferences = settings ?: StoredSettings()
        PaymentAppState(payments.map { it.toPayment() }, preferences.salaryDay,
            preferences.notificationsEnabled, notifications)
    }

    private suspend fun snapshot(): PaymentAppState {
        val settings = dao.settings() ?: StoredSettings()
        return PaymentAppState(dao.allPayments().map { it.toPayment() }, settings.salaryDay, settings.notificationsEnabled)
    }

    suspend fun importLegacy(payments: List<Payment>, salaryDay: Int) = mutate {
        val settings = dao.settings() ?: StoredSettings()
        if (!settings.legacyImported) {
            if (dao.allPayments().isEmpty()) {
                payments.forEach { dao.savePayment(StoredPayment.from(it)) }
            }
            dao.saveSettings(settings.copy(
                salaryDay = if (payments.isNotEmpty() || salaryDay != 5) salaryDay else settings.salaryDay,
                legacyImported = true
            ))
        }
    }

    suspend fun add(
        name: String, amount: String, rule: RepeatRule,
        lastPaidOn: LocalDate?, time: LocalTime, currency: PaymentCurrency,
        firstDueDate: LocalDate? = null
    ) = mutate {
        val settings = dao.settings() ?: StoredSettings()
        val payment = createPayment(0, name, amount, rule, lastPaidOn, time,
            LocalDate.now(), settings.salaryDay, currency, firstDueDate)
        dao.savePayment(StoredPayment.from(payment))
    }

    suspend fun markPaid(id: Long) = change(id) { payment, settings ->
        payment.markPaid(LocalDate.now(), settings.salaryDay)
    }

    suspend fun undo(id: Long) = change(id) { payment, settings ->
        payment.undoLastPayment(settings.salaryDay)
    }

    suspend fun edit(
        id: Long, name: String, amount: String, rule: RepeatRule,
        dueDate: LocalDate, time: LocalTime, currency: PaymentCurrency
    ) = change(id) { payment, _ -> payment.edited(name, amount, rule, dueDate, time, currency) }

    suspend fun completeHistoryAmount(
        id: Long, index: Int, expectedRecord: PaymentRecord, amount: String, currency: PaymentCurrency
    ) = change(id) { payment, _ ->
        payment.completeHistoryAmount(index, expectedRecord, amount, currency)
    }

    suspend fun delete(id: Long) = mutate(setOf(id)) { dao.deletePayment(id) }

    suspend fun setSalaryDay(day: Int) = mutate(clearAll = true) {
        val preferences = dao.settings() ?: StoredSettings()
        writeSettings(day, preferences.notificationsEnabled)
    }

    suspend fun setTime(id: Long, time: LocalTime) = change(id) { payment, _ ->
        payment.copy(paymentTime = time.withSecond(0).withNano(0))
    }

    private suspend fun change(
        id: Long, transform: (Payment, StoredSettings) -> Payment
    ) = mutate(setOf(id)) {
        val payment = dao.payment(id)?.toPayment() ?: return@mutate
        dao.savePayment(StoredPayment.from(transform(payment, dao.settings() ?: StoredSettings())))
    }

    suspend fun saveSettings(salaryDay: Int, enabled: Boolean) = mutate(clearAll = true) {
        writeSettings(salaryDay, enabled)
    }

    private suspend fun writeSettings(salaryDay: Int, enabled: Boolean) {
        require(salaryDay in 1..31)
        val previous = dao.settings() ?: StoredSettings()
        dao.allPayments().forEach { stored ->
            dao.savePayment(StoredPayment.from(stored.toPayment().updateSalaryDay(salaryDay)))
        }
        dao.saveSettings(previous.copy(salaryDay = salaryDay, notificationsEnabled = enabled))
    }

    // A committed database write must also finish updating the native alarms if the UI is recreated.
    private suspend fun mutate(
        clearIds: Set<Long> = emptySet(),
        clearAll: Boolean = false,
        block: suspend () -> Unit
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            withContext(NonCancellable) {
                db.withTransaction { block() }
                scheduler.reschedule(snapshot(), clearIds, clearAll)
            }
        }
    }

    suspend fun reschedule() = withContext(Dispatchers.IO) {
        mutex.withLock { scheduler.reschedule(snapshot()) }
    }

    suspend fun markNotificationsRead(throughId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock { dao.markNotificationsRead(throughId) }
    }

    suspend fun clearNotifications() = withContext(Dispatchers.IO) {
        mutex.withLock { dao.clearNotifications() }
    }

    suspend fun deliver(id: Long, kind: ReminderKind, expectedDue: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val settings = dao.settings() ?: StoredSettings()
            if (!settings.notificationsEnabled) return@withLock
            val payment = dao.payment(id)?.toPayment() ?: return@withLock
            val plan = ReminderTimes.forPayment(payment, ZoneId.systemDefault()).firstOrNull { it.kind == kind }
                ?: return@withLock
            val now = Instant.now()
            if (plan.dueAt.toEpochMilli() != expectedDue || !plan.isDeliverableAt(now)) {
                return@withLock
            }
            if (dao.notificationExists(id, kind.name, expectedDue)) return@withLock
            val message = scheduler.showNotification(payment, kind) ?: return@withLock
            dao.recordNotification(NotificationRecord(
                paymentId = id, kind = kind.name, dueAt = expectedDue,
                title = payment.name, message = message, sentAt = Instant.now().toEpochMilli()
            ))
        }
    }

    companion object {
        @Volatile private var instance: PaymentStore? = null
        fun get(context: Context): PaymentStore = instance ?: synchronized(this) {
            instance ?: PaymentStore(context.applicationContext).also { instance = it }
        }
    }
}

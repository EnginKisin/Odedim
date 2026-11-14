package com.example.odedim

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationHistoryTest {
    private lateinit var db: PaymentDatabase
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(context, PaymentDatabase::class.java).build()
    }

    @After fun closeDatabase() = db.close()

    private fun record(index: Int, kind: ReminderKind = ReminderKind.DUE) = NotificationRecord(
        paymentId = 1, kind = kind.name, dueAt = 1000L + index,
        title = "Fatura $index", message = "Gönderilen hatırlatma $index",
        sentAt = 5000L + index
    )

    @Test fun fourRemindersForOnePaymentRemainFourSeparateRows() = runBlocking {
        ReminderKind.entries.forEach { kind -> db.dao().recordNotification(record(1, kind)) }
        val records = db.dao().allNotifications()
        assertEquals(4, records.size)
        assertEquals(ReminderKind.entries.map { it.name }.toSet(), records.map { it.kind }.toSet())
        assertTrue(records.all { !it.isRead })
        assertEquals(records, db.dao().observeNotifications().first())
    }

    @Test fun onlyTenLatestSendsRemainStoredEvenIfTheClockMovesBackwards() = runBlocking {
        repeat(14) { index ->
            db.dao().recordNotification(record(index).copy(sentAt = 10000L - index))
        }
        val records = db.dao().allNotifications()
        assertEquals(10, records.size)
        assertEquals((13 downTo 4).map { "Fatura $it" }, records.map { it.title })
        assertEquals(10, records.count { !it.isRead })
        assertFalse(db.dao().notificationExists(1, ReminderKind.DUE.name, 1000))
    }

    @Test fun duplicateDeliveryDoesNotAddARowButOtherKindsAndDueDatesDo() = runBlocking {
        db.dao().recordNotification(record(1))
        val first = db.dao().allNotifications().single()
        db.dao().recordNotification(record(1).copy(message = "Yinelenen kayıt"))
        assertEquals(listOf(first), db.dao().allNotifications())
        assertTrue(db.dao().notificationExists(1, ReminderKind.DUE.name, 1001))
        assertFalse(db.dao().notificationExists(1, ReminderKind.TWO_HOURS.name, 1001))
        assertFalse(db.dao().notificationExists(1, ReminderKind.DUE.name, 1002))
        db.dao().recordNotification(record(1, ReminderKind.TWO_HOURS))
        db.dao().recordNotification(record(2))
        assertEquals(3, db.dao().allNotifications().size)
    }

    @Test fun openingMarksExistingRecordsReadWithoutConsumingALaterUnseenNotification() = runBlocking {
        repeat(3) { db.dao().recordNotification(record(it)) }
        val throughId = db.dao().allNotifications().first().id
        db.dao().recordNotification(record(3))
        db.dao().markNotificationsRead(throughId)
        val records = db.dao().allNotifications()
        assertEquals(1, records.count { !it.isRead })
        assertFalse(records.first().isRead)
        assertTrue(records.drop(1).all { it.isRead })
        db.dao().markNotificationsRead(records.first().id)
        db.dao().markNotificationsRead(records.first().id)
        assertTrue(db.dao().allNotifications().all { it.isRead })
    }

    @Test fun clearRemovesNotificationsOnlyAndFutureRemindersStartUnread() = runBlocking {
        val payment = Payment(1, "Fatura", "100.00", LocalDate.of(2026, 12, 5), paymentTime = LocalTime.NOON)
        val settings = StoredSettings(salaryDay = 17, legacyImported = true)
        db.dao().savePayment(StoredPayment.from(payment))
        db.dao().saveSettings(settings)
        db.dao().recordNotification(record(1))
        db.dao().clearNotifications()
        assertTrue(db.dao().allNotifications().isEmpty())
        assertEquals(payment, db.dao().payment(1)?.toPayment())
        assertEquals(settings, db.dao().settings())
        db.dao().recordNotification(record(2))
        assertFalse(db.dao().allNotifications().single().isRead)
    }

    @Test fun sentTitleAndMessageRemainAsSentAfterThePaymentIsEditedOrDeleted() = runBlocking {
        val payment = Payment(1, "Fatura", "100.00", LocalDate.of(2026, 12, 5), paymentTime = LocalTime.NOON)
        db.dao().savePayment(StoredPayment.from(payment))
        db.dao().recordNotification(record(1))
        val expected = db.dao().allNotifications()
        db.dao().savePayment(StoredPayment.from(payment.copy(name = "Yeni ad", amount = "200.00")))
        assertEquals(expected, db.dao().allNotifications())
        db.dao().deletePayment(1)
        assertEquals(expected, db.dao().allNotifications())
    }

    @Test fun notificationTextAndReadStateSurviveReopeningTheDatabase() = runBlocking {
        val name = "odedim-inbox-" + java.util.UUID.randomUUID() + ".db"
        var persistent = Room.databaseBuilder(context, PaymentDatabase::class.java, name).build()
        try {
            persistent.dao().recordNotification(record(1))
            val id = persistent.dao().allNotifications().single().id
            persistent.dao().markNotificationsRead(id)
            val expected = persistent.dao().allNotifications()
            persistent.close()
            persistent = Room.databaseBuilder(context, PaymentDatabase::class.java, name).build()
            assertEquals(expected, persistent.dao().allNotifications())
            assertTrue(persistent.dao().allNotifications().single().isRead)
        } finally {
            persistent.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun versionThreeUpgradeKeepsPaymentsHistoryAndSettingsAndStartsAnEmptyInbox() = runBlocking {
        val name = "odedim-inbox-migration-" + java.util.UUID.randomUUID() + ".db"
        val payment = Payment(21, "Abonelik", "9.99", LocalDate.of(2026, 11, 5),
            paymentTime = LocalTime.of(18, 30), currency = PaymentCurrency.EUR)
            .markPaid(LocalDate.of(2026, 11, 5), 17)
        val stored = StoredPayment.from(payment)
        val settings = StoredSettings(salaryDay = 17, notificationsEnabled = false, legacyImported = true)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, amount TEXT NOT NULL, dueEpochDay INTEGER NOT NULL, repeatType TEXT NOT NULL, monthlyDay INTEGER NOT NULL, minuteOfDay INTEGER, historyJson TEXT NOT NULL, periodPaid INTEGER NOT NULL DEFAULT 0, undoAvailable INTEGER NOT NULL DEFAULT 0, currencyCode TEXT NOT NULL DEFAULT 'TRY')")
            old.execSQL("CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, salaryDay INTEGER NOT NULL, notificationsEnabled INTEGER NOT NULL, legacyImported INTEGER NOT NULL)")
            old.execSQL("INSERT INTO payments VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(stored.id, stored.name, stored.amount, stored.dueEpochDay,
                    stored.repeatType, stored.monthlyDay, requireNotNull(stored.minuteOfDay),
                    stored.historyJson, 1, 1, stored.currencyCode))
            old.execSQL("INSERT INTO settings VALUES (1, 17, 0, 1)")
            old.version = 3
        }
        val upgraded = Room.databaseBuilder(context, PaymentDatabase::class.java, name)
            .addMigrations(PaymentDatabase.MIGRATION_3_4).build()
        try {
            assertEquals(payment, upgraded.dao().payment(21)?.toPayment())
            assertEquals(settings, upgraded.dao().settings())
            assertTrue(upgraded.dao().allNotifications().isEmpty())
            upgraded.dao().recordNotification(record(1))
            assertEquals(1, upgraded.dao().allNotifications().size)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}

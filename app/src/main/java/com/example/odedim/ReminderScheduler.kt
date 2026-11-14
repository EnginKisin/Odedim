package com.example.odedim

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            enableVibration(true)
        }
        // Existing channel sound/importance remain under the user's control.
        notifications.createNotificationChannel(channel)
    }

    fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) &&
            notifications.areNotificationsEnabled() &&
            notifications.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun exactAlarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun reschedule(
        state: PaymentAppState,
        clearIds: Set<Long> = emptySet(),
        clearAll: Boolean = false
    ) {
        createChannel()
        if (clearAll || !state.notificationsEnabled) notifications.cancelAll()
        clearIds.forEach { id ->
            ReminderKind.entries.forEach { notifications.cancel(tag(id, it), 0) }
        }
        (state.payments.map { it.id }.toSet() + clearIds).forEach { id ->
            ReminderKind.entries.forEach { kind ->
                pending(id, kind, PendingIntent.FLAG_NO_CREATE)?.let {
                    alarms.cancel(it)
                    it.cancel()
                }
            }
        }
        if (!state.notificationsEnabled || !notificationsAllowed()) return
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        state.payments.forEach { payment ->
            ReminderTimes.upcoming(payment, now, zone).forEach { plan ->
                val intent = reminderIntent(payment.id, plan.kind).putExtra("due", plan.dueAt.toEpochMilli())
                val pending = PendingIntent.getBroadcast(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                schedule(plan.triggerAt.toEpochMilli(), pending)
            }
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(at: Long, pending: PendingIntent) {
        try {
            if (exactAlarmsAllowed()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            // Permission may be revoked between the check and the framework call.
            Log.w("Odedim", "Exact alarm permission changed; using an inexact alarm", e)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun showNotification(payment: Payment, kind: ReminderKind): String? {
        createChannel()
        if (!notificationsAllowed()) return null
        val time = payment.paymentTime ?: return null
        val message = context.getString(
            when (kind) {
                ReminderKind.DAY_BEFORE -> R.string.reminder_tomorrow
                ReminderKind.TWO_HOURS -> R.string.reminder_two_hours
                ReminderKind.THIRTY_MINUTES -> R.string.reminder_thirty_minutes
                ReminderKind.DUE -> R.string.reminder_due_now
            },
            payment.name, formatPaymentTime(time), TurkishMoney.formatCurrency(payment.amount, payment.currency)
        )
        return message.takeIf { postNotification(it, tag(payment.id, kind), payment.id) }
    }

    fun showTestNotification() {
        postNotification(context.getString(R.string.test_notification_body), "test", 0)
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(message: String, notificationTag: String, paymentId: Long): Boolean {
        createChannel()
        if (!notificationsAllowed()) return false
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("odedim://payment/" + paymentId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_payment_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        return try {
            notifications.notify(notificationTag, 0, notification)
            true
        } catch (e: SecurityException) {
            Log.w("Odedim", "Notification permission was revoked", e)
            false
        }
    }

    private fun reminderIntent(id: Long, kind: ReminderKind) =
        Intent(context, PaymentReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("odedim://reminder/" + id + "/" + kind.name)
            putExtra("paymentId", id)
            putExtra("kind", kind.name)
        }

    private fun pending(id: Long, kind: ReminderKind, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, reminderIntent(id, kind), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun tag(id: Long, kind: ReminderKind) = "payment:" + id + ":" + kind.name

    companion object {
        const val CHANNEL_ID = "payment_reminders_v1"
        const val ACTION_REMIND = "com.example.odedim.REMIND"
    }
}

class PaymentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val id = intent.getLongExtra("paymentId", -1)
        val kind = runCatching { ReminderKind.valueOf(intent.getStringExtra("kind").orEmpty()) }
            .getOrNull() ?: return
        val due = intent.getLongExtra("due", -1)
        if (id <= 0 || due <= 0) return
        val pending = goAsync()
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, context.packageName + ":payment-reminder")
        wakeLock.acquire(10_000L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PaymentStore.get(context).deliver(id, kind, due)
            } catch (e: Exception) {
                Log.e("Odedim", "Could not deliver reminder", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }
    }
}

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PaymentStore.get(context).reschedule()
            } catch (e: Exception) {
                Log.e("Odedim", "Could not restore reminders", e)
            } finally {
                pending.finish()
            }
        }
    }
}

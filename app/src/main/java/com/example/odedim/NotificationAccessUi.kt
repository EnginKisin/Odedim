package com.example.odedim

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@Composable
internal fun NotificationAccessUi(resumeToken: Int, enabled: Boolean) {
    val context = LocalContext.current
    val scheduler = remember(context, resumeToken) { ReminderScheduler(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var step by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingSettings by rememberSaveable { mutableStateOf<String?>(null) }
    val allowed = refresh.let { scheduler.notificationsAllowed() }
    val exact = scheduler.exactAlarmsAllowed()

    fun reschedule() {
        scope.launch {
            runCatching { PaymentStore.get(context).reschedule() }
                .onFailure { Log.e("Odedim", "Could not refresh reminders", it) }
        }
    }

    fun nextStep() {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        step = when {
            needsPermission -> {
                val activity = context.activity()
                if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, Manifest.permission.POST_NOTIFICATIONS)) "rationale" else "prompt"
            }
            !scheduler.notificationsAllowed() -> "notifications"
            !scheduler.exactAlarmsAllowed() -> "timing"
            else -> null
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (granted) nextStep() else step = "denied"
        reschedule()
    }
    LaunchedEffect(step) {
        if (step == "prompt") launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(resumeToken) {
        val returningFrom = awaitingSettings
        if (returningFrom != null) {
            awaitingSettings = null
            refresh++
            // Continue only when the requested permission was actually enabled.
            // Returning without granting access leaves setup available, without another prompt.
            if (returningFrom == "notifications" && scheduler.notificationsAllowed()) nextStep()
            else step = null
            reschedule()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!enabled) {
            Text(stringResource(R.string.notifications_off_status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(when {
                !allowed -> R.string.notifications_need_permission
                !exact -> R.string.notifications_need_timing
                else -> R.string.notifications_ready
            }), style = MaterialTheme.typography.bodyMedium,
                color = if (allowed && exact) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!allowed || !exact) {
                Button(onClick = { nextStep() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.finish_notification_setup))
                }
            }
            TextButton(onClick = { openNotificationSettings(context) }) {
                Text(stringResource(R.string.notification_sound_simple))
            }
        }
    }

    if (step != null && step != "prompt") {
        val currentStep = step
        AlertDialog(
            onDismissRequest = { step = null },
            title = { Text(stringResource(if (currentStep == "timing")
                R.string.timing_setup_title else R.string.notification_setup_title)) },
            text = { Text(stringResource(when (currentStep) {
                "timing" -> R.string.timing_setup_explanation
                "denied" -> R.string.notification_denied_hint
                "notifications" -> R.string.notification_system_explanation
                else -> R.string.notification_setup_explanation
            })) },
            confirmButton = {
                TextButton(onClick = {
                    when (currentStep) {
                        "rationale" -> step = "prompt"
                        "timing" -> {
                            step = null
                            awaitingSettings = "timing"
                            if (Build.VERSION.SDK_INT >= 31) {
                                openSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + context.packageName)))
                            }
                        }
                        "denied" -> {
                            step = null
                            awaitingSettings = "notifications"
                            openSettings(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + context.packageName)))
                        }
                        else -> {
                            step = null
                            awaitingSettings = "notifications"
                            openNotificationSettings(context)
                        }
                    }
                }) {
                    Text(stringResource(if (currentStep == "rationale")
                        R.string.continue_label else R.string.open_phone_settings_short))
                }
            },
            dismissButton = {
                TextButton(onClick = { step = null }) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun openNotificationSettings(context: Context) {
    ReminderScheduler(context).createChannel()
    openSettings(context, Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, ReminderScheduler.CHANNEL_ID)
    })
}

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)))
    }
}

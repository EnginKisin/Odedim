package com.example.odedim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.odedim.ui.theme.OdedimTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun NotificationsScreen(
    records: List<NotificationRecord>, busy: Boolean, onClose: () -> Unit, onClear: () -> Unit
) {
    PaymentPage(stringResource(R.string.notification_inbox), busy, onClose) { padding ->
        NotificationsContent(records, busy, padding, onClear)
    }
}

@Composable
private fun NotificationsContent(
    records: List<NotificationRecord>, busy: Boolean = false,
    padding: PaddingValues = PaddingValues(), onClear: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.notification_retention_note, NotificationRecord.HISTORY_LIMIT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (records.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_notifications), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.no_notification_history),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.notification_history_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClear, enabled = !busy) {
                        Text(stringResource(R.string.clear_all_notifications))
                    }
                }
            }
            items(records, key = { it.id }) { record ->
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.title, style = MaterialTheme.typography.titleMedium)
                        Text(record.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Instant.ofEpochMilli(record.sentAt).atZone(ZoneId.systemDefault()).format(
                                DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm", Locale.forLanguageTag("tr-TR"))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "tr", widthDp = 390, heightDp = 800)
@Composable
private fun NotificationsPreview() {
    OdedimTheme {
        Surface {
            NotificationsContent(listOf(
                NotificationRecord(3, 1, "DUE", 1, "Elektrik faturası",
                    "Elektrik faturası ödeme zamanı geldi. Saat 18:30 · 850,50 TL", 1794670200000, true),
                NotificationRecord(2, 1, "THIRTY_MINUTES", 1, "Elektrik faturası",
                    "Elektrik faturası ödemenize 30 dakika kaldı. Saat 18:30 · 850,50 TL", 1794668400000, true),
                NotificationRecord(1, 1, "TWO_HOURS", 1, "Elektrik faturası",
                    "Elektrik faturası ödemenize 2 saat kaldı. Saat 18:30 · 850,50 TL", 1794663000000, true)
            ))
        }
    }
}

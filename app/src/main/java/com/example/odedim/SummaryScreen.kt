package com.example.odedim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.odedim.ui.theme.OdedimTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SummaryScreen(payments: List<Payment>, today: LocalDate, onClose: () -> Unit) {
    PaymentPage(stringResource(R.string.summary_title), busy = false, onClose = onClose) { padding ->
        SummaryContent(payments, today, padding)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryContent(
    payments: List<Payment>, today: LocalDate, padding: PaddingValues = PaddingValues(),
    initialPeriod: Int = 12
) {
    var currency by rememberSaveable { mutableStateOf(PaymentCurrency.TRY) }
    var period by rememberSaveable { mutableStateOf(initialPeriod) }
    var periodMenu by remember { mutableStateOf(false) }
    val summary = remember(payments, today, currency, period) {
        PaymentSummaries.calculate(payments, today, currency, period)
    }
    val current = summary.currentMonth
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PaymentCurrency.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = currency == option,
                        onClick = { currency = option },
                        shape = SegmentedButtonDefaults.itemShape(index, PaymentCurrency.entries.size)
                    ) { Text(option.label) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.summary_period),
                    style = MaterialTheme.typography.titleSmall)
                Box {
                    TextButton(onClick = { periodMenu = true }) {
                        Text(stringResource(R.string.summary_period_label, period) + " ▾")
                    }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        PaymentSummaries.periods.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.summary_period_label, option)) },
                                onClick = { period = option; periodMenu = false }
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )) {
                Column(Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.summary_paid_this_month),
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (current.paymentCount == 0 && current.unknownAmountCount > 0) "—"
                        else TurkishMoney.formatCurrency(current.amount.toPlainString(), currency),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        if (current.paymentCount == 0 && current.unknownAmountCount > 0) {
                            monthName(current.month)
                        } else {
                            monthName(current.month) + " · " +
                                stringResource(R.string.summary_payment_count, current.paymentCount)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (current.unknownAmountCount > 0) {
                        Text(stringResource(R.string.summary_unknown_amounts, current.unknownAmountCount),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetric(
                    title = stringResource(R.string.summary_upcoming),
                    value = TurkishMoney.formatCurrency(summary.upcomingAmount.toPlainString(), currency),
                    detail = stringResource(R.string.summary_payment_count, summary.upcomingCount),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                SummaryMetric(
                    title = stringResource(R.string.summary_on_time),
                    value = summary.onTimePercent?.let { "%$it" } ?: "—",
                    detail = if (summary.historyCount == 0) stringResource(R.string.no_payment_history)
                        else stringResource(R.string.summary_on_time_count,
                            summary.onTimeCount, summary.historyCount),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        item {
            Text(stringResource(R.string.summary_rate_scope, period),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { MonthlyChart(summary) }
        item {
            Text(stringResource(R.string.summary_scope_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (summary.unknownAmountCount > 0) {
            item {
                Text(stringResource(R.string.summary_legacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SummaryMetric(title: String, value: String, detail: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthlyChart(summary: PaymentSummary) {
    var selectedMonth by rememberSaveable { mutableStateOf(summary.currentMonth.month.toString()) }
    var monthMenu by remember { mutableStateOf(false) }
    val selectedIndex = summary.months.indexOfFirst { it.month.toString() == selectedMonth }
        .takeIf { it >= 0 } ?: summary.months.lastIndex
    val selected = summary.months[selectedIndex]
    val selectedValue = if (selected.paymentCount == 0 && selected.unknownAmountCount > 0) {
        stringResource(R.string.summary_amount_unknown)
    } else TurkishMoney.formatCurrency(selected.amount.toPlainString(), summary.currency)
    val chartDescription = stringResource(R.string.summary_chart_description,
        monthName(selected.month), selectedValue)
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    val maxAmount = summary.months.maxOf { it.amount }.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.summary_monthly_payments),
                    style = MaterialTheme.typography.titleMedium)
                Text(summary.currency.label, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
            Box {
                TextButton(onClick = { monthMenu = true }) {
                    Text(monthName(selected.month) + " ▾")
                }
                DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                    summary.months.asReversed().forEach { month ->
                        DropdownMenuItem(
                            text = { Text(monthName(month.month)) },
                            onClick = { selectedMonth = month.month.toString(); monthMenu = false }
                        )
                    }
                }
            }
            Text(selectedValue, style = MaterialTheme.typography.headlineSmall)
            if (selected.paymentCount > 0 || selected.unknownAmountCount == 0) {
                Text(stringResource(R.string.summary_payment_count, selected.paymentCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected.unknownAmountCount > 0) {
                Text(stringResource(R.string.summary_unknown_amounts, selected.unknownAmountCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(
                Modifier.fillMaxWidth().height(150.dp)
                    .semantics { contentDescription = chartDescription }
                    .pointerInput(summary.months) {
                        detectTapGestures { position ->
                            val index = (position.x / size.width * summary.months.size).toInt()
                                .coerceIn(0, summary.months.lastIndex)
                            selectedMonth = summary.months[index].month.toString()
                        }
                    }
            ) {
                val slot = size.width / summary.months.size
                val barWidth = slot * 0.55f
                val baseline = size.height - 2.dp.toPx()
                drawLine(muted, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                summary.months.forEachIndexed { index, month ->
                    val ratio = month.amount.divide(maxAmount, 8, RoundingMode.HALF_UP).toFloat()
                    val barHeight = if (month.amount > BigDecimal.ZERO) {
                        (ratio * (baseline - 8.dp.toPx())).coerceAtLeast(3.dp.toPx())
                    } else 2.dp.toPx()
                    drawRoundRect(
                        color = when {
                            month.amount == BigDecimal.ZERO -> muted
                            index == selectedIndex -> primary
                            else -> primary.copy(alpha = 0.4f)
                        },
                        topLeft = Offset(slot * index + (slot - barWidth) / 2, baseline - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
            // Sparse labels stay readable even with 24 bars on a narrow phone.
            BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp)) {
                val labelCount = minOf(summary.months.size, 4)
                val labelWidth = minOf(60.dp, maxWidth / labelCount)
                val indices = if (labelCount == 1) listOf(0) else List(labelCount) {
                    it * summary.months.lastIndex / (labelCount - 1)
                }
                indices.forEach { index ->
                    val center = maxWidth * ((index + 0.5f) / summary.months.size)
                    Text(
                        summary.months[index].month.format(
                            DateTimeFormatter.ofPattern("MMM\nyy", Locale.forLanguageTag("tr-TR"))),
                        modifier = Modifier.width(labelWidth).offset(
                            x = (center - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (index == selectedIndex) primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (summary.months.all { it.paymentCount == 0 }) {
                Text(stringResource(R.string.summary_chart_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun monthName(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("tr-TR")))

@Preview(showBackground = true, locale = "tr", widthDp = 390, heightDp = 900)
@Composable
private fun SummaryPreview() = SummaryPreviewContent(12)

@Preview(showBackground = true, locale = "tr", widthDp = 390, heightDp = 1000)
@Composable
private fun Summary24MonthPreview() = SummaryPreviewContent(24)

@Composable
private fun SummaryPreviewContent(period: Int) {
    val today = LocalDate.of(2026, 11, 14)
    val records = List(24) { index ->
        val month = YearMonth.of(2024, 12).plusMonths(index.toLong())
        PaymentRecord(month.atDay(5), month.atDay(7), (900 + index * 70).toString(), PaymentCurrency.TRY)
    }
    OdedimTheme {
        Surface {
            SummaryContent(
                listOf(
                    Payment(1, "Kira", "1700", today.plusDays(2), history = records),
                    Payment(2, "İnternet", "450", today.plusDays(4)),
                    Payment(3, "Abonelik", "9.99", today.plusDays(3), currency = PaymentCurrency.USD)
                ), today, initialPeriod = period
            )
        }
    }
}

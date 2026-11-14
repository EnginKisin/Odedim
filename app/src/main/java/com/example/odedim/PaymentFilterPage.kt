package com.example.odedim

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal val PaymentListFilterSaver = listSaver<PaymentListFilter, String>(
    save = { listOf(it.currency?.name.orEmpty(), it.minimum?.toPlainString().orEmpty(),
        it.maximum?.toPlainString().orEmpty(), it.due.name, it.from?.toString().orEmpty(),
        it.until?.toString().orEmpty(), it.sort.name) },
    restore = { values ->
        PaymentListFilter(
            currency = values[0].takeIf { it.isNotEmpty() }?.let(PaymentCurrency::valueOf),
            minimum = values[1].toBigDecimalOrNull(), maximum = values[2].toBigDecimalOrNull(),
            due = DueFilter.valueOf(values[3]),
            from = values[4].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            until = values[5].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            sort = PaymentSort.valueOf(values[6])
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentFilterPage(
    initial: PaymentListFilter, onClose: () -> Unit, onApply: (PaymentListFilter) -> Unit
) {
    var currency by rememberSaveable { mutableStateOf(initial.currency) }
    var minimum by rememberSaveable { mutableStateOf(initial.minimum?.toPlainString()?.let(TurkishMoney::editableAmount).orEmpty()) }
    var maximum by rememberSaveable { mutableStateOf(initial.maximum?.toPlainString()?.let(TurkishMoney::editableAmount).orEmpty()) }
    var due by rememberSaveable { mutableStateOf(initial.due) }
    var fromEpoch by rememberSaveable { mutableStateOf(initial.from?.toEpochDay()) }
    var untilEpoch by rememberSaveable { mutableStateOf(initial.until?.toEpochDay()) }
    var sort by rememberSaveable { mutableStateOf(initial.sort) }
    var dueMenu by rememberSaveable { mutableStateOf(false) }
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val from = fromEpoch?.let(LocalDate::ofEpochDay)
    val until = untilEpoch?.let(LocalDate::ofEpochDay)
    val minValue = currency?.let { PaymentListFilter.amountBoundary(minimum, it) }
    val maxValue = currency?.let { PaymentListFilter.amountBoundary(maximum, it) }
    val invalidMin = minimum.isNotEmpty() && minValue == null
    val invalidMax = maximum.isNotEmpty() && maxValue == null
    val invertedAmount = minValue != null && maxValue != null && minValue > maxValue
    val invertedDates = due == DueFilter.CUSTOM && from != null && until != null && from > until
    val missingDates = due == DueFilter.CUSTOM && from == null && until == null
    val valid = !invalidMin && !invalidMax && !invertedAmount && !invertedDates && !missingDates

    fun chooseDate(start: Boolean) {
        focus.clearFocus()
        val initialDate = (if (start) from ?: until else until ?: from) ?: LocalDate.now()
        DatePickerDialog(context, { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day).toEpochDay()
            if (start) fromEpoch = date else untilEpoch = date
        }, initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth).show()
    }

    PaymentPage(
        title = stringResource(R.string.payment_filters), busy = false, onClose = onClose,
        bottomBar = {
            Surface {
                Button(
                    enabled = valid,
                    onClick = {
                        focus.clearFocus()
                        onApply(PaymentListFilter(
                            currency, minValue, maxValue, due,
                            from.takeIf { due == DueFilter.CUSTOM },
                            until.takeIf { due == DueFilter.CUSTOM }, sort
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) { Text(stringResource(R.string.apply_filters)) }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TextButton(onClick = { focus.clearFocus(); onApply(PaymentListFilter()) }) {
                Text(stringResource(R.string.reset_filters))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.filter_currency), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        focus.clearFocus()
                        currency = null; minimum = ""; maximum = ""
                        if (sort in PaymentListFilter.amountSorts) sort = PaymentSort.ADDED
                    }) {
                        Text(stringResource(if (currency == null) R.string.filter_all_selected else R.string.filter_all))
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PaymentCurrency.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = currency == option,
                            onClick = { focus.clearFocus(); currency = option },
                            shape = SegmentedButtonDefaults.itemShape(index, PaymentCurrency.entries.size)
                        ) { Text(option.label) }
                    }
                }
                Text(stringResource(if (currency == null) R.string.filter_currency_hint else R.string.filter_amount_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.filter_amount), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoundaryField(
                        value = minimum, label = stringResource(R.string.filter_minimum),
                        currency = currency, isError = invalidMin || invertedAmount,
                        onValue = { minimum = it }, modifier = Modifier.weight(1f)
                    )
                    BoundaryField(
                        value = maximum, label = stringResource(R.string.filter_maximum),
                        currency = currency, isError = invalidMax || invertedAmount,
                        onValue = { maximum = it }, modifier = Modifier.weight(1f)
                    )
                }
                if (invertedAmount) {
                    Text(stringResource(R.string.filter_amount_order_error), color = MaterialTheme.colorScheme.error)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.filter_due_date), style = MaterialTheme.typography.titleSmall)
                Box {
                    TextButton(onClick = { focus.clearFocus(); dueMenu = true }) {
                        Text(dueFilterLabel(due) + " ▾")
                    }
                    DropdownMenu(expanded = dueMenu, onDismissRequest = { dueMenu = false }) {
                        DueFilter.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(dueFilterLabel(option)) },
                                onClick = { due = option; dueMenu = false }
                            )
                        }
                    }
                }
                if (due == DueFilter.CUSTOM) {
                    Card {
                        Row(Modifier.fillMaxWidth()) {
                            TimeChoiceField(
                                stringResource(R.string.filter_from),
                                from?.let(::formatPaymentDate) ?: stringResource(R.string.filter_select_date),
                                Modifier.weight(1f), onClick = { chooseDate(true) }
                            )
                            TimeChoiceField(
                                stringResource(R.string.filter_until),
                                until?.let(::formatPaymentDate) ?: stringResource(R.string.filter_select_date),
                                Modifier.weight(1f), onClick = { chooseDate(false) }
                            )
                        }
                    }
                    if (from != null || until != null) {
                        TextButton(onClick = { fromEpoch = null; untilEpoch = null }) {
                            Text(stringResource(R.string.clear_filter_dates))
                        }
                    }
                    Text(stringResource(when {
                        invertedDates -> R.string.filter_date_order_error
                        missingDates -> R.string.filter_pick_date_hint
                        else -> R.string.filter_date_inclusive_hint
                    }), style = MaterialTheme.typography.bodySmall,
                        color = if (invertedDates) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.filter_sort), style = MaterialTheme.typography.titleSmall)
                Box {
                    TextButton(onClick = { focus.clearFocus(); sortMenu = true }) {
                        Text(paymentSortLabel(sort) + " ▾")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        PaymentSort.entries.forEach { option ->
                            DropdownMenuItem(
                                enabled = currency != null || option !in PaymentListFilter.amountSorts,
                                text = { Text(paymentSortLabel(option)) },
                                onClick = { sort = option; sortMenu = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, locale = "tr", widthDp = 390, heightDp = 1000)
@Composable
private fun PaymentFilterPreview() {
    com.example.odedim.ui.theme.OdedimTheme {
        PaymentFilterPage(
            initial = PaymentListFilter(
                currency = PaymentCurrency.TRY, minimum = "100.00".toBigDecimal(), maximum = "1000.00".toBigDecimal(),
                due = DueFilter.CUSTOM, from = LocalDate.of(2026, 11, 14),
                until = LocalDate.of(2026, 12, 14), sort = PaymentSort.DUE_ASCENDING
            ), onClose = {}, onApply = {}
        )
    }
}

@Composable
private fun BoundaryField(
    value: String, label: String, currency: PaymentCurrency?,
    isError: Boolean, onValue: (String) -> Unit, modifier: Modifier
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            currency?.let { TurkishMoney.normalizeInput(input, it)?.let(onValue) }
        },
        enabled = currency != null, singleLine = true, isError = isError,
        label = { Text(label) }, placeholder = { Text(stringResource(R.string.filter_no_limit)) },
        suffix = { Text(currency?.label.orEmpty()) },
        visualTransformation = TurkishMoneyVisualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier
    )
}

@Composable
private fun dueFilterLabel(filter: DueFilter): String = stringResource(when (filter) {
    DueFilter.ALL -> R.string.filter_all_dates
    DueFilter.OVERDUE -> R.string.filter_overdue
    DueFilter.TODAY -> R.string.filter_today
    DueFilter.NEXT_7_DAYS -> R.string.filter_next_week
    DueFilter.NEXT_30_DAYS -> R.string.filter_next_month
    DueFilter.CUSTOM -> R.string.filter_custom_dates
})

@Composable
private fun paymentSortLabel(sort: PaymentSort): String = stringResource(when (sort) {
    PaymentSort.ADDED -> R.string.sort_added
    PaymentSort.DUE_ASCENDING -> R.string.sort_due_ascending
    PaymentSort.DUE_DESCENDING -> R.string.sort_due_descending
    PaymentSort.AMOUNT_ASCENDING -> R.string.sort_amount_ascending
    PaymentSort.AMOUNT_DESCENDING -> R.string.sort_amount_descending
    PaymentSort.NAME -> R.string.sort_name
})

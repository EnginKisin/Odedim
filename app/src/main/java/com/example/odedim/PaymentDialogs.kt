package com.example.odedim

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddPaymentDialog(
    salaryDay: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSalaryChange: (Int) -> Unit,
    payment: Payment? = null,
    onSave: (String, String, RepeatRule, LocalDate?, LocalTime, PaymentCurrency, LocalDate?) -> Unit
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var currency by rememberSaveable { mutableStateOf(payment?.currency ?: PaymentCurrency.TRY) }
    var currencyMenu by rememberSaveable { mutableStateOf(false) }
    val currencyDescription = stringResource(R.string.select_payment_currency, currency.label)
    var name by rememberSaveable { mutableStateOf(payment?.name.orEmpty()) }
    var amount by rememberSaveable {
        mutableStateOf(payment?.amount?.let(TurkishMoney::editableAmount).orEmpty())
    }
    var repeatType by rememberSaveable { mutableStateOf(payment?.repeatRule?.type ?: RepeatType.AFTER_30_DAYS) }
    var monthlyDay by rememberSaveable { mutableIntStateOf(payment?.repeatRule?.monthlyDay ?: LocalDate.now().dayOfMonth) }
    var paidEpochDay by rememberSaveable { mutableLongStateOf((payment?.nextDueDate ?: LocalDate.now()).toEpochDay()) }
    var firstDueEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var recordPreviousPayment by rememberSaveable { mutableStateOf(false) }
    var minuteOfDay by rememberSaveable { mutableStateOf(payment?.paymentTime?.let { it.hour * 60 + it.minute }) }
    var showDayPicker by rememberSaveable { mutableStateOf(false) }
    var showSalaryPicker by rememberSaveable { mutableStateOf(false) }
    val enteringFirstDue = payment == null && repeatType == RepeatType.AFTER_30_DAYS && !recordPreviousPayment
    val chosenDate = LocalDate.ofEpochDay(if (enteringFirstDue) firstDueEpochDay else paidEpochDay)
    val time = minuteOfDay?.let { LocalTime.of(it / 60, it % 60) }
    val normalizedAmount = TurkishMoney.storedAmount(amount, currency)
    val validAmount = normalizedAmount != null
    val rule = RepeatRule(repeatType, monthlyDay)
    val validDate = payment != null || repeatType != RepeatType.AFTER_30_DAYS ||
        enteringFirstDue || chosenDate <= LocalDate.now()
    val due = when {
        payment != null -> PaymentSchedule.dateForRuleInPeriod(rule, chosenDate, salaryDay)
        enteringFirstDue -> chosenDate
        repeatType == RepeatType.AFTER_30_DAYS -> chosenDate.plusDays(30)
        else -> PaymentSchedule.firstDueDate(rule, LocalDate.now(), salaryDay)
    }

    fun releaseKeyboard() {
        keyboard?.hide()
        focus.clearFocus()
    }

    PaymentPage(
        title = stringResource(if (payment == null) R.string.new_payment_title else R.string.edit_payment),
        busy = busy,
        onClose = onDismiss,
        bottomBar = {
            FormSaveButton(
                enabled = !busy && name.isNotBlank() && validAmount && validDate && time != null
            ) {
                onSave(name.trim(), requireNotNull(normalizedAmount), rule,
                    if (payment != null) due else chosenDate.takeIf {
                        repeatType == RepeatType.AFTER_30_DAYS && recordPreviousPayment
                    },
                    requireNotNull(time), currency, chosenDate.takeIf { enteringFirstDue })
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.payment_name)) },
                    placeholder = { Text(stringResource(R.string.payment_name_example)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input ->
                        TurkishMoney.normalizeInput(input, currency)?.let { amount = it }
                    },
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.amount_label)) },
                    placeholder = { Text(stringResource(R.string.amount_placeholder)) },
                    suffix = {
                        Box {
                            TextButton(
                                enabled = !busy,
                                onClick = { releaseKeyboard(); currencyMenu = true },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.semantics { contentDescription = currencyDescription }
                            ) {
                                Text(currency.label + " ▾")
                            }
                            DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                                PaymentCurrency.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = { currency = option; currencyMenu = false }
                                    )
                                }
                            }
                        }
                    },
                    visualTransformation = TurkishMoneyVisualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        TurkishMoney.storedAmount(amount, currency)?.let { amount = TurkishMoney.editableAmount(it) }
                        releaseKeyboard()
                    }),
                    isError = amount.isNotBlank() && !validAmount,
                    supportingText = if (amount.isNotBlank() && !validAmount) {
                        { Text(stringResource(R.string.amount_hint)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            TurkishMoney.storedAmount(amount, currency)?.let { amount = TurkishMoney.editableAmount(it) }
                        }
                    }
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.repeat_label), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RepeatType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = repeatType == type,
                            onClick = { releaseKeyboard(); repeatType = type },
                            enabled = !busy,
                            shape = SegmentedButtonDefaults.itemShape(index, RepeatType.entries.size)
                        ) {
                            Text(stringResource(when (type) {
                                RepeatType.AFTER_30_DAYS -> R.string.repeat_short_30
                                RepeatType.MONTHLY_DAY -> R.string.repeat_short_monthly
                                RepeatType.SALARY_DAY -> R.string.repeat_short_salary
                            }))
                        }
                    }
                }
                Text(
                    stringResource(when (repeatType) {
                        RepeatType.AFTER_30_DAYS -> when {
                            payment != null -> R.string.repeat_help_30_edit
                            enteringFirstDue -> R.string.repeat_help_30_first_due
                            else -> R.string.repeat_help_30
                        }
                        RepeatType.MONTHLY_DAY -> R.string.repeat_help_monthly
                        RepeatType.SALARY_DAY -> R.string.repeat_help_salary
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.payment_when), style = MaterialTheme.typography.titleSmall)
                if (payment == null && repeatType == RepeatType.AFTER_30_DAYS) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !recordPreviousPayment,
                            onClick = { releaseKeyboard(); recordPreviousPayment = false },
                            enabled = !busy, shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text(stringResource(R.string.first_payment_label)) }
                        SegmentedButton(
                            selected = recordPreviousPayment,
                            onClick = { releaseKeyboard(); recordPreviousPayment = true },
                            enabled = !busy, shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text(stringResource(R.string.previous_payment_choice)) }
                    }
                }
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        TimeChoiceField(
                            label = stringResource(when (repeatType) {
                                RepeatType.AFTER_30_DAYS -> when {
                                    payment != null -> R.string.next_payment_label
                                    enteringFirstDue -> R.string.first_payment_label
                                    else -> R.string.previous_payment_date_label
                                }
                                RepeatType.MONTHLY_DAY -> R.string.month_day_label
                                RepeatType.SALARY_DAY -> R.string.salary_day
                            }),
                            value = if (repeatType == RepeatType.AFTER_30_DAYS) {
                                formatPaymentDate(chosenDate)
                            } else {
                                stringResource(R.string.day_number,
                                    if (repeatType == RepeatType.SALARY_DAY) salaryDay else monthlyDay)
                            },
                            modifier = Modifier.weight(1f),
                            onClick = if (busy || repeatType == RepeatType.SALARY_DAY) null else {
                                {
                                    releaseKeyboard()
                                    if (repeatType == RepeatType.MONTHLY_DAY) showDayPicker = true
                                    else DatePickerDialog(context, { _, year, month, day ->
                                        val selected = LocalDate.of(year, month + 1, day).toEpochDay()
                                        if (enteringFirstDue) firstDueEpochDay = selected else paidEpochDay = selected
                                    }, chosenDate.year, chosenDate.monthValue - 1, chosenDate.dayOfMonth)
                                        .apply {
                                            if (payment == null && recordPreviousPayment) {
                                                datePicker.maxDate = System.currentTimeMillis()
                                            }
                                        }.show()
                                }
                            }
                        )
                        VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 16.dp))
                        TimeChoiceField(
                            label = stringResource(R.string.time_label),
                            value = time?.let { formatPaymentTime(it) } ?: stringResource(R.string.select),
                            modifier = Modifier.weight(1f),
                            onClick = if (busy) null else {
                                {
                                    releaseKeyboard()
                                    val initial = time ?: LocalTime.now()
                                    TimePickerDialog(context, { _, hour, minute ->
                                        minuteOfDay = hour * 60 + minute
                                    }, initial.hour, initial.minute, true).show()
                                }
                            }
                        )
                    }
                }
                if (repeatType == RepeatType.SALARY_DAY) {
                    TextButton(onClick = { showSalaryPicker = true }, enabled = !busy) {
                        Text(stringResource(R.string.change_salary_day))
                    }
                }
                if (time == null) {
                    Text(stringResource(R.string.time_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (validDate) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.next_payment_label),
                            style = MaterialTheme.typography.labelMedium)
                        Text(
                            time?.let { stringResource(R.string.date_time_value,
                                formatPaymentDate(due), formatPaymentTime(it)) } ?: formatPaymentDate(due),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
    if (showDayPicker) {
        DayOfMonthDialog(monthlyDay, onDismiss = { showDayPicker = false }) {
            monthlyDay = it
            showDayPicker = false
        }
    }
    if (showSalaryPicker) {
        DayOfMonthDialog(salaryDay, onDismiss = { showSalaryPicker = false }) {
            onSalaryChange(it)
            showSalaryPicker = false
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, locale = "tr", widthDp = 390, heightDp = 900)
@Composable
private fun FirstDuePaymentPreview() {
    com.example.odedim.ui.theme.OdedimTheme {
        AddPaymentDialog(
            salaryDay = 5, busy = false, onDismiss = {}, onSalaryChange = {},
            onSave = { _, _, _, _, _, _, _ -> }
        )
    }
}

@Composable
internal fun SalarySettingsDialog(
    salaryDay: Int,
    notificationsEnabled: Boolean,
    busy: Boolean,
    resumeToken: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Boolean) -> Unit
) {
    var showDayPicker by rememberSaveable { mutableStateOf(false) }
    PaymentPage(stringResource(R.string.settings), busy, onDismiss) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.notifications_title),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = notificationsEnabled,
                            onCheckedChange = { onSave(salaryDay, it) }, enabled = !busy)
                    }
                    Text(stringResource(R.string.reminder_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NotificationAccessUi(resumeToken, enabled = notificationsEnabled)
                }
            }
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.salary_day),
                            style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showDayPicker = true }, enabled = !busy) {
                            Text(stringResource(R.string.change))
                        }
                    }
                    Text(stringResource(R.string.salary_day_value, salaryDay),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.salary_day_simple_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.app_version_label,
                    stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
    if (showDayPicker) {
        DayOfMonthDialog(salaryDay, onDismiss = { showDayPicker = false }) {
            onSave(it, notificationsEnabled)
            showDayPicker = false
        }
    }
}

@Composable
internal fun PaymentHistoryDialog(
    payment: Payment, busy: Boolean, onDismiss: () -> Unit, onUndo: () -> Unit,
    onCompleteAmount: (Int, PaymentRecord, String, PaymentCurrency) -> Unit
) {
    val historyListState = rememberLazyListState()
    var editingIndex by rememberSaveable(payment.id) { mutableStateOf<Int?>(null) }
    val editingRecord = editingIndex?.let { payment.history.getOrNull(it) }
    if (editingRecord != null && !editingRecord.hasAmountSnapshot) {
        HistoryAmountPage(
            record = editingRecord, busy = busy,
            onClose = { if (!busy) editingIndex = null },
            onSave = { amount, currency ->
                onCompleteAmount(requireNotNull(editingIndex), editingRecord, amount, currency)
            }
        )
        return
    }
    PaymentPage(stringResource(R.string.payment_history_simple), busy = busy, onClose = onDismiss) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(padding),
            state = historyListState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(payment.name, style = MaterialTheme.typography.titleLarge) }
            item {
                Text(stringResource(R.string.history_retention_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (payment.history.isEmpty()) {
                item { Text(stringResource(R.string.no_payment_history)) }
            }
            itemsIndexed(payment.history.asReversed()) { index, record ->
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatPaymentDate(record.paidOn), style = MaterialTheme.typography.titleMedium)
                        if (record.hasAmountSnapshot) {
                            Text(TurkishMoney.formatCurrency(requireNotNull(record.amount), requireNotNull(record.currency)),
                                style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text(stringResource(R.string.summary_amount_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(
                                enabled = !busy,
                                onClick = { editingIndex = payment.history.lastIndex - index }
                            ) { Text(stringResource(R.string.history_add_amount)) }
                        }
                        Text(stringResource(R.string.history_due_date, formatPaymentDate(record.dueDate)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (index == 0 && payment.undoAvailable) {
                            TextButton(onClick = onUndo, enabled = !busy) {
                                Text(stringResource(R.string.undo_payment))
                            }
                        }
                    }
                }
            }
        }
    }
}

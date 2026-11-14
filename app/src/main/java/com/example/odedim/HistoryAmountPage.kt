package com.example.odedim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryAmountPage(
    record: PaymentRecord, busy: Boolean, onClose: () -> Unit,
    onSave: (String, PaymentCurrency) -> Unit
) {
    var amount by rememberSaveable(record.paidOn, record.dueDate) { mutableStateOf("") }
    // The original currency is unknown, so the user must explicitly choose it.
    var currency by rememberSaveable(record.paidOn, record.dueDate) {
        mutableStateOf<PaymentCurrency?>(null)
    }
    val storedAmount = TurkishMoney.storedAmount(amount)
    val focus = LocalFocusManager.current
    PaymentPage(
        title = stringResource(R.string.history_add_amount), busy = busy, onClose = onClose,
        bottomBar = {
            Surface {
                Button(
                    enabled = !busy && storedAmount != null && currency != null,
                    onClick = {
                        focus.clearFocus()
                        onSave(requireNotNull(storedAmount), requireNotNull(currency))
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) { Text(stringResource(R.string.save)) }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(formatPaymentDate(record.paidOn), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.history_amount_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = amount,
                onValueChange = { input -> TurkishMoney.normalizeInput(input)?.let { amount = it } },
                enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.amount_label)) },
                placeholder = { Text(stringResource(R.string.amount_placeholder)) },
                visualTransformation = TurkishMoneyVisualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    storedAmount?.let { amount = TurkishMoney.editableAmount(it) }
                    focus.clearFocus()
                }),
                isError = amount.isNotBlank() && storedAmount == null,
                supportingText = if (amount.isNotBlank() && storedAmount == null) {
                    { Text(stringResource(R.string.amount_hint)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.history_choose_currency),
                style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PaymentCurrency.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = currency == option, enabled = !busy,
                        onClick = { focus.clearFocus(); currency = option },
                        shape = SegmentedButtonDefaults.itemShape(index, PaymentCurrency.entries.size)
                    ) { Text(option.label) }
                }
            }
        }
    }
}

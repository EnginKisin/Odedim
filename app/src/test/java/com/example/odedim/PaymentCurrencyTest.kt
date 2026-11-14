package com.example.odedim

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class PaymentCurrencyTest {
    private fun payment(currency: PaymentCurrency = PaymentCurrency.TRY) =
        Payment(1, "Abonelik", "1234.50", LocalDate.of(2026, 11, 20),
            paymentTime = LocalTime.NOON, currency = currency)

    @Test fun onlyThreeSupportedCurrenciesHaveClearLabels() {
        assertEquals(listOf("TL", "USD", "EUR"), PaymentCurrency.entries.map { it.label })
        assertEquals(PaymentCurrency.TRY, payment().currency)
    }

    @Test fun allCurrenciesUseTheSameTurkishNumberFormat() {
        PaymentCurrency.entries.forEach { currency ->
            assertEquals("1.234,50 " + currency.label, TurkishMoney.formatCurrency("1234.50", currency))
            assertEquals("0,01 " + currency.label, TurkishMoney.formatCurrency("0.01", currency))
        }
    }

    @Test fun selectedCurrencySymbolAndCodeCanBePastedWithoutChangingAmount() {
        PaymentCurrency.entries.forEach { currency ->
            assertEquals("1234.50", TurkishMoney.storedAmount(currency.symbol + " 1.234,50", currency))
            assertEquals("1234.50", TurkishMoney.storedAmount("1.234,50 " + currency.label, currency))
            assertEquals("1234.50", TurkishMoney.storedAmount("1.234,50 " + currency.name, currency))
            assertEquals("1234.50", TurkishMoney.storedAmount("1234,50", currency))
        }
    }

    @Test fun foreignCurrencyPasteIsRejectedRatherThanSilentlyRelabelled() {
        assertNull(TurkishMoney.storedAmount("1.234,50 USD", PaymentCurrency.TRY))
        assertNull(TurkishMoney.storedAmount("€ 1.234,50", PaymentCurrency.USD))
        assertNull(TurkishMoney.storedAmount("1.234,50 TL", PaymentCurrency.EUR))
    }

    @Test fun paidUndoAndSalaryUpdatesKeepCurrency() {
        PaymentCurrency.entries.forEach { currency ->
            val original = payment(currency)
            val paid = original.markPaid(LocalDate.of(2026, 11, 20), 5)
            assertEquals(currency, paid.currency)
            assertEquals(original, paid.undoLastPayment(5))
            assertEquals(currency, paid.updateSalaryDay(15).currency)
        }
    }

    @Test fun editingOtherDetailsPreservesCurrencyAndChangingCurrencyDoesNotConvertValue() {
        val original = payment(PaymentCurrency.USD)
        val renamed = original.edited("Yeni ad", original.amount, original.repeatRule,
            original.nextDueDate, LocalTime.of(18, 30))
        assertEquals(PaymentCurrency.USD, renamed.currency)
        val changed = renamed.edited(renamed.name, renamed.amount, renamed.repeatRule,
            renamed.nextDueDate, LocalTime.NOON, PaymentCurrency.EUR)
        assertEquals(PaymentCurrency.EUR, changed.currency)
        assertEquals(original.amount, changed.amount)
        assertEquals(original.history, changed.history)
        assertEquals(original.nextDueDate, changed.nextDueDate)
    }

    @Test fun creatingPaymentsKeepsTheSelectedCurrency() {
        PaymentCurrency.entries.forEach { currency ->
            val created = createPayment(1, "Abonelik", "9.99", RepeatRule(),
                LocalDate.of(2026, 11, 13), LocalTime.NOON, LocalDate.of(2026, 11, 14), 5, currency)
            assertEquals(currency, created.currency)
            assertEquals("9.99", created.amount)
            assertEquals(LocalDate.of(2026, 12, 13), created.nextDueDate)
        }
    }
}

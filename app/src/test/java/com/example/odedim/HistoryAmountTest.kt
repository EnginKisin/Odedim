package com.example.odedim

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class HistoryAmountTest {
    private val old = PaymentRecord(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 5))
    private val last = PaymentRecord(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), "80.00", PaymentCurrency.TRY)
    private val payment = Payment(1, "Fatura", "999.00", LocalDate.of(2026, 11, 5),
        history = listOf(old, last), paymentTime = LocalTime.NOON)

    @Test fun completingOldAmountKeepsAllDatesOtherRecordsAndCycleState() {
        val completed = payment.completeHistoryAmount(0, old, "12.34", PaymentCurrency.EUR)
        assertEquals(payment.copy(history = listOf(old.copy(amount = "12.34", currency = PaymentCurrency.EUR), last)), completed)
        assertEquals(payment.history.size, completed.history.size)
        assertEquals(payment.isPaymentLocked(LocalDate.of(2026, 10, 4)),
            completed.isPaymentLocked(LocalDate.of(2026, 10, 4)))
    }

    @Test fun completingOldAmountDoesNotGiveUndoRightsToEarlierRecords() {
        val undone = payment.undoLastPayment(5)
        assertFalse(undone.undoAvailable)
        val completed = undone.completeHistoryAmount(0, old, "20.00", PaymentCurrency.USD)
        assertFalse(completed.undoAvailable)
        assertEquals(completed, completed.undoLastPayment(5))
    }

    @Test fun undoStillRemovesOnlyLatestPaymentAfterEarlierAmountIsCompleted() {
        val completed = payment.completeHistoryAmount(0, old, "20.00", PaymentCurrency.USD)
        val undone = completed.undoLastPayment(5)
        assertEquals(listOf(completed.history.first()), undone.history)
        assertEquals(last.dueDate, undone.nextDueDate)
        assertEquals(undone, undone.undoLastPayment(5))
    }

    @Test fun rejectsMissingOrStaleRecordAndCannotOverwriteKnownSnapshot() {
        listOf(-1, 2).forEach { index ->
            assertThrows(IllegalArgumentException::class.java) {
                payment.completeHistoryAmount(index, old, "10.00", PaymentCurrency.TRY)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            payment.completeHistoryAmount(0, old.copy(dueDate = old.dueDate.plusDays(1)), "10.00", PaymentCurrency.TRY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            payment.completeHistoryAmount(1, last, "10.00", PaymentCurrency.TRY)
        }
    }

    @Test fun rejectsInvalidAmountsBeforeModifyingHistory() {
        listOf("", "0", "-1", "1.234,50", "1.001", "1000000000", "NaN").forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) {
                payment.completeHistoryAmount(0, old, amount, PaymentCurrency.TRY)
            }
        }
    }

    @Test fun acceptsTheLargestSupportedAmountExactly() {
        val completed = payment.completeHistoryAmount(0, old, "999999999.99", PaymentCurrency.TRY)
        assertEquals("999999999.99", completed.history.first().amount)
    }
}

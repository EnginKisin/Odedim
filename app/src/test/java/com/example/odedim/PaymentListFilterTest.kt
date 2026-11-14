package com.example.odedim

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PaymentListFilterTest {
    private val today = LocalDate.of(2026, 11, 14)
    private fun payment(id: Long, name: String = "Fatura", amount: String = "100.00",
        day: Long = 0, currency: PaymentCurrency = PaymentCurrency.TRY) =
        Payment(id, name, amount, today.plusDays(day), currency = currency)

    private fun ids(filter: PaymentListFilter, data: List<Payment>, query: String = "") =
        filter.apply(data, query, today).map { it.id }

    @Test fun defaultPreservesAllPaymentsAndTheirOrder() {
        val data = listOf(payment(5), payment(1), payment(3))
        assertEquals(data, PaymentListFilter().apply(data, "", today))
        assertEquals(0, PaymentListFilter().activeCount)
    }

    @Test fun nameSearchIgnoresCaseTurkishLettersAccentsAndSurroundingSpaces() {
        val data = listOf(payment(1, "İNTERNET Faturası"), payment(2, "İş Bankası"), payment(3, "Doğalgaz"))
        listOf("internet", "  İnTeRnEt  ", "İNTERNET").forEach { query ->
            assertEquals(listOf(1L), ids(PaymentListFilter(), data, query))
        }
        assertEquals(listOf(2L), ids(PaymentListFilter(), data, "IS BANKASI"))
        assertEquals(listOf(3L), ids(PaymentListFilter(), data, "dogalgaz"))
        assertTrue(ids(PaymentListFilter(), data, "kira").isEmpty())
        assertEquals(listOf(1L, 2L, 3L), ids(PaymentListFilter(), data, "  "))
    }

    @Test fun amountLimitsAreInclusiveExactAndRestrictedToOneCurrency() {
        val data = listOf(payment(1, amount = "10.09"), payment(2, amount = "10.10"),
            payment(3, amount = "20.20"), payment(4, amount = "20.21"),
            payment(5, amount = "15.00", currency = PaymentCurrency.USD))
        val filter = PaymentListFilter(PaymentCurrency.TRY, BigDecimal("10.10"), BigDecimal("20.20"))
        assertEquals(listOf(2L, 3L), ids(filter, data))
        assertEquals(2, filter.activeCount)
    }

    @Test fun eitherAmountBoundaryCanBeOmittedAndZeroIsValid() {
        val data = listOf(payment(1, amount = "0.01"), payment(2, amount = "5.00"))
        assertEquals(listOf(2L), ids(PaymentListFilter(PaymentCurrency.TRY, minimum = BigDecimal("5")), data))
        assertEquals(listOf(1L), ids(PaymentListFilter(PaymentCurrency.TRY, maximum = BigDecimal("0.01")), data))
        assertEquals(listOf(1L, 2L), ids(PaymentListFilter(PaymentCurrency.TRY, minimum = BigDecimal.ZERO), data))
        assertTrue(ids(PaymentListFilter(PaymentCurrency.TRY, maximum = BigDecimal.ZERO), data).isEmpty())
    }

    @Test fun datePresetsUseUpcomingDueDatesAndIncludeTheirExactBoundaries() {
        val data = listOf(-1L, 0L, 6L, 7L, 29L, 30L).mapIndexed { index, day -> payment(index + 1L, day = day) }
        assertEquals(listOf(1L), ids(PaymentListFilter(due = DueFilter.OVERDUE), data))
        assertEquals(listOf(2L), ids(PaymentListFilter(due = DueFilter.TODAY), data))
        assertEquals(listOf(2L, 3L), ids(PaymentListFilter(due = DueFilter.NEXT_7_DAYS), data))
        assertEquals(listOf(2L, 3L, 4L, 5L), ids(PaymentListFilter(due = DueFilter.NEXT_30_DAYS), data))
    }

    @Test fun customDateRangeIsInclusiveAndCanHaveAnOpenEnd() {
        val data = listOf(-10L, -1L, 0L, 3L, 9L).mapIndexed { index, day -> payment(index + 1L, day = day) }
        assertEquals(listOf(2L, 3L, 4L), ids(PaymentListFilter(due = DueFilter.CUSTOM,
            from = today.minusDays(1), until = today.plusDays(3)), data))
        assertEquals(listOf(4L, 5L), ids(PaymentListFilter(due = DueFilter.CUSTOM, from = today.plusDays(3)), data))
        assertEquals(listOf(1L, 2L), ids(PaymentListFilter(due = DueFilter.CUSTOM, until = today.minusDays(1)), data))
        assertEquals(listOf(3L), ids(PaymentListFilter(due = DueFilter.CUSTOM, from = today, until = today), data))
    }

    @Test fun searchCurrencyAmountAndDateCombineRatherThanOverrideEachOther() {
        val data = listOf(payment(1, "İnternet", "100.00", 3),
            payment(2, "İnternet", "100.00", 7), payment(3, "İnternet", "90.00", 3),
            payment(4, "Kira", "100.00", 3), payment(5, "İnternet", "100.00", 3, PaymentCurrency.EUR))
        val filter = PaymentListFilter(PaymentCurrency.TRY, BigDecimal("100"), BigDecimal("100"), DueFilter.NEXT_7_DAYS)
        assertEquals(listOf(1L), ids(filter, data, "internet"))
        assertEquals(3, filter.activeCount)
    }

    @Test fun amountSortUsesNumericValuesAndOnlyTheChosenCurrency() {
        val data = listOf(payment(1, amount = "100.00"), payment(2, amount = "9.99"),
            payment(3, amount = "20.00"), payment(4, amount = "1.00", currency = PaymentCurrency.EUR))
        assertEquals(listOf(2L, 3L, 1L), ids(PaymentListFilter(
            currency = PaymentCurrency.TRY, sort = PaymentSort.AMOUNT_ASCENDING), data))
        assertEquals(listOf(1L, 3L, 2L), ids(PaymentListFilter(
            currency = PaymentCurrency.TRY, sort = PaymentSort.AMOUNT_DESCENDING), data))
    }

    @Test fun dueAndNameSortingDoNotMutateTheSourceOrPaymentHistory() {
        val data = listOf(payment(1, "C", day = 3), payment(2, "A", day = -2), payment(3, "B", day = 1))
        val original = data.toList()
        assertEquals(listOf(2L, 3L, 1L), ids(PaymentListFilter(sort = PaymentSort.DUE_ASCENDING), data))
        assertEquals(listOf(1L, 3L, 2L), ids(PaymentListFilter(sort = PaymentSort.DUE_DESCENDING), data))
        assertEquals(listOf(2L, 3L, 1L), ids(PaymentListFilter(sort = PaymentSort.NAME), data))
        assertEquals(original, data)
    }

    @Test fun dueFiltersFollowTheNextCycleAfterPayingAndUndoing() {
        val original = payment(1)
        val filter = PaymentListFilter(due = DueFilter.TODAY)
        val paid = original.markPaid(today, 5)
        assertTrue(filter.apply(listOf(paid), "", today).isEmpty())
        assertEquals(listOf(original), filter.apply(listOf(paid.undoLastPayment(5)), "", today))
    }

    @Test fun invalidRangesAndMixedCurrencyAmountSortAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PaymentListFilter(minimum = BigDecimal.ONE) }
        assertThrows(IllegalArgumentException::class.java) {
            PaymentListFilter(PaymentCurrency.TRY, BigDecimal("10"), BigDecimal("9"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaymentListFilter(currency = PaymentCurrency.TRY, minimum = BigDecimal("-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaymentListFilter(due = DueFilter.CUSTOM, from = today.plusDays(1), until = today)
        }
        assertThrows(IllegalArgumentException::class.java) { PaymentListFilter(due = DueFilter.CUSTOM) }
        assertThrows(IllegalArgumentException::class.java) { PaymentListFilter(from = today) }
        PaymentListFilter.amountSorts.forEach { sort ->
            assertThrows(IllegalArgumentException::class.java) { PaymentListFilter(sort = sort) }
        }
    }

    @Test fun filterBoundariesAcceptBankStyleAmountsWithoutRoundingOrNegativeValues() {
        assertEquals(BigDecimal.ZERO, PaymentListFilter.amountBoundary("0", PaymentCurrency.TRY))
        assertEquals(BigDecimal("1234.50"), PaymentListFilter.amountBoundary("1.234,50", PaymentCurrency.TRY))
        assertEquals(BigDecimal("0.01"), PaymentListFilter.amountBoundary("0,01", PaymentCurrency.EUR))
        listOf("", "-1", "1,001", "1000000000", "9.99 USD").forEach {
            assertNull(PaymentListFilter.amountBoundary(it, PaymentCurrency.TRY))
        }
    }
}

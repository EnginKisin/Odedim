package com.example.odedim

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class PaymentSummaryTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun record(
        paid: LocalDate = today,
        amount: String = "10.00",
        currency: PaymentCurrency = PaymentCurrency.TRY,
        due: LocalDate = paid
    ) = PaymentRecord(paid, due, amount, currency)

    private fun payment(
        records: List<PaymentRecord> = emptyList(),
        due: LocalDate = today.plusDays(3),
        amount: String = "100.00",
        currency: PaymentCurrency = PaymentCurrency.TRY
    ) = Payment(1, "Fatura", amount, due, history = records, paymentTime = LocalTime.NOON, currency = currency)

    private fun summary(payments: List<Payment>, currency: PaymentCurrency = PaymentCurrency.TRY) =
        PaymentSummaries.calculate(payments, today, currency)

    @Test fun emptyDataContainsTwelveCalendarMonthsAndNoInventedRate() {
        val result = summary(emptyList())
        assertEquals(12, result.months.size)
        assertEquals(YearMonth.of(2025, 12), result.months.first().month)
        assertEquals(YearMonth.of(2026, 11), result.currentMonth.month)
        assertTrue(result.months.all { it.amount.signum() == 0 && it.paymentCount == 0 })
        assertEquals(0, result.upcomingCount)
        assertNull(result.onTimePercent)
    }

    @Test fun currenciesStaySeparateEvenWithinTheSamePaymentsHistory() {
        val data = listOf(payment(
            records = listOf(record(amount = "10.10"), record(amount = "20.20", currency = PaymentCurrency.USD),
                record(amount = "30.30", currency = PaymentCurrency.EUR)),
            amount = "999.00", currency = PaymentCurrency.EUR
        ))
        listOf(PaymentCurrency.TRY to "10.10", PaymentCurrency.USD to "20.20",
            PaymentCurrency.EUR to "30.30").forEach { (currency, total) ->
            val result = summary(data, currency)
            assertEquals(BigDecimal(total), result.currentMonth.amount)
            assertEquals(1, result.currentMonth.paymentCount)
        }
        assertEquals(0, summary(data, PaymentCurrency.TRY).upcomingCount)
        assertEquals(BigDecimal("999.00"), summary(data, PaymentCurrency.EUR).upcomingAmount)
    }

    @Test fun editingFutureAmountAndCurrencyCannotRewritePaidTotals() {
        val original = payment(records = emptyList(), due = today, amount = "100.25").markPaid(today, 5)
        val edited = original.edited("Yeni ad", "777.00", original.repeatRule,
            original.nextDueDate, LocalTime.NOON, PaymentCurrency.USD)
        assertEquals(BigDecimal("100.25"), summary(listOf(edited)).currentMonth.amount)
        assertEquals(BigDecimal.ZERO, summary(listOf(edited), PaymentCurrency.USD).currentMonth.amount)
        assertEquals("100.25", edited.history.single().amount)
        assertEquals(PaymentCurrency.TRY, edited.history.single().currency)
    }

    @Test fun monthsFollowActualPaymentDateRatherThanDueDate() {
        val data = listOf(payment(records = listOf(
            record(paid = LocalDate.of(2026, 11, 2), due = LocalDate.of(2026, 10, 30), amount = "30.00"),
            record(paid = LocalDate.of(2026, 10, 31), due = LocalDate.of(2026, 11, 10), amount = "20.00")
        )))
        val result = summary(data)
        assertEquals(BigDecimal("30.00"), result.currentMonth.amount)
        assertEquals(BigDecimal("20.00"), result.months[10].amount)
        assertEquals(1, result.onTimeCount)
        assertEquals(50, result.onTimePercent)
    }

    @Test fun oldAndFutureRecordsOutsideTheWindowAreExcluded() {
        val result = summary(listOf(payment(records = listOf(
            record(LocalDate.of(2025, 11, 30), "1.00"),
            record(LocalDate.of(2025, 12, 1), "2.00"),
            record(today, "3.00"),
            record(today.plusDays(1), "4.00")
        ))))
        assertEquals(2, result.historyCount)
        assertEquals(BigDecimal("2.00"), result.months.first().amount)
        assertEquals(BigDecimal("3.00"), result.currentMonth.amount)
    }

    @Test fun legacyAmountsAndCurrenciesAreNeverInferredFromCurrentPayment() {
        val legacy = PaymentRecord(today, today.minusDays(1))
        PaymentCurrency.entries.forEach { currency ->
            val result = summary(listOf(payment(listOf(legacy), amount = "999.00", currency = currency)), currency)
            assertEquals(BigDecimal.ZERO, result.currentMonth.amount)
            assertEquals(0, result.currentMonth.paymentCount)
            assertEquals(1, result.currentMonth.unknownAmountCount)
            assertEquals(1, result.unknownAmountCount)
            assertEquals(1, result.historyCount)
            assertEquals(0, result.onTimePercent)
        }
    }

    @Test fun incompleteSnapshotsAreUnknownRatherThanGuessed() {
        val result = summary(listOf(payment(listOf(
            PaymentRecord(today, today, amount = "100.00"),
            PaymentRecord(today, today, currency = PaymentCurrency.EUR)
        ))))
        assertEquals(2, result.unknownAmountCount)
        assertEquals(BigDecimal.ZERO, result.currentMonth.amount)
    }

    @Test fun upcomingRangeIncludesTodayAndSixFollowingDaysButNotOverdueOrEighthDay() {
        val data = listOf(-1L, 0L, 6L, 7L).map { offset ->
            payment(due = today.plusDays(offset), amount = "10.00").copy(id = offset + 2)
        } + payment(due = today.plusDays(3), amount = "999.00", currency = PaymentCurrency.USD).copy(id = 20)
        val result = summary(data)
        assertEquals(2, result.upcomingCount)
        assertEquals(BigDecimal("20.00"), result.upcomingAmount)
    }

    @Test fun aPaidPreviousCycleStillHasAnUpcomingFutureObligation() {
        val data = payment(listOf(record(paid = today.minusMonths(1))),
            due = today.plusDays(2)).copy(periodPaid = true)
        assertTrue(data.isPaymentLocked(today))
        assertEquals(1, summary(listOf(data)).upcomingCount)
    }

    @Test fun decimalTotalsRemainExact() {
        val result = summary(listOf(payment(listOf(record(amount = "0.10"), record(amount = "0.20")))))
        assertEquals(BigDecimal("0.30"), result.currentMonth.amount)
    }

    @Test fun onTimeRateUsesRecordedDatesAcrossCurrenciesAndIncludesTheDueDay() {
        val result = summary(listOf(payment(listOf(
            record(due = today.plusDays(1), currency = PaymentCurrency.USD),
            PaymentRecord(today, today),
            record(due = today.minusDays(1), currency = PaymentCurrency.EUR)
        ))))
        assertEquals(2, result.onTimeCount)
        assertEquals(3, result.historyCount)
        assertEquals(67, result.onTimePercent)
    }

    @Test fun summaryRespectsTheTwentyFourRecordRetentionLimit() {
        val records = List(26) { index -> record(today.minusDays(25L - index), "1.00") }
        val result = summary(listOf(payment(records)))
        assertEquals(24, result.historyCount)
        assertEquals(BigDecimal("24.00"), result.months.fold(BigDecimal.ZERO) { total, month -> total + month.amount })
    }


    @Test fun eachPeriodUsesCalendarBoundariesAndKeepsCurrentMonthAndUpcomingCardsStable() {
        PaymentSummaries.periods.forEach { count ->
            val first = YearMonth.from(today).minusMonths(count - 1L).atDay(1)
            val data = listOf(payment(records = listOf(
                record(first.minusDays(1), "99.00"),
                record(first, "10.00"),
                record(today, "20.00"),
                record(today.plusDays(1), "999.00")
            )))
            val result = PaymentSummaries.calculate(data, today, PaymentCurrency.TRY, count)
            assertEquals(count, result.months.size)
            assertEquals(YearMonth.from(first), result.months.first().month)
            assertEquals(YearMonth.from(today), result.currentMonth.month)
            assertEquals(2, result.historyCount)
            assertEquals(100, result.onTimePercent)
            assertEquals(BigDecimal("30.00"), result.months.fold(BigDecimal.ZERO) { total, month -> total + month.amount })
            assertEquals(if (count == 1) BigDecimal("30.00") else BigDecimal("20.00"), result.currentMonth.amount)
            assertEquals(BigDecimal("100.00"), result.upcomingAmount)
            assertEquals(1, result.upcomingCount)
        }
    }

    @Test fun changingPeriodFiltersUnknownRecordsAndTimelinessTogether() {
        val older = today.minusMonths(2)
        val data = listOf(payment(listOf(
            PaymentRecord(older, older.minusDays(1)),
            record(today, "10.00")
        )))
        val oneMonth = PaymentSummaries.calculate(data, today, PaymentCurrency.TRY, 1)
        val threeMonths = PaymentSummaries.calculate(data, today, PaymentCurrency.TRY, 3)
        assertEquals(100, oneMonth.onTimePercent)
        assertEquals(0, oneMonth.unknownAmountCount)
        assertEquals(50, threeMonths.onTimePercent)
        assertEquals(1, threeMonths.unknownAmountCount)
        assertEquals(oneMonth.currentMonth, threeMonths.currentMonth)
    }

    @Test fun twentyFourMonthWindowCrossesYearsAndLeapFebruary() {
        val date = LocalDate.of(2025, 1, 15)
        val data = listOf(payment(listOf(record(LocalDate.of(2024, 2, 29), "29.00"))))
        val result = PaymentSummaries.calculate(data, date, PaymentCurrency.TRY, 24)
        assertEquals(YearMonth.of(2023, 2), result.months.first().month)
        assertEquals(YearMonth.of(2025, 1), result.currentMonth.month)
        assertEquals(BigDecimal("29.00"), result.months.single { it.month == YearMonth.of(2024, 2) }.amount)
        assertEquals(1, result.historyCount)
    }

    @Test fun fillingLegacyAmountIncludesOnlyTheActualEnteredCurrencyAndOriginalMonth() {
        val legacy = PaymentRecord(today.minusMonths(1), today.minusMonths(1).plusDays(1))
        val original = payment(listOf(legacy), amount = "999.00", currency = PaymentCurrency.TRY)
        val completed = original.completeHistoryAmount(0, legacy, "12.34", PaymentCurrency.USD)
        val dollar = summary(listOf(completed), PaymentCurrency.USD)
        assertEquals(BigDecimal("12.34"), dollar.months[10].amount)
        assertEquals(0, dollar.unknownAmountCount)
        assertEquals(BigDecimal.ZERO, dollar.currentMonth.amount)
        assertTrue(summary(listOf(completed)).months.all { it.amount.signum() == 0 })
        assertEquals(summary(listOf(original)).onTimePercent, dollar.onTimePercent)
    }

    @Test fun unsupportedPeriodsAreRejected() {
        listOf(0, -1, 2, 25, Int.MAX_VALUE).forEach { count ->
            assertThrows(IllegalArgumentException::class.java) {
                PaymentSummaries.calculate(emptyList(), today, PaymentCurrency.TRY, count)
            }
        }
    }

    @Test fun undoRemovesItsSnapshotFromTheSummary() {
        val paid = payment(due = today).markPaid(today, 5)
        assertEquals(BigDecimal("100.00"), summary(listOf(paid)).currentMonth.amount)
        val undo = paid.undoLastPayment(5)
        assertEquals(BigDecimal.ZERO, summary(listOf(undo)).currentMonth.amount)
        assertEquals(0, summary(listOf(undo)).historyCount)
        assertNull(summary(listOf(undo)).onTimePercent)
    }
}

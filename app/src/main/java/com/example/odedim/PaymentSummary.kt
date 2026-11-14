package com.example.odedim

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

data class MonthlyPaymentTotal(
    val month: YearMonth,
    val amount: BigDecimal,
    val paymentCount: Int,
    val unknownAmountCount: Int
)

data class PaymentSummary(
    val currency: PaymentCurrency,
    val months: List<MonthlyPaymentTotal>,
    val upcomingAmount: BigDecimal,
    val upcomingCount: Int,
    val onTimeCount: Int,
    val historyCount: Int,
    val unknownAmountCount: Int
) {
    val currentMonth: MonthlyPaymentTotal get() = months.last()
    val onTimePercent: Int? get() =
        if (historyCount == 0) null else (onTimeCount * 100.0 / historyCount).roundToInt()
}

object PaymentSummaries {
    val periods = listOf(1, 3, 6, 12, 24)

    /** Amounts are exact snapshots. Unknown legacy values are never inferred from today's payment. */
    fun calculate(
        payments: List<Payment>, today: LocalDate, currency: PaymentCurrency, monthCount: Int = 12
    ): PaymentSummary {
        require(monthCount in periods)
        val lastMonth = YearMonth.from(today)
        val firstMonth = lastMonth.minusMonths(monthCount - 1L)
        val records = payments.flatMap { it.history.takeLast(Payment.HISTORY_LIMIT) }
            .filter { it.paidOn <= today && YearMonth.from(it.paidOn) >= firstMonth }
        val recordsByMonth = records.groupBy { YearMonth.from(it.paidOn) }
        val months = List(monthCount) { index ->
            val month = firstMonth.plusMonths(index.toLong())
            val monthlyRecords = recordsByMonth[month].orEmpty()
            val priced = monthlyRecords.filter { it.hasAmountSnapshot && it.currency == currency }
            MonthlyPaymentTotal(
                month = month,
                amount = priced.fold(BigDecimal.ZERO) { total, record ->
                    total + requireNotNull(record.amount).toBigDecimal()
                },
                paymentCount = priced.size,
                unknownAmountCount = monthlyRecords.count { !it.hasAmountSnapshot }
            )
        }
        // Includes today and the following six calendar days, regardless of the previous cycle's lock.
        val nextWeek = payments.filter {
            it.currency == currency && it.nextDueDate >= today && it.nextDueDate <= today.plusDays(6)
        }
        return PaymentSummary(
            currency = currency,
            months = months,
            upcomingAmount = nextWeek.fold(BigDecimal.ZERO) { total, payment -> total + payment.amount.toBigDecimal() },
            upcomingCount = nextWeek.size,
            onTimeCount = records.count { it.paidOn <= it.dueDate },
            historyCount = records.size,
            unknownAmountCount = records.count { !it.hasAmountSnapshot }
        )
    }
}

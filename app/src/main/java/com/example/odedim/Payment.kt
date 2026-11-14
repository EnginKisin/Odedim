package com.example.odedim

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class RepeatType {
    AFTER_30_DAYS,
    MONTHLY_DAY,
    SALARY_DAY
}

data class RepeatRule(
    val type: RepeatType = RepeatType.AFTER_30_DAYS,
    val monthlyDay: Int = 5
) {
    init {
        require(monthlyDay in 1..31)
    }
}

data class PaymentRecord(
    val paidOn: LocalDate,
    val dueDate: LocalDate,
    val amount: String? = null,
    val currency: PaymentCurrency? = null
) {
    val hasAmountSnapshot: Boolean get() = amount != null && currency != null
}

data class Payment(
    val id: Long,
    val name: String,
    val amount: String,
    val nextDueDate: LocalDate,
    val repeatRule: RepeatRule = RepeatRule(),
    val history: List<PaymentRecord> = emptyList(),
    val paymentTime: LocalTime? = null,
    val periodPaid: Boolean = history.isNotEmpty(),
    val undoAvailable: Boolean = history.isNotEmpty(),
    val currency: PaymentCurrency = PaymentCurrency.TRY
) {
    fun isPaymentLocked(today: LocalDate): Boolean = periodPaid && today < nextDueDate

    fun markPaid(paidOn: LocalDate, salaryDay: Int): Payment {
        if (isPaymentLocked(paidOn)) return this
        return copy(
            nextDueDate = PaymentSchedule.nextDueDate(repeatRule, nextDueDate, paidOn, salaryDay),
            history = (history + PaymentRecord(paidOn, nextDueDate, amount, currency)).takeLast(HISTORY_LIMIT),
            periodPaid = true,
            undoAvailable = true
        )
    }

    fun undoLastPayment(salaryDay: Int): Payment {
        if (!undoAvailable) return this
        val lastRecord = history.lastOrNull() ?: return this
        return copy(
            nextDueDate = PaymentSchedule.alignSalaryDate(repeatRule, lastRecord.dueDate, salaryDay),
            history = history.dropLast(1),
            periodPaid = false,
            undoAvailable = false
        )
    }

    /** Completes a legacy snapshot without changing dates, the tracked cycle, or undo eligibility. */
    fun completeHistoryAmount(
        index: Int, expectedRecord: PaymentRecord, amount: String, currency: PaymentCurrency
    ): Payment {
        require(index in history.indices && history[index] == expectedRecord)
        require(!expectedRecord.hasAmountSnapshot)
        require(amount.matches(Regex("[0-9]{1,9}(\\.[0-9]{1,2})?")) &&
            amount.toBigDecimalOrNull()?.signum() == 1)
        return copy(history = history.mapIndexed { recordIndex, record ->
            if (recordIndex == index) record.copy(amount = amount, currency = currency) else record
        })
    }

    fun updateSalaryDay(salaryDay: Int): Payment = copy(
        nextDueDate = PaymentSchedule.alignSalaryDate(repeatRule, nextDueDate, salaryDay)
    )

    fun edited(
        name: String, amount: String, rule: RepeatRule, dueDate: LocalDate, time: LocalTime,
        currency: PaymentCurrency = this.currency
    ): Payment {
        require(name.isNotBlank())
        require(amount.matches(Regex("[0-9]{1,9}(\\.[0-9]{1,2})?")) &&
            amount.toBigDecimalOrNull()?.signum() == 1)
        return copy(
            name = name.trim(), amount = amount, currency = currency,
            repeatRule = rule, nextDueDate = dueDate,
            paymentTime = time.withSecond(0).withNano(0),
            history = history.takeLast(HISTORY_LIMIT)
        )
    }

    companion object {
        const val HISTORY_LIMIT = 24
    }
}

/** Calendar-only calculations: no clock, Android API, or time-zone conversion. */
object PaymentSchedule {
    fun firstDueDate(rule: RepeatRule, today: LocalDate, salaryDay: Int): LocalDate {
        require(salaryDay in 1..31)
        if (rule.type == RepeatType.AFTER_30_DAYS) return today
        val day = if (rule.type == RepeatType.SALARY_DAY) salaryDay else rule.monthlyDay
        val month = YearMonth.from(today)
        val candidate = dateInMonth(month, day)
        return if (candidate < today) dateInMonth(month.plusMonths(1), day) else candidate
    }

    fun nextDueDate(
        rule: RepeatRule,
        currentDueDate: LocalDate,
        paidOn: LocalDate,
        salaryDay: Int
    ): LocalDate {
        require(salaryDay in 1..31)
        if (rule.type == RepeatType.AFTER_30_DAYS) return paidOn.plusDays(30)

        val day = if (rule.type == RepeatType.SALARY_DAY) salaryDay else rule.monthlyDay
        // Always settle the displayed period, even when paid before its due month.
        // Late payments advance to the first future due date, skipping elapsed periods.
        val earliestMonth = YearMonth.from(currentDueDate).plusMonths(1)
        val paidMonth = YearMonth.from(paidOn)
        val nextMonth = maxOf(earliestMonth, paidMonth)
        val candidate = dateInMonth(nextMonth, day)
        return if (candidate <= paidOn) dateInMonth(nextMonth.plusMonths(1), day) else candidate
    }

    fun alignSalaryDate(rule: RepeatRule, dueDate: LocalDate, salaryDay: Int): LocalDate {
        require(salaryDay in 1..31)
        // Changing the salary day retains the tracked period, including overdue periods.
        return if (rule.type == RepeatType.SALARY_DAY) {
            dateInMonth(YearMonth.from(dueDate), salaryDay)
        } else {
            dueDate
        }
    }

    fun dateForRuleInPeriod(rule: RepeatRule, periodDate: LocalDate, salaryDay: Int): LocalDate {
        require(salaryDay in 1..31)
        return when (rule.type) {
            RepeatType.AFTER_30_DAYS -> periodDate
            RepeatType.MONTHLY_DAY -> dateInMonth(YearMonth.from(periodDate), rule.monthlyDay)
            RepeatType.SALARY_DAY -> dateInMonth(YearMonth.from(periodDate), salaryDay)
        }
    }

    private fun dateInMonth(month: YearMonth, day: Int): LocalDate =
        month.atDay(minOf(day, month.lengthOfMonth()))
}

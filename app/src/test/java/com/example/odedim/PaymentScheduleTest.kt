package com.example.odedim

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentScheduleTest {
    private val after30 = RepeatRule()
    private val monthly20 = RepeatRule(RepeatType.MONTHLY_DAY, 20)
    private val salary = RepeatRule(RepeatType.SALARY_DAY)
    private fun date(value: String): LocalDate = LocalDate.parse(value)

    private fun next(
        rule: RepeatRule,
        due: String,
        paid: String,
        salaryDay: Int = 5
    ): LocalDate = PaymentSchedule.nextDueDate(rule, date(due), date(paid), salaryDay)

    @Test
    fun thirtyDaysStartsFromActualPaymentForEarlyAndLatePayments() {
        assertEquals(date("2026-12-10"), next(after30, "2026-11-20", "2026-11-10"))
        assertEquals(date("2026-12-30"), next(after30, "2026-11-20", "2026-11-30"))
    }

    @Test
    fun thirtyDaysIsCalendarDaysRatherThanOneMonth() {
        assertEquals(date("2025-03-02"), next(after30, "2025-01-31", "2025-01-31"))
        assertEquals(date("2024-02-29"), next(after30, "2024-01-30", "2024-01-30"))
        assertEquals(date("2027-01-14"), next(after30, "2026-12-15", "2026-12-15"))
    }

    @Test
    fun firstDueDateForThirtyDaysIsToday() {
        assertEquals(
            date("2026-11-09"),
            PaymentSchedule.firstDueDate(after30, date("2026-11-09"), 5)
        )
    }

    @Test
    fun firstFixedDueDateIncludesTodayAndSkipsPassedDays() {
        listOf(
            "2026-11-09" to "2026-11-20",
            "2026-11-20" to "2026-11-20",
            "2026-11-21" to "2026-12-20"
        ).forEach { (today, expected) ->
            assertEquals(date(expected), PaymentSchedule.firstDueDate(monthly20, date(today), 5))
        }
    }

    @Test
    fun firstSalaryDueDateUsesConfiguredDay() {
        assertEquals(date("2026-12-05"), PaymentSchedule.firstDueDate(salary, date("2026-11-09"), 5))
        assertEquals(date("2026-11-15"), PaymentSchedule.firstDueDate(salary, date("2026-11-09"), 15))
    }

    @Test
    fun earlyFixedPaymentPreservesNextScheduledMonth() {
        assertEquals(date("2026-12-20"), next(monthly20, "2026-11-20", "2026-11-10"))
        assertEquals(date("2026-12-20"), next(monthly20, "2026-11-20", "2026-10-01"))
    }

    @Test
    fun paymentOnDueDateAdvancesOnePeriod() {
        assertEquals(date("2026-12-20"), next(monthly20, "2026-11-20", "2026-11-20"))
        assertEquals(date("2026-12-05"), next(salary, "2026-11-05", "2026-11-05"))
    }

    @Test
    fun lateFixedPaymentAdvancesToFirstFutureDueDate() {
        assertEquals(date("2027-03-20"), next(monthly20, "2026-11-20", "2027-03-10"))
        assertEquals(date("2027-04-20"), next(monthly20, "2026-11-20", "2027-03-20"))
        assertEquals(date("2027-04-20"), next(monthly20, "2026-11-20", "2027-03-25"))
    }

    @Test
    fun days29Through31ClampToFebruaryAndReturnToOriginalDayInMarch() {
        for (day in 29..31) {
            val rule = RepeatRule(RepeatType.MONTHLY_DAY, day)
            val januaryDue = LocalDate.of(2025, 1, day)
            val february = PaymentSchedule.nextDueDate(rule, januaryDue, januaryDue, 5)
            assertEquals(date("2025-02-28"), february)
            assertEquals(
                LocalDate.of(2025, 3, day),
                PaymentSchedule.nextDueDate(rule, february, february, 5)
            )
        }
    }

    @Test
    fun leapFebruaryUses29ForDays29Through31() {
        for (day in 29..31) {
            val rule = RepeatRule(RepeatType.MONTHLY_DAY, day)
            assertEquals(
                date("2024-02-29"),
                next(rule, "2024-01-" + day, "2024-01-" + day)
            )
        }
    }

    @Test
    fun firstDueDateAlsoClampsShortMonths() {
        val rule = RepeatRule(RepeatType.MONTHLY_DAY, 31)
        assertEquals(date("2025-02-28"), PaymentSchedule.firstDueDate(rule, date("2025-02-01"), 5))
        assertEquals(date("2024-02-29"), PaymentSchedule.firstDueDate(rule, date("2024-02-29"), 5))
        assertEquals(date("2025-04-30"), PaymentSchedule.firstDueDate(rule, date("2025-04-01"), 5))
    }

    @Test
    fun shortThirtyDayMonthsAndYearRolloverRetainRule() {
        val rule = RepeatRule(RepeatType.MONTHLY_DAY, 31)
        assertEquals(date("2026-04-30"), next(rule, "2026-03-31", "2026-03-31"))
        assertEquals(date("2026-05-31"), next(rule, "2026-04-30", "2026-04-30"))
        assertEquals(date("2027-01-31"), next(rule, "2026-12-31", "2026-12-31"))
    }

    @Test
    fun salaryPaymentsFollowSettingsAndPreserveEarlyPaymentSchedule() {
        assertEquals(date("2026-12-05"), next(salary, "2026-11-05", "2026-11-01"))
        assertEquals(date("2026-12-15"), next(salary, "2026-11-15", "2026-11-01", 15))
        assertEquals(date("2027-04-05"), next(salary, "2026-11-05", "2027-03-10"))
    }

    @Test
    fun salaryDay31AlsoRecoversAfterShortMonths() {
        assertEquals(date("2025-02-28"), next(salary, "2025-01-31", "2025-01-20", 31))
        assertEquals(date("2025-03-31"), next(salary, "2025-02-28", "2025-02-20", 31))
    }

    @Test
    fun markPaidRecordsActualDateAndSettledPeriodAndKeepsPaymentActive() {
        val original = Payment(1, "Kira", "100", date("2026-11-20"), monthly20)
        val paid = original.markPaid(date("2026-11-10"), 5)
        assertEquals(date("2026-12-20"), paid.nextDueDate)
        assertEquals(
            listOf(PaymentRecord(date("2026-11-10"), date("2026-11-20"), "100", PaymentCurrency.TRY)),
            paid.history
        )
        assertTrue(original.history.isEmpty())
        assertEquals(original, paid.undoLastPayment(5))
    }

    @Test
    fun undoRestoresOnlyLatestPeriodWithoutUnlockingOlderHistory() {
        val original = Payment(1, "Kira", "100", date("2026-11-20"), monthly20)
        val once = original.markPaid(date("2026-11-10"), 5)
        val twice = once.markPaid(date("2026-12-20"), 5)
        assertEquals(date("2027-01-20"), twice.nextDueDate)
        assertEquals(2, twice.history.size)
        val undone = twice.undoLastPayment(5)
        assertEquals(once.nextDueDate, undone.nextDueDate)
        assertEquals(once.history, undone.history)
        assertTrue(!undone.undoAvailable)
        assertEquals(undone, undone.undoLastPayment(5))
    }

    @Test
    fun undoWithoutHistoryDoesNothing() {
        val payment = Payment(1, "Kira", "100", date("2026-11-20"))
        assertEquals(payment, payment.undoLastPayment(5))
    }

    @Test
    fun salaryChangeKeepsAlreadyAdvancedPeriodAndHistory() {
        val original = Payment(1, "Kira", "100", date("2026-11-05"), salary)
        val paid = original.markPaid(date("2026-11-01"), 5)
        val changed = paid.updateSalaryDay(31)
        assertEquals(date("2026-12-31"), changed.nextDueDate)
        assertEquals(paid.history, changed.history)
        assertEquals(date("2026-11-30"), changed.undoLastPayment(31).nextDueDate)
        assertTrue(changed.undoLastPayment(31).history.isEmpty())
    }

    @Test
    fun salaryChangeClampsShortMonthsAndDoesNotAffectOtherRules() {
        val payment = Payment(1, "Kira", "100", date("2025-02-05"), salary)
        assertEquals(date("2025-02-28"), payment.updateSalaryDay(31).nextDueDate)
        for (rule in listOf(after30, monthly20)) {
            val fixed = payment.copy(repeatRule = rule)
            assertEquals(fixed, fixed.updateSalaryDay(31))
        }
    }

    @Test
    fun undoForThirtyDaysRestoresExactPreviousDate() {
        val original = Payment(1, "Fatura", "100", date("2026-11-20"))
        assertEquals(original, original.markPaid(date("2026-11-09"), 5).undoLastPayment(5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroMonthlyDayIsRejected() {
        RepeatRule(RepeatType.MONTHLY_DAY, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun monthlyDayAbove31IsRejected() {
        RepeatRule(RepeatType.MONTHLY_DAY, 32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSalaryDayIsRejected() {
        PaymentSchedule.firstDueDate(salary, date("2026-11-09"), 0)
    }
}

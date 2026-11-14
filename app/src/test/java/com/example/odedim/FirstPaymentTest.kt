package com.example.odedim

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class FirstPaymentTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val due = LocalDate.of(2026, 12, 20)
    private fun create(firstDue: LocalDate = due, currency: PaymentCurrency = PaymentCurrency.TRY) =
        createPayment(1, "Yeni ödeme", "250.50", RepeatRule(), null,
            LocalTime.of(18, 30, 5), today, 5, currency, firstDue)

    @Test fun futureFirstDueIsExactAndDoesNotInventAPaymentOrLockTheButton() {
        val payment = create()
        assertEquals(due, payment.nextDueDate)
        assertEquals(LocalTime.of(18, 30), payment.paymentTime)
        assertTrue(payment.history.isEmpty())
        assertFalse(payment.periodPaid)
        assertFalse(payment.undoAvailable)
        assertFalse(payment.isPaymentLocked(today))
        assertEquals(payment, payment.undoLastPayment(5))
    }

    @Test fun firstActualPaymentStartsThirtyDaysFromItsActualDateAndUndoRestoresFirstDue() {
        val original = create()
        val paidOn = due.minusDays(2)
        val paid = original.markPaid(paidOn, 5)
        assertEquals(paidOn.plusDays(30), paid.nextDueDate)
        assertEquals(PaymentRecord(paidOn, due, "250.50", PaymentCurrency.TRY), paid.history.single())
        assertTrue(paid.isPaymentLocked(paidOn))
        assertEquals(original, paid.undoLastPayment(5))
    }

    @Test fun firstDueCanBeTodayOrAnUnpaidOverdueDateWithoutMakingAHistoryEntry() {
        listOf(today, today.minusDays(10), LocalDate.of(2024, 2, 29)).forEach { date ->
            val payment = create(date)
            assertEquals(date, payment.nextDueDate)
            assertTrue(payment.history.isEmpty())
            assertFalse(payment.periodPaid)
        }
    }

    @Test fun allCurrenciesWorkForFirstDueAndItsFourReminders() {
        PaymentCurrency.entries.forEach { currency ->
            val payment = create(currency = currency)
            assertEquals(currency, payment.currency)
            val plans = ReminderTimes.forPayment(payment, ZoneId.of("Europe/Istanbul"))
            assertEquals(4, plans.size)
            assertEquals(due.atTime(18, 30).atZone(ZoneId.of("Europe/Istanbul")).toInstant(),
                plans.single { it.kind == ReminderKind.DUE }.dueAt)
            assertEquals(4, ReminderTimes.upcoming(payment, today.atStartOfDay(ZoneId.of("Europe/Istanbul")).toInstant(),
                ZoneId.of("Europe/Istanbul")).size)
        }
    }

    @Test fun previousActualPaymentStillCreatesHistoryAndItsNextThirtyDayCycle() {
        val date = today.minusDays(1)
        val payment = createPayment(1, "Ödeme", "250.50", RepeatRule(), date,
            LocalTime.NOON, today, 5, PaymentCurrency.USD)
        assertEquals(date.plusDays(30), payment.nextDueDate)
        assertEquals(PaymentRecord(date, date, "250.50", PaymentCurrency.USD), payment.history.single())
        assertTrue(payment.undoAvailable)
        assertTrue(payment.periodPaid)
    }

    @Test fun cannotConfuseFirstDueWithAnActualFuturePaymentOrProvideBothDates() {
        assertThrows(IllegalArgumentException::class.java) {
            createPayment(1, "Ödeme", "10", RepeatRule(), due, LocalTime.NOON, today, 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createPayment(1, "Ödeme", "10", RepeatRule(), today, LocalTime.NOON, today, 5, firstDueDate = due)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createPayment(1, "Ödeme", "10", RepeatRule(), null, LocalTime.NOON, today, 5)
        }
    }

    @Test fun fixedAndSalaryRulesKeepTheirScheduledDatesWithoutHistory() {
        listOf(RepeatRule(RepeatType.MONTHLY_DAY, 31), RepeatRule(RepeatType.SALARY_DAY)).forEach { rule ->
            val payment = createPayment(1, "Ödeme", "10", rule, null, LocalTime.NOON, today, 5)
            assertEquals(PaymentSchedule.firstDueDate(rule, today, 5), payment.nextDueDate)
            assertTrue(payment.history.isEmpty())
            assertFalse(payment.periodPaid)
        }
    }
}

package com.example.odedim

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PaymentBehaviorTest {
    private val rule = RepeatRule(RepeatType.MONTHLY_DAY, 20)
    private val payment = Payment(1, "Fatura", "100", LocalDate.of(2026, 11, 20), rule,
        paymentTime = LocalTime.of(12, 0))

    @Test fun payingLocksNextCycleAndRejectsDuplicateOrEarlySecondPayment() {
        val paid = payment.markPaid(LocalDate.of(2026, 11, 10), 5)
        assertTrue(paid.isPaymentLocked(LocalDate.of(2026, 11, 10)))
        assertTrue(paid.isPaymentLocked(LocalDate.of(2026, 12, 19)))
        assertEquals(paid, paid.markPaid(LocalDate.of(2026, 11, 10), 5))
        assertEquals(paid, paid.markPaid(LocalDate.of(2026, 12, 19), 5))
        assertEquals(1, paid.history.size)
    }

    @Test fun nextDueDayOpensCycleAndPermitsNextPayment() {
        val paid = payment.markPaid(LocalDate.of(2026, 11, 10), 5)
        assertFalse(paid.isPaymentLocked(LocalDate.of(2026, 12, 20)))
        val next = paid.markPaid(LocalDate.of(2026, 12, 20), 5)
        assertEquals(LocalDate.of(2027, 1, 20), next.nextDueDate)
        assertEquals(2, next.history.size)
        assertTrue(next.isPaymentLocked(LocalDate.of(2026, 12, 20)))
    }

    @Test fun undoOpensPaymentButDoesNotAllowUndoOfEarlierRecords() {
        val first = payment.markPaid(LocalDate.of(2026, 11, 20), 5)
        val second = first.markPaid(LocalDate.of(2026, 12, 20), 5)
        val undo = second.undoLastPayment(5)
        assertEquals(first.nextDueDate, undo.nextDueDate)
        assertEquals(first.history, undo.history)
        assertFalse(undo.isPaymentLocked(LocalDate.of(2026, 12, 20)))
        assertFalse(undo.undoAvailable)
        assertEquals(undo, undo.undoLastPayment(5))
        val paidAgain = undo.markPaid(LocalDate.of(2026, 12, 20), 5)
        assertTrue(paidAgain.undoAvailable)
        assertEquals(2, paidAgain.history.size)
    }

    @Test fun lastTwentyFourRecordsAreKeptAndOlderRecordsCannotReturnOnUndo() {
        var current = payment.copy(nextDueDate = LocalDate.of(2020, 1, 20))
        repeat(36) { current = current.markPaid(current.nextDueDate, 5) }
        assertEquals(24, current.history.size)
        assertEquals(LocalDate.of(2021, 1, 20), current.history.first().paidOn)
        assertEquals(LocalDate.of(2022, 12, 20), current.history.last().paidOn)
        val undone = current.undoLastPayment(5)
        assertEquals(23, undone.history.size)
        assertEquals(current.history.first(), undone.history.first())
        assertEquals(undone, undone.undoLastPayment(5))
    }

    @Test fun editingNameAmountAndTimeKeepsHistoryAndCycleState() {
        val paid = payment.markPaid(LocalDate.of(2026, 11, 10), 5)
        val edited = paid.edited("Yeni fatura", "250.50", paid.repeatRule,
            paid.nextDueDate, LocalTime.of(18, 30))
        assertEquals("Yeni fatura", edited.name)
        assertEquals("250.50", edited.amount)
        assertEquals(LocalTime.of(18, 30), edited.paymentTime)
        assertEquals(paid.id, edited.id)
        assertEquals(paid.history, edited.history)
        assertEquals(paid.nextDueDate, edited.nextDueDate)
        assertTrue(edited.periodPaid)
        assertTrue(edited.undoAvailable)
    }

    @Test fun changingRuleKeepsTrackedMonthAndClampsShortMonths() {
        val date = LocalDate.of(2025, 2, 5)
        assertEquals(LocalDate.of(2025, 2, 28),
            PaymentSchedule.dateForRuleInPeriod(RepeatRule(RepeatType.MONTHLY_DAY, 31), date, 5))
        assertEquals(LocalDate.of(2025, 2, 28),
            PaymentSchedule.dateForRuleInPeriod(RepeatRule(RepeatType.SALARY_DAY), date, 31))
        assertEquals(date, PaymentSchedule.dateForRuleInPeriod(RepeatRule(), date, 5))
    }

    @Test fun dueReminderCanArriveAtOrShortlyAfterDeadline() {
        val due = Instant.parse("2026-11-20T09:00:00Z")
        val plan = ReminderTime(ReminderKind.DUE, due, due)
        assertFalse(plan.isDeliverableAt(due.minusSeconds(1)))
        assertTrue(plan.isDeliverableAt(due))
        assertTrue(plan.isDeliverableAt(due.plusSeconds(90)))
        assertFalse(plan.isDeliverableAt(due.plusSeconds(86_400)))
    }

    @Test fun advanceReminderCannotBeDeliveredAfterDueTime() {
        val plans = ReminderTimes.forPayment(payment, ZoneId.of("Europe/Istanbul"))
        assertEquals(4, plans.size)
        plans.filter { it.kind != ReminderKind.DUE }.forEach {
            assertTrue(it.isDeliverableAt(it.triggerAt))
            assertFalse(it.isDeliverableAt(it.dueAt))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun editingRejectsBlankName() {
        payment.edited("  ", "100", rule, payment.nextDueDate, LocalTime.NOON)
    }

    @Test(expected = IllegalArgumentException::class)
    fun editingRejectsZeroAmount() {
        payment.edited("Fatura", "0", rule, payment.nextDueDate, LocalTime.NOON)
    }
}

package com.example.odedim

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderTimesTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val payment = Payment(
        1, "İnternet", "450", LocalDate.of(2026, 12, 5),
        paymentTime = LocalTime.of(18, 30)
    )

    @Test
    fun createsAllFourRemindersAtSelectedTime() {
        val reminders = ReminderTimes.forPayment(payment, zone)
        assertEquals(4, reminders.size)
        assertEquals(Instant.parse("2026-12-04T15:30:00Z"), reminders[0].triggerAt)
        assertEquals(Instant.parse("2026-12-05T13:30:00Z"), reminders[1].triggerAt)
        assertEquals(Instant.parse("2026-12-05T15:00:00Z"), reminders[2].triggerAt)
        assertEquals(Instant.parse("2026-12-05T15:30:00Z"), reminders[3].triggerAt)
        assertTrue(reminders.all { it.dueAt == Instant.parse("2026-12-05T15:30:00Z") })
    }

    @Test
    fun pastRemindersAreNotScheduledImmediatelyOnAppStart() {
        val remaining = ReminderTimes.upcoming(payment, Instant.parse("2026-12-05T14:00:00Z"), zone)
        assertEquals(listOf(ReminderKind.THIRTY_MINUTES, ReminderKind.DUE), remaining.map { it.kind })
        assertTrue(ReminderTimes.upcoming(payment, Instant.parse("2026-12-06T00:00:00Z"), zone).isEmpty())
    }

    @Test
    fun reminderExactlyAtNowIsNotScheduledAgain() {
        val remaining = ReminderTimes.upcoming(payment, Instant.parse("2026-12-05T13:30:00Z"), zone)
        assertEquals(listOf(ReminderKind.THIRTY_MINUTES, ReminderKind.DUE), remaining.map { it.kind })
    }

    @Test
    fun midnightTimeCrossesPreviousDayMonthAndYear() {
        val january = payment.copy(nextDueDate = LocalDate.of(2027, 1, 1), paymentTime = LocalTime.MIDNIGHT)
        val reminders = ReminderTimes.forPayment(january, zone)
        assertEquals(LocalDate.of(2026, 12, 31), reminders[1].triggerAt.atZone(zone).toLocalDate())
        assertEquals(LocalTime.of(22, 0), reminders[1].triggerAt.atZone(zone).toLocalTime())
        assertEquals(LocalTime.of(23, 30), reminders[2].triggerAt.atZone(zone).toLocalTime())
    }

    @Test
    fun missingLegacyTimeDoesNotCreateArbitraryAlarms() {
        assertTrue(ReminderTimes.forPayment(payment.copy(paymentTime = null), zone).isEmpty())
    }

    @Test
    fun dayBeforeIsPreviousLocalCalendarDayAcrossDaylightSaving() {
        val berlin = ZoneId.of("Europe/Berlin")
        val march = payment.copy(nextDueDate = LocalDate.of(2026, 3, 29), paymentTime = LocalTime.of(10, 0))
        val reminders = ReminderTimes.forPayment(march, berlin)
        assertEquals(LocalDate.of(2026, 3, 28), reminders[0].triggerAt.atZone(berlin).toLocalDate())
        assertEquals(LocalTime.of(10, 0), reminders[0].triggerAt.atZone(berlin).toLocalTime())
        assertEquals(Duration.ofHours(23), Duration.between(reminders[0].triggerAt, reminders[0].dueAt))
        assertEquals(Duration.ofHours(2), Duration.between(reminders[1].triggerAt, reminders[1].dueAt))
        assertEquals(Duration.ofMinutes(30), Duration.between(reminders[2].triggerAt, reminders[2].dueAt))
    }

    @Test
    fun daylightSavingGapStillKeepsElapsedHourOffsetsExact() {
        val berlin = ZoneId.of("Europe/Berlin")
        val march = payment.copy(nextDueDate = LocalDate.of(2026, 3, 29), paymentTime = LocalTime.of(2, 30))
        val reminders = ReminderTimes.forPayment(march, berlin)
        assertEquals(Duration.ofHours(2), Duration.between(reminders[1].triggerAt, reminders[1].dueAt))
        assertEquals(Duration.ofMinutes(30), Duration.between(reminders[2].triggerAt, reminders[2].dueAt))
    }

    @Test
    fun registeringYesterdayPaymentRecordsYesterdayAndSchedulesThirtyDaysLater() {
        val yesterday = LocalDate.of(2026, 11, 8)
        val created = createPayment(
            1, "İnternet", "450", RepeatRule(), yesterday, LocalTime.of(18, 30),
            LocalDate.of(2026, 11, 9), 5
        )
        assertEquals(LocalDate.of(2026, 12, 8), created.nextDueDate)
        assertEquals(yesterday, created.history.single().paidOn)
        assertEquals(LocalTime.of(18, 30), created.paymentTime)
    }

    @Test
    fun selectingOlderDateKeepsCorrectLeapYearCalendarMath() {
        val created = createPayment(
            1, "İnternet", "450", RepeatRule(), LocalDate.of(2024, 1, 30),
            LocalTime.MIDNIGHT, LocalDate.of(2024, 2, 1), 5
        )
        assertEquals(LocalDate.of(2024, 2, 29), created.nextDueDate)
        assertEquals(LocalTime.MIDNIGHT, created.paymentTime)
    }

    @Test
    fun fixedRuleCreationDoesNotInventACompletedPayment() {
        val created = createPayment(
            1, "Kira", "100", RepeatRule(RepeatType.MONTHLY_DAY, 31), null,
            LocalTime.of(12, 0), LocalDate.of(2025, 2, 1), 5
        )
        assertEquals(LocalDate.of(2025, 2, 28), created.nextDueDate)
        assertTrue(created.history.isEmpty())
    }

    @Test
    fun paidAndUndoKeepSelectedTimeAndRecalculateReminderDates() {
        val paid = payment.markPaid(LocalDate.of(2026, 12, 4), 5)
        assertEquals(payment.paymentTime, paid.paymentTime)
        assertNotEquals(
            ReminderTimes.forPayment(payment, zone).first().dueAt,
            ReminderTimes.forPayment(paid, zone).first().dueAt
        )
        assertEquals(payment, paid.undoLastPayment(5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureLastPaymentDateIsRejected() {
        createPayment(
            1, "Kira", "100", RepeatRule(), LocalDate.of(2026, 11, 10),
            LocalTime.of(12, 0), LocalDate.of(2026, 11, 9), 5
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingLastPaymentDateIsRejectedForThirtyDayRule() {
        createPayment(1, "Kira", "100", RepeatRule(), null, LocalTime.NOON, LocalDate.of(2026, 11, 9), 5)
    }
}

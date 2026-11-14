package com.example.odedim

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderKind { DAY_BEFORE, TWO_HOURS, THIRTY_MINUTES, DUE }

data class ReminderTime(val kind: ReminderKind, val triggerAt: Instant, val dueAt: Instant) {
    fun isDeliverableAt(now: Instant): Boolean {
        if (now < triggerAt) return false
        return if (kind == ReminderKind.DUE) now < dueAt.plusSeconds(86_400)
            else now < dueAt
    }
}

object ReminderTimes {
    fun forPayment(payment: Payment, zone: ZoneId): List<ReminderTime> {
        val time = payment.paymentTime ?: return emptyList()
        val due = payment.nextDueDate.atTime(time).atZone(zone)
        return listOf(
            ReminderTime(ReminderKind.DAY_BEFORE, due.minusDays(1).toInstant(), due.toInstant()),
            ReminderTime(ReminderKind.TWO_HOURS, due.minusHours(2).toInstant(), due.toInstant()),
            ReminderTime(ReminderKind.THIRTY_MINUTES, due.minusMinutes(30).toInstant(), due.toInstant()),
            ReminderTime(ReminderKind.DUE, due.toInstant(), due.toInstant())
        )
    }

    fun upcoming(payment: Payment, now: Instant, zone: ZoneId): List<ReminderTime> =
        forPayment(payment, zone).filter { it.triggerAt > now }
}

fun createPayment(
    id: Long,
    name: String,
    amount: String,
    rule: RepeatRule,
    lastPaidOn: LocalDate?,
    time: LocalTime,
    today: LocalDate,
    salaryDay: Int,
    currency: PaymentCurrency = PaymentCurrency.TRY,
    firstDueDate: LocalDate? = null
): Payment {
    require(salaryDay in 1..31)
    val initialDate = if (rule.type == RepeatType.AFTER_30_DAYS) {
        // Choose either an actual prior payment or an unpaid first due date.
        require((lastPaidOn == null) != (firstDueDate == null))
        lastPaidOn?.also { require(it <= today) } ?: requireNotNull(firstDueDate)
    } else {
        require(firstDueDate == null)
        PaymentSchedule.firstDueDate(rule, today, salaryDay)
    }
    val payment = Payment(
        id, name, amount, initialDate, rule,
        paymentTime = time.withSecond(0).withNano(0),
        currency = currency
    )
    return if (rule.type == RepeatType.AFTER_30_DAYS && lastPaidOn != null) {
        payment.markPaid(requireNotNull(lastPaidOn), salaryDay)
    } else {
        payment
    }
}

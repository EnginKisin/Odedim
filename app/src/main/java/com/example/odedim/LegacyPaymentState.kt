package com.example.odedim

import android.os.Bundle
import androidx.compose.runtime.saveable.listSaver
import java.time.LocalDate

// One-time bridge from the earlier, non-persistent prototype's saved instance state.
internal val PaymentListSaver = listSaver<List<Payment>, Any>(
    save = { payments ->
        payments.map { payment ->
            Bundle().apply {
                putLong("id", payment.id)
                putString("name", payment.name)
                putString("amount", payment.amount)
                putString("currency", payment.currency.name)
                putString("repeatType", payment.repeatRule.type.name)
                putInt("monthlyDay", payment.repeatRule.monthlyDay)
                putLong("nextDueDate", payment.nextDueDate.toEpochDay())
                putLongArray("paidOn", payment.history.map { it.paidOn.toEpochDay() }.toLongArray())
                putLongArray("dueDates", payment.history.map { it.dueDate.toEpochDay() }.toLongArray())
            }
        }
    },
    restore = { saved ->
        if (saved.firstOrNull() is Long) {
            saved.chunked(4).map {
                Payment(it[0] as Long, it[1] as String, it[2] as String, LocalDate.now())
            }
        } else {
            saved.map { value ->
                val state = value as Bundle
                val paidDates = state.getLongArray("paidOn") ?: longArrayOf()
                val dueDates = state.getLongArray("dueDates") ?: longArrayOf()
                Payment(
                    state.getLong("id"),
                    requireNotNull(state.getString("name")),
                    requireNotNull(state.getString("amount")),
                    LocalDate.ofEpochDay(state.getLong("nextDueDate")),
                    RepeatRule(
                        RepeatType.valueOf(requireNotNull(state.getString("repeatType"))),
                        state.getInt("monthlyDay")
                    ),
                    paidDates.zip(dueDates).map { (paid, due) ->
                        PaymentRecord(LocalDate.ofEpochDay(paid), LocalDate.ofEpochDay(due))
                    },
                    currency = state.getString("currency")?.let(PaymentCurrency::valueOf) ?: PaymentCurrency.TRY
                )
            }
        }
    }
)

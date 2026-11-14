package com.example.odedim

import java.math.BigDecimal
import java.text.Collator
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

enum class DueFilter { ALL, OVERDUE, TODAY, NEXT_7_DAYS, NEXT_30_DAYS, CUSTOM }
enum class PaymentSort { ADDED, DUE_ASCENDING, DUE_DESCENDING, AMOUNT_ASCENDING, AMOUNT_DESCENDING, NAME }

data class PaymentListFilter(
    val currency: PaymentCurrency? = null,
    val minimum: BigDecimal? = null,
    val maximum: BigDecimal? = null,
    val due: DueFilter = DueFilter.ALL,
    val from: LocalDate? = null,
    val until: LocalDate? = null,
    val sort: PaymentSort = PaymentSort.ADDED
) {
    init {
        require(minimum == null || minimum.signum() >= 0)
        require(maximum == null || maximum.signum() >= 0)
        require(minimum == null || maximum == null || minimum <= maximum)
        require((minimum == null && maximum == null) || currency != null)
        require(due == DueFilter.CUSTOM || (from == null && until == null))
        require(from == null || until == null || from <= until)
        require(due != DueFilter.CUSTOM || from != null || until != null)
        require(currency != null || sort !in amountSorts)
    }

    val activeCount: Int get() = (if (currency != null) 1 else 0) +
        (if (minimum != null || maximum != null) 1 else 0) +
        (if (due != DueFilter.ALL) 1 else 0) +
        (if (sort != PaymentSort.ADDED) 1 else 0)

    fun apply(payments: List<Payment>, query: String, today: LocalDate): List<Payment> {
        val search = searchable(query.trim())
        val matches = payments.filter { payment ->
            val amount = payment.amount.toBigDecimal()
            (search.isEmpty() || searchable(payment.name).contains(search)) &&
                (currency == null || payment.currency == currency) &&
                (minimum == null || amount >= minimum) &&
                (maximum == null || amount <= maximum) &&
                when (due) {
                    DueFilter.ALL -> true
                    DueFilter.OVERDUE -> payment.nextDueDate < today
                    DueFilter.TODAY -> payment.nextDueDate == today
                    DueFilter.NEXT_7_DAYS -> payment.nextDueDate in today..today.plusDays(6)
                    DueFilter.NEXT_30_DAYS -> payment.nextDueDate in today..today.plusDays(29)
                    DueFilter.CUSTOM -> (from == null || payment.nextDueDate >= from) &&
                        (until == null || payment.nextDueDate <= until)
                }
        }
        return when (sort) {
            PaymentSort.ADDED -> matches
            PaymentSort.DUE_ASCENDING -> matches.sortedBy { it.nextDueDate }
            PaymentSort.DUE_DESCENDING -> matches.sortedByDescending { it.nextDueDate }
            PaymentSort.AMOUNT_ASCENDING -> matches.sortedBy { it.amount.toBigDecimal() }
            PaymentSort.AMOUNT_DESCENDING -> matches.sortedByDescending { it.amount.toBigDecimal() }
            PaymentSort.NAME -> {
                val collator = Collator.getInstance(turkish)
                matches.sortedWith { a, b -> collator.compare(a.name, b.name) }
            }
        }
    }

    companion object {
        val amountSorts = setOf(PaymentSort.AMOUNT_ASCENDING, PaymentSort.AMOUNT_DESCENDING)
        private val turkish = Locale.forLanguageTag("tr-TR")
        private val marks = Regex("\\p{M}+")
        private fun searchable(text: String): String =
            Normalizer.normalize(text.lowercase(turkish), Normalizer.Form.NFD)
                .replace(marks, "").replace('ı', 'i')

        /** A filter boundary may be zero; payment amounts themselves must remain positive. */
        fun amountBoundary(input: String, currency: PaymentCurrency): BigDecimal? =
            TurkishMoney.normalizeInput(input, currency)?.takeIf { it.isNotEmpty() }
                ?.trimEnd(',')?.replace(',', '.')?.toBigDecimalOrNull()
    }
}

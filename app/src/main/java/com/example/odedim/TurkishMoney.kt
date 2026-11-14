package com.example.odedim

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Turkish editing/display text is separate from the exact decimal string stored in Room. */
object TurkishMoney {
    private val ungrouped = Regex("[0-9]*")
    private val grouped = Regex("[0-9]{1,3}(\\.[0-9]{3})+")
    private val decimal = Regex("[0-9]{0,2}")

    fun normalizeInput(input: String, currency: PaymentCurrency = PaymentCurrency.TRY): String? {
        if (input.length > 64) return null
        var text = input.trim()
        if (text.startsWith(currency.symbol)) text = text.drop(currency.symbol.length).trim()
        val suffix = listOf(currency.label, currency.name).firstOrNull {
            text.endsWith(it, ignoreCase = true)
        }
        if (suffix != null) text = text.dropLast(suffix.length).trim()
        if (text.isEmpty()) return ""

        val commaCount = text.count { it == ',' }
        if (commaCount > 1) return null
        if (commaCount == 0 && '.' in text) {
            // Accept Turkish grouped pastes, or a decimal dot from a numeric keyboard.
            text = if (grouped.matches(text)) text.replace(".", "")
                else if (text.count { it == '.' } == 1) text.replace('.', ',')
                else return null
        }
        val whole = text.substringBefore(',')
        val fraction = text.substringAfter(',', "")
        if (!ungrouped.matches(whole) && !grouped.matches(whole)) return null
        if (!decimal.matches(fraction)) return null
        val digits = whole.replace(".", "").trimStart('0').ifEmpty { "0" }
        if (digits.length > 9) return null
        return digits + if (',' in text) "," + fraction else ""
    }

    fun storedAmount(input: String, currency: PaymentCurrency = PaymentCurrency.TRY): String? {
        val normalized = normalizeInput(input, currency)?.takeIf { it.isNotEmpty() } ?: return null
        val value = normalized.trimEnd(',').replace(',', '.').toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) return null
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString()
    }

    fun editableAmount(stored: String): String =
        BigDecimal(stored).setScale(2, RoundingMode.UNNECESSARY).toPlainString().replace('.', ',')

    fun groupedInput(input: String): String {
        if (input.isEmpty()) return ""
        val whole = input.substringBefore(',')
        val groupedWhole = whole.reversed().chunked(3).joinToString(".").reversed()
        return groupedWhole + if (',' in input) "," + input.substringAfter(',') else ""
    }

    fun formatNumber(stored: String): String = DecimalFormat(
        "#,##0.00", DecimalFormatSymbols(Locale.forLanguageTag("tr-TR"))
    ).apply {
        roundingMode = RoundingMode.UNNECESSARY
    }.format(BigDecimal(stored))

    fun formatCurrency(stored: String, currency: PaymentCurrency = PaymentCurrency.TRY): String =
        formatNumber(stored) + " " + currency.label
}

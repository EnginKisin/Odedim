package com.example.odedim

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test

class TurkishMoneyTest {
    @Test fun turkishInputAndPastedCurrencyKeepExactValue() {
        mapOf(
            "1234,50" to "1234.50",
            "1.234,50" to "1234.50",
            "1.234,50 TL" to "1234.50",
            "₺ 1.234,50" to "1234.50",
            "1.234" to "1234.00",
            "1234.50" to "1234.50",
            "850" to "850.00",
            "850,5" to "850.50",
            ",05" to "0.05",
            "00012,5" to "12.50",
            "999.999.999,99" to "999999999.99"
        ).forEach { (input, expected) ->
            assertEquals(input, expected, TurkishMoney.storedAmount(input))
        }
    }

    @Test fun invalidNegativeOverPreciseOrAmbiguousAmountsAreRejected() {
        listOf(
            "-1", "+1", "1e3", "abc", "1,,50", "12,345", "1.234,567",
            "12.34,50", "1,234.50", "1..234", "1000000000", "NaN"
        ).forEach { input -> assertNull(input, TurkishMoney.normalizeInput(input)) }
    }

    @Test fun emptyAndZeroCannotBeSaved() {
        listOf("", "0", "0,00", "000", ",", ".").forEach { input ->
            assertNull(input, TurkishMoney.storedAmount(input))
        }
        assertEquals("", TurkishMoney.normalizeInput(""))
    }

    @Test fun unfinishedDecimalCanBeEditedAndSavedAsWholeLira() {
        assertEquals("1234,", TurkishMoney.normalizeInput("1234."))
        assertEquals("1234.00", TurkishMoney.storedAmount("1234,"))
        assertEquals("0,", TurkishMoney.normalizeInput(","))
        assertEquals("0,5", TurkishMoney.normalizeInput(",5"))
    }

    @Test fun savedAmountsAlwaysDisplayTwoDecimalPlacesAndTurkishGrouping() {
        assertEquals("1.234,50", TurkishMoney.formatNumber("1234.5"))
        assertEquals("1.234,50 TL", TurkishMoney.formatCurrency("1234.50"))
        assertEquals("850,00 TL", TurkishMoney.formatCurrency("850"))
        assertEquals("0,01 TL", TurkishMoney.formatCurrency("0.01"))
        assertEquals("999.999.999,99 TL", TurkishMoney.formatCurrency("999999999.99"))
    }

    @Test fun editingExistingRecordsPreservesValueWithoutFloatingPointRounding() {
        listOf("1234.50", "850", "0.01", "999999999.99").forEach { stored ->
            val editable = TurkishMoney.editableAmount(stored)
            val roundTrip = TurkishMoney.storedAmount(editable)
            assertEquals(stored.toBigDecimal().setScale(2).toPlainString(), roundTrip)
        }
    }

    @Test fun groupingDoesNotAddFractionDigitsWhileTyping() {
        assertEquals("1.234", TurkishMoney.groupedInput("1234"))
        assertEquals("1.234,", TurkishMoney.groupedInput("1234,"))
        assertEquals("1.234,5", TurkishMoney.groupedInput("1234,5"))
        assertEquals("1.234,50", TurkishMoney.groupedInput("1234,50"))
    }

    @Test fun cursorPositionsStayValidAcrossInsertedGroupingSeparators() {
        listOf("", "1", "1234", "1234567,50", "0,", "0,01").forEach { input ->
            val visual = TurkishMoneyVisualTransformation.filter(AnnotatedString(input))
            assertEquals(TurkishMoney.groupedInput(input), visual.text.text)
            var previous = -1
            for (offset in 0..input.length) {
                val mapped = visual.offsetMapping.originalToTransformed(offset)
                assertTrue(mapped in 0..visual.text.length)
                assertTrue(mapped >= previous)
                assertEquals(offset, visual.offsetMapping.transformedToOriginal(mapped))
                previous = mapped
            }
            previous = -1
            for (offset in 0..visual.text.length) {
                val mapped = visual.offsetMapping.transformedToOriginal(offset)
                assertTrue(mapped in 0..input.length)
                assertTrue(mapped >= previous)
                previous = mapped
            }
        }
    }

    @Test fun tappingEitherSideOfAGroupingSeparatorMapsToTheSameEditingGap() {
        val visual = TurkishMoneyVisualTransformation.filter(AnnotatedString("1234"))
        assertEquals("1.234", visual.text.text)
        assertEquals(1, visual.offsetMapping.transformedToOriginal(1))
        assertEquals(1, visual.offsetMapping.transformedToOriginal(2))
        assertEquals(2, visual.offsetMapping.originalToTransformed(1))
    }
}

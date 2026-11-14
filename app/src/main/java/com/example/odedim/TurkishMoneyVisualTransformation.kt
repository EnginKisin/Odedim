package com.example.odedim

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Inserts grouping separators without storing them or moving the logical editing position. */
object TurkishMoneyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val displayed = TurkishMoney.groupedInput(text.text)
        val originalOffsets = IntArray(text.length + 1)
        val displayedOffsets = IntArray(displayed.length + 1)
        var originalIndex = 0
        displayed.forEachIndexed { displayIndex, character ->
            if (character != '.') {
                originalOffsets[originalIndex] = displayIndex
                originalIndex++
            }
            displayedOffsets[displayIndex + 1] = originalIndex
        }
        originalOffsets[text.length] = displayed.length
        return TransformedText(AnnotatedString(displayed), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalOffsets[offset.coerceIn(0, originalOffsets.lastIndex)]

            override fun transformedToOriginal(offset: Int): Int =
                displayedOffsets[offset.coerceIn(0, displayedOffsets.lastIndex)]
        })
    }
}

package com.almi.ai.ui.v12

import org.junit.Assert.assertEquals
import org.junit.Test

class V12BodyNumberInputTest {
    @Test
    fun normalizesArabicIndicDigitsAndDecimalSeparator() {
        assertEquals("170.5", v12NormalizeDecimalInput("١٧٠٫٥"))
    }

    @Test
    fun normalizesPersianDigitsAndComma() {
        assertEquals("82.4", v12NormalizeDecimalInput("۸۲,۴"))
    }

    @Test
    fun keepsOnlyOneDecimalSeparator() {
        assertEquals("12.34", v12NormalizeDecimalInput("12,3.4"))
    }

    @Test
    fun ignoresNonNumericCharactersAndRespectsMaxLength() {
        assertEquals("123.45", v12NormalizeDecimalInput("kg ١٢٣٫٤٥٦٧", maxLength = 6))
    }

    @Test
    fun leadingSeparatorGetsZeroPrefix() {
        assertEquals("0.5", v12NormalizeDecimalInput("٫٥"))
    }

    @Test
    fun preservesTrailingSeparatorWhileUserIsStillTyping() {
        assertEquals("12.", v12NormalizeDecimalInput("١٢٫"))
    }
}

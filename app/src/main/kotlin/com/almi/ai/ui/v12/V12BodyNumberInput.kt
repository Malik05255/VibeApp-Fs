package com.almi.ai.ui.v12

/**
 * Normalizes decimal input from English, Arabic-Indic, and Persian keyboards into the ASCII form
 * expected by Float.toFloatOrNull(). Arabic decimal separators and commas are accepted as '.'.
 */
internal fun v12NormalizeDecimalInput(raw: String, maxLength: Int = 6): String {
    if (maxLength <= 0) return ""
    return buildString {
        var hasDecimal = false
        raw.forEach { ch ->
            if (length >= maxLength) return@forEach
            when (ch) {
                in '0'..'9' -> append(ch)
                in '٠'..'٩' -> append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> append(('0'.code + (ch.code - '۰'.code)).toChar())
                '.', ',', '٫' -> if (!hasDecimal) {
                    if (isEmpty()) append('0')
                    if (length < maxLength) append('.')
                    hasDecimal = true
                }
            }
        }
    }
}

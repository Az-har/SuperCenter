package com.eipna.centsation.sms.parser

object MerchantCleaner {

    private val INVALID_WORDS = setOf(
        "USING", "VIA", "THROUGH", "BY", "WITH", "FOR", "TO", "FROM", "AT", "THE",
        "BLOCK", "BLOCK UPI", "UPIBLOCK", "BLOCK ACT", "BLOCK CARD", "BLOCK A/C",
        "BLOCK ACCOUNT", "VPA", "UPI", "ACCOUNT", "A/C", "CARD", "DEBIT", "CREDIT"
    )

    fun clean(raw: String): String {
        var result = raw.trim()

        // Strip known cleaning regexes
        result = SmsCompiledPatterns.Cleaning.TRAILING_PARENTHESES.replace(result, "")
        result = SmsCompiledPatterns.Cleaning.REF_NUMBER_SUFFIX.replace(result, "")
        result = SmsCompiledPatterns.Cleaning.UPI_SUFFIX.replace(result, "")
        result = SmsCompiledPatterns.Cleaning.DATE_SUFFIX.replace(result, "")
        result = SmsCompiledPatterns.Cleaning.TIME_SUFFIX.replace(result, "")
        result = SmsCompiledPatterns.Cleaning.PVT_LTD.replace(result, "")
        result = SmsCompiledPatterns.Cleaning.LTD.replace(result, "")
        result = SmsCompiledPatterns.Cleaning.TRAILING_DASH.replace(result, "")
        result = result.replace(Regex("""\s+"""), " ").trim()

        // Strip leading prefixes like "UPI/" or "VPA/"
        if (result.startsWith("UPI/", ignoreCase = true)) {
            result = result.substring(4).trim()
        }
        if (result.startsWith("VPA/", ignoreCase = true)) {
            result = result.substring(4).trim()
        }

        return result
    }

    fun isValid(name: String): Boolean {
        val upper = name.uppercase().trim()
        if (upper in INVALID_WORDS) return false
        if (upper.startsWith("BLOCK") || upper.startsWith("TO BLOCK") || upper.startsWith("UPIBLOCK")) return false
        return name.length >= 2 && name.any { it.isLetter() } && !name.all { it.isDigit() }
    }
}

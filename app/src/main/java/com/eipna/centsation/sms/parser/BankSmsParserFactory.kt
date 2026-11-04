package com.eipna.centsation.sms.parser

object BankSmsParserFactory {

    private val specializedParsers: List<BankSmsParser> = listOf(
        ICICIBankParser(),
        CityUnionBankParser(),
        YesBankParser()
    )

    private val fallbackParser = GenericIndianBankParser()

    fun parse(sender: String, body: String, timestamp: Long = System.currentTimeMillis()): ParsedBankTransaction? {
        for (parser in specializedParsers) {
            if (parser.canHandle(sender, body)) {
                val parsed = parser.parse(sender, body, timestamp)
                if (parsed != null) return parsed
            }
        }
        return fallbackParser.parse(sender, body, timestamp)
    }
}

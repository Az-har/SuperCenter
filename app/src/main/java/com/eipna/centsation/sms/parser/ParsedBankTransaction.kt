package com.eipna.centsation.sms.parser

data class ParsedBankTransaction(
    val amount: Double,
    val isExpense: Boolean,
    val rawMerchant: String,
    val resolvedMerchant: String = rawMerchant,
    val category: String = "General",
    val bankName: String,
    val accountLast4: String? = null,
    val balanceAfter: Double? = null,
    val reference: String? = null,
    val sender: String,
    val smsBody: String,
    val timestamp: Long = System.currentTimeMillis()
)

interface BankSmsParser {
    val bankName: String
    fun canHandle(sender: String, body: String): Boolean
    fun parse(sender: String, body: String, timestamp: Long): ParsedBankTransaction?
}

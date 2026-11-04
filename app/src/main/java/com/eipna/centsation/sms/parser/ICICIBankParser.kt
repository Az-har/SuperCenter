package com.eipna.centsation.sms.parser

class ICICIBankParser : BankSmsParser {

    override val bankName = "ICICI Bank"

    override fun canHandle(sender: String, body: String): Boolean {
        val upperSender = sender.uppercase()
        val upperBody = body.uppercase()
        return upperSender.contains("ICICI") ||
                upperSender.contains("ICICIB") ||
                upperBody.contains("ICICI BANK")
    }

    override fun parse(sender: String, body: String, timestamp: Long): ParsedBankTransaction? {
        if (!isTransactionMessage(body)) return null

        val amount = extractAmount(body) ?: return null
        val isExpense = isDebit(body)
        val accountLast4 = extractAccountLast4(body)
        val balance = extractBalance(body)
        val reference = extractReference(body)
        val rawMerchant = extractMerchant(body)
        val cleanedMerchant = MerchantCleaner.clean(rawMerchant)

        return ParsedBankTransaction(
            amount = amount,
            isExpense = isExpense,
            rawMerchant = cleanedMerchant,
            bankName = bankName,
            accountLast4 = accountLast4,
            balanceAfter = balance,
            reference = reference,
            sender = sender,
            smsBody = body,
            timestamp = timestamp
        )
    }

    private fun isTransactionMessage(body: String): Boolean {
        // 1. Fast reject OTPs & Spam
        for (p in SmsCompiledPatterns.FastReject.OTP_PATTERNS) {
            if (p.containsMatchIn(body)) return false
        }
        for (p in SmsCompiledPatterns.FastReject.SPAM_OR_REMINDER) {
            if (p.containsMatchIn(body)) return false
        }
        if (SmsCompiledPatterns.FastReject.CC_BILL_PAYMENT_CONFIRMATION.containsMatchIn(body)) {
            return false // Credit card bill payment confirmation (transfers between own accounts)
        }

        val lower = body.lowercase()
        return lower.contains("debited") || lower.contains("credited") ||
                lower.contains("spent") || lower.contains("withdrawn") ||
                lower.contains("payment of") || lower.contains("transferred") ||
                lower.contains("refund") || lower.contains("reversed")
    }

    private fun isDebit(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.contains("is credited") || lower.contains("credited with") ||
            lower.contains("deposited") || lower.contains("refund") ||
            lower.contains("reversed")
        ) {
            return false
        }
        return true
    }

    private fun extractAmount(body: String): Double? {
        return SmsCompiledPatterns.Amount.extractTransactionAmount(body)
    }

    private fun extractAccountLast4(body: String): String? {
        for (p in SmsCompiledPatterns.Account.ALL) {
            val m = p.find(body)
            if (m != null) {
                val digits = m.groupValues[1].filter { it.isDigit() }
                if (digits.length >= 3) return digits.takeLast(4)
            }
        }
        return null
    }

    private fun extractBalance(body: String): Double? {
        val m = SmsCompiledPatterns.Balance.AVL_BAL.find(body)
        if (m != null) {
            return m.groupValues[1].replace(",", "").toDoubleOrNull()
        }
        return null
    }

    private fun extractReference(body: String): String? {
        for (p in SmsCompiledPatterns.Reference.ALL) {
            val m = p.find(body)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun extractMerchant(body: String): String {
        // Pattern 1: Info: UPI/Ref/Merchant
        val infoM = SmsCompiledPatterns.Merchant.UPI_INFO.find(body)
        if (infoM != null) {
            val cand = infoM.groupValues[1].trim()
            if (cand.isNotBlank()) return cand
        }

        // Pattern 2: to / towards / at
        val toM = SmsCompiledPatterns.Merchant.TO_PATTERN.find(body)
        if (toM != null) {
            val cand = toM.groupValues[1].trim()
            if (MerchantCleaner.isValid(cand)) return cand
        }

        return "ICICI Transaction"
    }
}

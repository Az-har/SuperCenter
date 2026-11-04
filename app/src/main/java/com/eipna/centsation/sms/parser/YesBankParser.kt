package com.eipna.centsation.sms.parser

class YesBankParser : BankSmsParser {

    override val bankName = "Yes Bank"

    override fun canHandle(sender: String, body: String): Boolean {
        val upperSender = sender.uppercase()
        val upperBody = body.uppercase()
        return upperSender.contains("YESB") ||
                upperSender.contains("YESBANK") ||
                upperBody.contains("YES BANK") ||
                body.contains("ptyes", ignoreCase = true)
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
        // Fast reject OTPs & Spam
        for (p in SmsCompiledPatterns.FastReject.OTP_PATTERNS) {
            if (p.containsMatchIn(body)) return false
        }
        for (p in SmsCompiledPatterns.FastReject.SPAM_OR_REMINDER) {
            if (p.containsMatchIn(body)) return false
        }
        if (SmsCompiledPatterns.FastReject.CC_BILL_PAYMENT_CONFIRMATION.containsMatchIn(body)) {
            return false
        }

        val lower = body.lowercase()
        return lower.contains("debited") || lower.contains("credited") ||
                lower.contains("spent") || lower.contains("sent") ||
                lower.contains("paid") || lower.contains("received")
    }

    private fun isDebit(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.contains("credited") || lower.contains("received") || lower.contains("deposited")) return false
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
        // Look for ptyes handles e.g. 9344002076ptyes, 9677236663ptyes
        val vpaMatch = Regex("""([A-Za-z0-9._-]+ptyes)""", RegexOption.IGNORE_CASE).find(body)
        if (vpaMatch != null) {
            return vpaMatch.groupValues[1].trim()
        }

        // UPI info
        val upiInfoM = SmsCompiledPatterns.Merchant.UPI_INFO.find(body)
        if (upiInfoM != null) {
            val cand = upiInfoM.groupValues[1].trim()
            if (cand.isNotBlank()) return cand
        }

        // to / towards / at
        val toM = SmsCompiledPatterns.Merchant.TO_PATTERN.find(body)
        if (toM != null) {
            val cand = toM.groupValues[1].trim()
            if (MerchantCleaner.isValid(cand)) return cand
        }

        return "Yes Bank Transaction"
    }
}

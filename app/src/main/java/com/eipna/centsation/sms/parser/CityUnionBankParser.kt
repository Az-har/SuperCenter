package com.eipna.centsation.sms.parser

class CityUnionBankParser : BankSmsParser {

    override val bankName = "City Union Bank"

    override fun canHandle(sender: String, body: String): Boolean {
        val upperSender = sender.uppercase()
        val upperBody = body.uppercase()
        return upperSender.contains("CUBANK") ||
                upperSender.contains("CUBLTD") ||
                upperSender.contains("CUB") ||
                upperBody.contains("CITY UNION BANK")
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
        return lower.contains("is debited") || lower.contains("is credited") ||
                lower.contains("credited with") || lower.contains("neft trf") ||
                lower.contains("debited for") || lower.contains("credited for") ||
                lower.contains("debited") || lower.contains("credited")
    }

    private fun isDebit(body: String): Boolean {
        val lower = body.lowercase()
        return when {
            lower.contains("your a/c") && lower.contains("is credited") -> false
            lower.contains("your a/c") && lower.contains("is debited") -> true
            lower.contains("is debited") || lower.contains("debited for") -> true
            lower.contains("is credited") || lower.contains("credited for") || lower.contains("credited with") -> false
            lower.contains("neft trf") -> false
            else -> true
        }
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
        // NEFT Transfer pattern
        val neftM = SmsCompiledPatterns.Merchant.NEFT_TRF.find(body)
        if (neftM != null) {
            val cand = neftM.groupValues[1].trim()
            if (cand.isNotBlank()) return "NEFT - $cand"
        }

        // towards
        val towardsM = SmsCompiledPatterns.Merchant.TO_PATTERN.find(body)
        if (towardsM != null) {
            val cand = towardsM.groupValues[1].trim()
            if (MerchantCleaner.isValid(cand)) return cand
        }

        val upiInfoM = SmsCompiledPatterns.Merchant.UPI_INFO.find(body)
        if (upiInfoM != null) {
            val cand = upiInfoM.groupValues[1].trim()
            if (cand.isNotBlank()) return cand
        }

        return "City Union Bank Transaction"
    }
}

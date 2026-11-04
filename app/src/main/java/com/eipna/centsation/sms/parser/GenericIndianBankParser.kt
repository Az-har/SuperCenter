package com.eipna.centsation.sms.parser

class GenericIndianBankParser : BankSmsParser {

    override val bankName = "Bank"

    override fun canHandle(sender: String, body: String): Boolean {
        return isTransactionMessage(body)
    }

    override fun parse(sender: String, body: String, timestamp: Long): ParsedBankTransaction? {
        if (!isTransactionMessage(body)) return null

        val amount = extractAmount(body) ?: return null
        val isExpense = isDebit(body)
        val detectedBank = detectBankName(sender, body)
        val accountLast4 = extractAccountLast4(body)
        val balance = extractBalance(body)
        val reference = extractReference(body)
        val rawMerchant = extractMerchant(body)
        val cleanedMerchant = MerchantCleaner.clean(rawMerchant)

        return ParsedBankTransaction(
            amount = amount,
            isExpense = isExpense,
            rawMerchant = cleanedMerchant,
            bankName = detectedBank,
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
                lower.contains("paid") || lower.contains("received") ||
                lower.contains("withdrawn") || lower.contains("transferred") ||
                lower.contains("refund") || lower.contains("reversed")
    }

    private fun isDebit(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.contains("is credited") || lower.contains("credited with") ||
            lower.contains("credited for") || lower.contains("has received") ||
            lower.contains("deposited") || lower.contains("refund") ||
            lower.contains("reversed")
        ) {
            return false
        }
        return true
    }

    private fun detectBankName(sender: String, body: String): String {
        val upperSender = sender.uppercase()
        val upperBody = body.uppercase()

        return when {
            upperSender.contains("ICICI") || upperBody.contains("ICICI BANK") -> "ICICI Bank"
            upperSender.contains("CUB") || upperBody.contains("CITY UNION BANK") -> "City Union Bank"
            upperSender.contains("YESB") || upperBody.contains("YES BANK") || upperBody.contains("PTYES") -> "Yes Bank"
            upperSender.contains("CASHFR") || upperBody.contains("CASHFREE") -> "Cashfree"
            upperSender.contains("HDFC") || upperBody.contains("HDFC BANK") -> "HDFC Bank"
            upperSender.contains("SBI") || upperBody.contains("STATE BANK") -> "SBI"
            upperSender.contains("AXIS") || upperBody.contains("AXIS BANK") -> "Axis Bank"
            upperSender.contains("KOTAK") || upperBody.contains("KOTAK") -> "Kotak Bank"
            upperSender.contains("PNB") || upperBody.contains("PUNJAB NATIONAL") -> "PNB"
            upperSender.contains("CANARA") || upperBody.contains("CANARA") -> "Canara Bank"
            upperSender.contains("INDUSIND") || upperBody.contains("INDUSIND") -> "IndusInd Bank"
            upperSender.contains("PAYTM") || upperBody.contains("PAYTM") -> "Paytm"
            upperSender.contains("GPAY") || upperBody.contains("GOOGLE PAY") -> "Google Pay"
            upperSender.contains("PHONEPE") || upperBody.contains("PHONEPE") -> "PhonePe"
            else -> "Bank"
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
        // Notification "Paid ₹150 to XYZ"
        val paidToM = SmsCompiledPatterns.Merchant.PAID_TO_NOTIFICATION.find(body)
        if (paidToM != null) {
            val cand = paidToM.groupValues[1].trim()
            if (MerchantCleaner.isValid(cand)) return cand
        }

        // Info: UPI/...
        val infoM = SmsCompiledPatterns.Merchant.UPI_INFO.find(body)
        if (infoM != null) {
            val cand = infoM.groupValues[1].trim()
            if (cand.isNotBlank()) return cand
        }

        // NEFT Transfer
        val neftM = SmsCompiledPatterns.Merchant.NEFT_TRF.find(body)
        if (neftM != null) {
            val cand = neftM.groupValues[1].trim()
            if (cand.isNotBlank()) return "NEFT - $cand"
        }

        // to / towards / at
        val toM = SmsCompiledPatterns.Merchant.TO_PATTERN.find(body)
        if (toM != null) {
            val cand = toM.groupValues[1].trim()
            if (MerchantCleaner.isValid(cand)) return cand
        }

        return "Bank Transaction"
    }
}

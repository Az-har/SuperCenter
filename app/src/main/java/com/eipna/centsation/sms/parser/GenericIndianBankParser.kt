package com.eipna.centsation.sms.parser

import java.util.regex.Pattern

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
        val merchant = extractMerchant(body)

        return ParsedBankTransaction(
            amount = amount,
            isExpense = isExpense,
            rawMerchant = merchant,
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
        val lower = body.lowercase()
        if (lower.contains("otp") || lower.contains("verification code") || lower.contains("code is") ||
            lower.contains("is due by") || lower.contains("will be debited")
        ) {
            return false
        }
        return lower.contains("debited") || lower.contains("credited") ||
                lower.contains("spent") || lower.contains("sent") ||
                lower.contains("paid") || lower.contains("received") ||
                lower.contains("withdrawn") || lower.contains("transferred")
    }

    private fun isDebit(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.contains("is credited") || lower.contains("credited with") ||
            lower.contains("credited for") || lower.contains("has received") ||
            lower.contains("deposited")
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
            upperSender.contains("PAYTM") || upperBody.contains("PAYTM") -> "Paytm"
            upperSender.contains("GPAY") || upperBody.contains("GOOGLE PAY") -> "Google Pay"
            upperSender.contains("PHONEPE") || upperBody.contains("PHONEPE") -> "PhonePe"
            else -> "Bank"
        }
    }

    private fun extractAmount(body: String): Double? {
        val patterns = listOf(
            Pattern.compile("""(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:debited|spent|paid|credited)\s+(?:for|with|by)?\s*(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(body)
            if (m.find()) {
                val raw = m.group(1)?.replace(",", "") ?: continue
                val value = raw.toDoubleOrNull()
                if (value != null && value > 0.0) return value
            }
        }
        return null
    }

    private fun extractAccountLast4(body: String): String? {
        val p = Pattern.compile("""(?:A/c|Account|Card|Acct)\s*(?:no\.?)?\s*[X*]*(\d{3,4})""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(body)
        if (m.find()) return m.group(1)
        return null
    }

    private fun extractBalance(body: String): Double? {
        val p = Pattern.compile("""(?:Avl\s+Bal|Available\s+Balance|Bal)\s*(?:is)?\s*(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(body)
        if (m.find()) {
            return m.group(1)?.replace(",", "")?.toDoubleOrNull()
        }
        return null
    }

    private fun extractReference(body: String): String? {
        val patterns = listOf(
            Pattern.compile("""(?:UPI|Ref|RRN)[:/\s]+([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""Ref\s+no\.?\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(body)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractMerchant(body: String): String {
        val patterns = listOf(
            Pattern.compile("""(?:to|towards|at)\s+([A-Za-z0-9\s._*-]+?)(?:\s+on|\s+ref|\s+avl|\.|\n|$)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""Info:\s*([^.\n]+?)(?:\.|\s+Avl|\n|$)""", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(body)
            if (m.find()) {
                val raw = m.group(1)!!.trim()
                if (raw.length in 2..50 && !raw.startsWith("VPA", ignoreCase = true) && !raw.startsWith("UPI", ignoreCase = true)) {
                    return raw
                }
            }
        }
        return "Bank Transaction"
    }
}

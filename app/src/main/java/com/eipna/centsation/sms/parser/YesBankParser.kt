package com.eipna.centsation.sms.parser

import java.util.regex.Pattern

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
        val merchant = extractMerchant(body)

        return ParsedBankTransaction(
            amount = amount,
            isExpense = isExpense,
            rawMerchant = merchant,
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
        val lower = body.lowercase()
        if (lower.contains("otp") || lower.contains("verification code")) return false
        return lower.contains("debited") || lower.contains("credited") ||
                lower.contains("spent") || lower.contains("sent") ||
                lower.contains("paid") || lower.contains("received")
    }

    private fun isDebit(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.contains("credited") || lower.contains("received")) return false
        return true
    }

    private fun extractAmount(body: String): Double? {
        val patterns = listOf(
            Pattern.compile("""(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""debited\s+(?:for|with|by)\s*(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""credited\s+(?:for|with|by)\s*(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""paid\s+(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
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
        val p = Pattern.compile("""(?:A/c|Account|Card)\s*(?:no\.?)?\s*[X*]*(\d{3,4})""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(body)
        if (m.find()) return m.group(1)
        return null
    }

    private fun extractBalance(body: String): Double? {
        val p = Pattern.compile("""(?:Avl\s+Bal|Bal)\s*(?:is)?\s*(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(body)
        if (m.find()) {
            return m.group(1)?.replace(",", "")?.toDoubleOrNull()
        }
        return null
    }

    private fun extractReference(body: String): String? {
        val patterns = listOf(
            Pattern.compile("""UPI\s*(?:Ref|txn)?\s*(?:no\.?)?\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""Ref\s*(?:no\.?)?\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(body)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractMerchant(body: String): String {
        // Look for ptyes handles e.g. 9344002076ptyes, 9677236663ptyes
        val vpaP = Pattern.compile("""([A-Za-z0-9._-]+ptyes)""", Pattern.CASE_INSENSITIVE)
        val vpaM = vpaP.matcher(body)
        if (vpaM.find()) {
            return vpaM.group(1)!!.trim()
        }

        // to / towards / at
        val toP = Pattern.compile("""(?:to|towards|at)\s+([A-Za-z0-9\s._*-]+?)(?:\s+on|\s+ref|\s+avl|\.|\n|$)""", Pattern.CASE_INSENSITIVE)
        val toM = toP.matcher(body)
        if (toM.find()) {
            val raw = toM.group(1)!!.trim()
            if (raw.length in 2..50 && !raw.startsWith("VPA", ignoreCase = true)) {
                return raw
            }
        }

        return "Yes Bank Transaction"
    }
}

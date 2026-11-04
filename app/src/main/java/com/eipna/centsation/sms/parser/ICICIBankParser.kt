package com.eipna.centsation.sms.parser

import java.util.regex.Pattern

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
        if (lower.contains("otp") || lower.contains("verification code") || lower.contains("login to") ||
            lower.contains("is due by") || lower.contains("will be debited")
        ) {
            return false
        }
        return lower.contains("debited") || lower.contains("credited") ||
                lower.contains("spent") || lower.contains("withdrawn") ||
                lower.contains("payment of") || lower.contains("transferred")
    }

    private fun isDebit(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.contains("is credited") || lower.contains("credited with") || lower.contains("deposited")) {
            return false
        }
        return true
    }

    private fun extractAmount(body: String): Double? {
        val patterns = listOf(
            Pattern.compile("""(?:Rs\.?|INR|USD)\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""debited\s+(?:for|with)\s*(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""credited\s+(?:for|with)\s*(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""spent\s+(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val matcher = p.matcher(body)
            if (matcher.find()) {
                val raw = matcher.group(1)?.replace(",", "") ?: continue
                try {
                    val value = raw.toDouble()
                    if (value > 0.0) return value
                } catch (ignored: Exception) {}
            }
        }
        return null
    }

    private fun extractAccountLast4(body: String): String? {
        val p = Pattern.compile("""(?:Acct|Account|Card|A/c)\s*(?:no\.?)?\s*[X*]*(\d{3,4})""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(body)
        if (m.find()) {
            return m.group(1)
        }
        return null
    }

    private fun extractBalance(body: String): Double? {
        val p = Pattern.compile("""(?:Avl\s+Bal|Available\s+Balance|Bal)\s*(?:is)?\s*(?:Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(body)
        if (m.find()) {
            val raw = m.group(1)?.replace(",", "") ?: return null
            return raw.toDoubleOrNull()
        }
        return null
    }

    private fun extractReference(body: String): String? {
        val patterns = listOf(
            Pattern.compile("""UPI[:/]\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""RRN\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""Ref\s*(?:no\.?)?\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(body)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractMerchant(body: String): String {
        // Pattern 1: Info: UPI/Ref/Merchant
        val infoP = Pattern.compile("""Info:\s*UPI/[^/]+/([^.]+?)(?:\.|\s+Avl|\n|$)""", Pattern.CASE_INSENSITIVE)
        val infoM = infoP.matcher(body)
        if (infoM.find()) {
            return infoM.group(1)!!.trim()
        }

        // Pattern 2: towards / at / to
        val toP = Pattern.compile("""(?:towards|at|to)\s+([A-Za-z0-9\s._*-]+?)(?:\s+on|\s+Avl|\s+Ref|\.|\n|$)""", Pattern.CASE_INSENSITIVE)
        val toM = toP.matcher(body)
        if (toM.find()) {
            val raw = toM.group(1)!!.trim()
            if (raw.length in 2..50 && !raw.equals("VPA", ignoreCase = true) && !raw.startsWith("UPI", ignoreCase = true)) {
                return raw
            }
        }

        return "ICICI Transaction"
    }
}

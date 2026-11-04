package com.eipna.centsation.sms.parser

import java.util.regex.Pattern

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
        if (lower.contains("otp") || lower.contains("verification") || lower.contains("request")) {
            return false
        }
        return lower.contains("is debited") || lower.contains("is credited") ||
                lower.contains("credited with") || lower.contains("neft trf") ||
                lower.contains("debited for") || lower.contains("credited for")
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
        val patterns = listOf(
            Pattern.compile("""debited\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""credited\s+for\s+Rs\.?\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""credited\s+with\s+INR\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR)\s*([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
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
        val patterns = listOf(
            Pattern.compile("""(?:Your\s+a/c\s+no\.?|Savings\s+No)\s*([X*\d]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""a/c\s+(?:no\.?)?\s*[X*]*(\d{3,4})""", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(body)
            if (m.find()) {
                val raw = m.group(1)!!.filter { it.isDigit() }
                if (raw.length >= 3) return raw.takeLast(4)
            }
        }
        return null
    }

    private fun extractBalance(body: String): Double? {
        val p = Pattern.compile("""Avl\s+Bal\s+([0-9,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(body)
        if (m.find()) {
            return m.group(1)?.replace(",", "")?.toDoubleOrNull()
        }
        return null
    }

    private fun extractReference(body: String): String? {
        val upiP = Pattern.compile("""\(UPI\s+Ref\s+no\s+(\d+)\)""", Pattern.CASE_INSENSITIVE)
        val upiM = upiP.matcher(body)
        if (upiM.find()) return upiM.group(1)

        val neftP = Pattern.compile("""NEFT[:/]\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE)
        val neftM = neftP.matcher(body)
        if (neftM.find()) return neftM.group(1)

        return null
    }

    private fun extractMerchant(body: String): String {
        // NEFT Transfer pattern
        val neftP = Pattern.compile("""BY\s+NEFT\s+TRF:([^:]+)""", Pattern.CASE_INSENSITIVE)
        val neftM = neftP.matcher(body)
        if (neftM.find()) {
            return "NEFT - " + neftM.group(1)!!.trim()
        }

        // UPI To account
        val toP = Pattern.compile("""credited\s+to\s+a/c\s+no\.\s+([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE)
        val toM = toP.matcher(body)
        if (toM.find()) {
            val last4 = toM.group(1)!!.takeLast(4)
            return "UPI Transfer to A/C XX$last4"
        }

        // UPI From account
        val fromP = Pattern.compile("""debited\s+from\s+a/c\s+no\.\s+([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE)
        val fromM = fromP.matcher(body)
        if (fromM.find()) {
            val last4 = fromM.group(1)!!.takeLast(4)
            return "UPI Transfer from A/C XX$last4"
        }

        // towards
        val towardsP = Pattern.compile("""towards\s+([A-Za-z0-9\s._*-]+?)(?:\s+on|\s+Avl|\.|\n|$)""", Pattern.CASE_INSENSITIVE)
        val towardsM = towardsP.matcher(body)
        if (towardsM.find()) {
            return towardsM.group(1)!!.trim()
        }

        return "City Union Bank Transaction"
    }
}

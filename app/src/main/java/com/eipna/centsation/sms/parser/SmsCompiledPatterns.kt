package com.eipna.centsation.sms.parser

/**
 * Pre-compiled regex patterns for high-performance SMS and notification parsing.
 * Eliminates repeated regex compilation on incoming messages and batch scans.
 */
object SmsCompiledPatterns {

    object Amount {
        private const val NUM = """(?:[0-9,]+(?:\.\d{1,2})?|\.\d{1,2})"""
        val RS_PATTERN = Regex("""Rs\.?\s*($NUM)""", RegexOption.IGNORE_CASE)
        val INR_PATTERN = Regex("""INR\s*($NUM)""", RegexOption.IGNORE_CASE)
        val RUPEE_SYMBOL = Regex("""₹\s*($NUM)""")
        val USD_PATTERN = Regex("""USD\s*($NUM)""", RegexOption.IGNORE_CASE)
        val EUR_PATTERN = Regex("""EUR\s*($NUM)""", RegexOption.IGNORE_CASE)

        val DEBIT_KEYWORD_AMOUNT = Regex(
            """(?:debited|spent|paid|withdrawn|sent)\s+(?:for|with|by)?\s*(?:Rs\.?|INR|₹)?\s*($NUM)""",
            RegexOption.IGNORE_CASE
        )
        val CREDIT_KEYWORD_AMOUNT = Regex(
            """(?:credited|deposited|received)\s+(?:for|with|by)?\s*(?:Rs\.?|INR|₹)?\s*($NUM)""",
            RegexOption.IGNORE_CASE
        )

        val ALL = listOf(RS_PATTERN, INR_PATTERN, RUPEE_SYMBOL, DEBIT_KEYWORD_AMOUNT, CREDIT_KEYWORD_AMOUNT, USD_PATTERN, EUR_PATTERN)

        fun extractTransactionAmount(body: String): Double? {
            val balMatch = Balance.AVL_BAL.find(body)
            val balStart = balMatch?.range?.first ?: Int.MAX_VALUE

            var bestIndex = Int.MAX_VALUE
            var bestAmount: Double? = null

            for (p in ALL) {
                for (m in p.findAll(body)) {
                    if (m.range.first >= balStart) continue
                    val raw = m.groupValues[1].replace(",", "")
                    val value = raw.toDoubleOrNull()
                    if (value != null && value > 0.0) {
                        if (m.range.first < bestIndex) {
                            bestIndex = m.range.first
                            bestAmount = value
                        }
                    }
                }
            }

            // Fallback if no amount appeared before balance (rare inverted SMS structure)
            if (bestAmount == null) {
                for (p in ALL) {
                    val m = p.find(body)
                    if (m != null) {
                        val raw = m.groupValues[1].replace(",", "")
                        val value = raw.toDoubleOrNull()
                        if (value != null && value > 0.0) return value
                    }
                }
            }

            return bestAmount
        }
    }

    object Reference {
        val UPI_REF = Regex("""(?:UPI\s+Ref\s+no|UPI\s+Ref|UPI\s+txn)[:/\s]+([0-9]{9,16})""", RegexOption.IGNORE_CASE)
        val GENERIC_REF = Regex("""(?:Ref|Reference|Txn|Transaction)\.?(?:\s+No\.?)?[:\s]+([A-Z0-9]*\d[A-Z0-9]*)""", RegexOption.IGNORE_CASE)
        val RRN_PATTERN = Regex("""RRN[:\s]+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        val NEFT_REF = Regex("""NEFT[:/\s]+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        val IMPS_REF = Regex("""IMPS[:/\s]+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)

        val ALL = listOf(UPI_REF, RRN_PATTERN, NEFT_REF, IMPS_REF, GENERIC_REF)
    }

    object Account {
        val ACCT_MASK = Regex("""(?:A/c|Account|Acct|Card)\s*(?:no\.?)?\s*([X*\d]{3,})""", RegexOption.IGNORE_CASE)
        val SAVINGS_NO = Regex("""Savings\s+No\s+([X*\d]+)""", RegexOption.IGNORE_CASE)
        val YOUR_AC = Regex("""Your\s+a/c\s+no\.?\s*([X*\d]+)""", RegexOption.IGNORE_CASE)
        val CARD_LAST4 = Regex("""Card\s+(?:ending\s+with\s+|ending\s+)?([X*\d]{3,4})""", RegexOption.IGNORE_CASE)

        val ALL = listOf(ACCT_MASK, YOUR_AC, SAVINGS_NO, CARD_LAST4)
    }

    object Balance {
        val AVL_BAL = Regex("""(?:Avl\s+Bal|Available\s+Balance|Updated\s+Bal|Bal)\s*(?:is)?\s*(?:Rs\.?|INR|₹)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    }

    object Merchant {
        val UPI_INFO = Regex("""Info:\s*UPI/[^/]+/([^.]+?)(?:\.|\s+Avl|\n|$)""", RegexOption.IGNORE_CASE)
        val NEFT_TRF = Regex("""BY\s+NEFT\s+TRF:([^:]+)""", RegexOption.IGNORE_CASE)
        val TO_PATTERN = Regex("""(?:to|towards|at)\s+([A-Za-z0-9\s._*-]+?)(?:\s+on|\s+ref|\s+upi|\s+avl|\.|\n|$)""", RegexOption.IGNORE_CASE)
        val PAID_TO_NOTIFICATION = Regex("""Paid\s+₹?[0-9,.]+\s+to\s+([^\n.]+?)(?:\s+using|\.|\n|$)""", RegexOption.IGNORE_CASE)
    }

    object Cleaning {
        val TRAILING_PARENTHESES = Regex("""\s*\(.*?\)\s*$""")
        val REF_NUMBER_SUFFIX = Regex("""\s+Ref(?:\s+No)?.*""", RegexOption.IGNORE_CASE)
        val UPI_SUFFIX = Regex("""\s+UPI.*""", RegexOption.IGNORE_CASE)
        val DATE_SUFFIX = Regex("""\s+on\s+\d{1,2}[/-]\w+.*""", RegexOption.IGNORE_CASE)
        val TIME_SUFFIX = Regex("""\s+at\s+\d{1,2}:\d{2}.*""", RegexOption.IGNORE_CASE)
        val TRAILING_DASH = Regex("""\s*-\s*$""")
        val PVT_LTD = Regex("""(\s+PVT\.?\s*LTD\.?|\s+PRIVATE\s+LIMITED)$""", RegexOption.IGNORE_CASE)
        val LTD = Regex("""(\s+LTD\.?|\s+LIMITED)$""", RegexOption.IGNORE_CASE)
        val VPA_SUFFIX = Regex("""@[A-Za-z0-9._-]+$""")
    }

    object FastReject {
        val OTP_PATTERNS = listOf(
            Regex("""\b(?:otp|one\s+time\s+password|verification\s+code)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:is\s+your\s+secret\s+code|do\s+not\s+share\s+this\s+otp)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:login\s+to|login\s+otp|code\s+is\s+\d{4,6})\b""", RegexOption.IGNORE_CASE)
        )

        val SPAM_OR_REMINDER = listOf(
            Regex("""\b(?:pre-approved|apply\s+for|instant\s+loan|congratulations)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:is\s+due\s+by|minimum\s+amount\s+due|payment\s+reminder)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:will\s+be\s+debited\s+on|mandate\s+set\s+for)\b""", RegexOption.IGNORE_CASE)
        )

        val CC_BILL_PAYMENT_CONFIRMATION = Regex(
            """(?:payment\s+of\s+rs.*received\s+on\s+your.*credit\s+card)""",
            RegexOption.IGNORE_CASE
        )
    }
}

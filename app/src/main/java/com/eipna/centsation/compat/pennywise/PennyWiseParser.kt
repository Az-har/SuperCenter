package com.eipna.centsation.compat.pennywise

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PennyWiseParser {

    fun parse(jsonContent: String): PennyWiseImportResult {
        val root = JSONObject(jsonContent)
        val db = root.optJSONObject("database") ?: JSONObject()

        // 1. Parse Accounts & Aliases
        val rawAccounts = db.optJSONArray("accounts") ?: JSONArray()
        val accounts = mutableListOf<PennyWiseAccount>()
        for (i in 0 until rawAccounts.length()) {
            val obj = rawAccounts.optJSONObject(i) ?: continue
            val bankName = obj.optString("bankName", obj.optString("bank_name", "Bank"))
            val last4 = obj.optString("accountLast4", obj.optString("account_last4", "0000"))
            val balance = obj.optDouble("balance", 0.0)
            val accType = obj.optString("accountType", obj.optString("account_type", "SAVINGS"))
            val isCc = obj.optBoolean("isCreditCard", obj.optBoolean("is_credit_card", false))
            val alias = if (obj.has("alias") && !obj.isNull("alias")) obj.optString("alias") else null
            val currency = obj.optString("currency", "INR")

            accounts.add(
                PennyWiseAccount(
                    id = obj.optLong("id", i.toLong() + 1),
                    bankName = bankName,
                    accountLast4 = last4,
                    balance = balance,
                    accountType = accType,
                    isCreditCard = isCc,
                    alias = alias?.takeIf { it.isNotBlank() },
                    currency = currency
                )
            )
        }

        // 2. Parse Merchant Aliases (Raw -> Clean Display Name)
        val rawAliases = db.optJSONArray("merchant_aliases") ?: JSONArray()
        val merchantAliases = mutableListOf<PennyWiseMerchantAlias>()
        val aliasMap = mutableMapOf<String, String>()
        for (i in 0 until rawAliases.length()) {
            val obj = rawAliases.optJSONObject(i) ?: continue
            val raw = obj.optString("merchantName", obj.optString("merchant_name", "")).trim()
            val alias = obj.optString("alias", "").trim()
            if (raw.isNotBlank() && alias.isNotBlank()) {
                merchantAliases.add(PennyWiseMerchantAlias(raw, alias))
                aliasMap[raw.lowercase(Locale.ROOT)] = alias
            }
        }

        // 3. Parse Merchant Mappings (Merchant -> Category)
        val rawMappings = db.optJSONArray("merchant_mappings") ?: JSONArray()
        val merchantMappings = mutableListOf<PennyWiseMerchantMapping>()
        val categoryMap = mutableMapOf<String, String>()
        for (i in 0 until rawMappings.length()) {
            val obj = rawMappings.optJSONObject(i) ?: continue
            val raw = obj.optString("merchantName", obj.optString("merchant_name", "")).trim()
            val cat = obj.optString("category", "").trim()
            if (raw.isNotBlank() && cat.isNotBlank()) {
                merchantMappings.add(PennyWiseMerchantMapping(raw, cat))
                categoryMap[raw.lowercase(Locale.ROOT)] = cat
            }
        }

        // 4. Parse Transactions (SMS + Manual)
        val rawTransactions = db.optJSONArray("transactions") ?: JSONArray()
        val transactions = mutableListOf<PennyWiseTransaction>()
        var totalIncome = 0.0
        var totalExpense = 0.0
        val merchantSpendMap = mutableMapOf<String, Double>()
        val detectedBanks = mutableSetOf<String>()

        for (i in 0 until rawTransactions.length()) {
            val obj = rawTransactions.optJSONObject(i) ?: continue
            val amount = obj.optDouble("amount", 0.0)
            val rawMerchant = obj.optString("merchantName", obj.optString("merchant_name", "Unknown")).trim()
            val rawCategory = obj.optString("category", "General").trim()
            val type = obj.optString("transactionType", obj.optString("transaction_type", "EXPENSE")).uppercase(Locale.ROOT)
            val desc = obj.optString("description", "").takeIf { it.isNotBlank() }
            val smsBody = obj.optString("smsBody", obj.optString("sms_body", "")).takeIf { it.isNotBlank() }
            val bankName = obj.optString("bankName", obj.optString("bank_name", "")).takeIf { it.isNotBlank() }
            val accNum = obj.optString("accountNumber", obj.optString("account_number", "")).takeIf { it.isNotBlank() }
            val balanceAfter = if (obj.has("balanceAfter")) obj.optDouble("balanceAfter") else null
            val currency = obj.optString("currency", "INR")

            val dateStr = obj.optString("dateTime", obj.optString("date_time", ""))
            val dateMillis = parseDateToMillis(dateStr)

            // Resolve clean alias
            val resolvedMerchant = aliasMap[rawMerchant.lowercase(Locale.ROOT)] ?: rawMerchant
            // Resolve category
            val resolvedCategory = categoryMap[resolvedMerchant.lowercase(Locale.ROOT)]
                ?: categoryMap[rawMerchant.lowercase(Locale.ROOT)]
                ?: rawCategory

            if (type == "INCOME") {
                totalIncome += amount
            } else if (type == "EXPENSE" || type == "CREDIT") {
                totalExpense += amount
                merchantSpendMap[resolvedMerchant] = (merchantSpendMap[resolvedMerchant] ?: 0.0) + amount
            }

            if (!bankName.isNullOrBlank()) {
                detectedBanks.add(bankName)
            }

            transactions.add(
                PennyWiseTransaction(
                    id = obj.optLong("id", i.toLong() + 1),
                    amount = amount,
                    rawMerchant = rawMerchant,
                    resolvedMerchant = resolvedMerchant,
                    category = resolvedCategory,
                    transactionType = type,
                    dateMillis = dateMillis,
                    description = desc,
                    smsBody = smsBody,
                    bankName = bankName,
                    accountLast4 = accNum,
                    balanceAfter = balanceAfter,
                    currency = currency
                )
            )
        }

        // 5. Parse Loans & Debts
        val rawLoans = db.optJSONArray("loans") ?: JSONArray()
        val loans = mutableListOf<PennyWiseLoan>()
        for (i in 0 until rawLoans.length()) {
            val obj = rawLoans.optJSONObject(i) ?: continue
            val person = obj.optString("personName", obj.optString("person_name", "Unknown")).trim()
            val initAmt = obj.optDouble("initialAmount", obj.optDouble("initial_amount", 0.0))
            val remAmt = obj.optDouble("remainingAmount", obj.optDouble("remaining_amount", initAmt))
            val loanType = obj.optString("loanType", obj.optString("loan_type", "LENT")).uppercase(Locale.ROOT)
            val isLent = loanType == "LENT"
            val notes = obj.optString("notes", "").takeIf { it.isNotBlank() }
            val dueDateStr = obj.optString("dueDate", obj.optString("due_date", ""))
            val dueDateMillis = if (dueDateStr.isNotBlank()) parseDateToMillis(dueDateStr) else null

            loans.add(
                PennyWiseLoan(
                    id = obj.optLong("id", i.toLong() + 1),
                    personName = person,
                    initialAmount = initAmt,
                    remainingAmount = remAmt,
                    isLent = isLent,
                    notes = notes,
                    dueDateMillis = dueDateMillis
                )
            )
        }

        val topMerchants = merchantSpendMap.toList()
            .sortedByDescending { it.second }
            .take(15)

        val markdownSummary = generateMarkdownSummary(
            accounts = accounts,
            merchantAliases = merchantAliases,
            merchantMappings = merchantMappings,
            transactions = transactions,
            loans = loans,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            topMerchants = topMerchants,
            detectedBanks = detectedBanks.toList()
        )

        val importResult = PennyWiseImportResult(
            accountsImported = accounts.size,
            merchantAliasesImported = merchantAliases.size,
            categoriesImported = merchantMappings.size,
            transactionsImported = transactions.size,
            loansImported = loans.size,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            accounts = accounts,
            topMerchants = topMerchants,
            detectedBanks = detectedBanks.toList(),
            markdownSummary = markdownSummary
        )

        return importResult
    }

    fun parsePackage(jsonContent: String): PennyWiseParsedPackage {
        val root = JSONObject(jsonContent)
        val db = root.optJSONObject("database") ?: JSONObject()

        // 1. Accounts
        val rawAccounts = db.optJSONArray("accounts") ?: org.json.JSONArray()
        val accounts = mutableListOf<PennyWiseAccount>()
        for (i in 0 until rawAccounts.length()) {
            val obj = rawAccounts.optJSONObject(i) ?: continue
            val bankName = obj.optString("bankName", obj.optString("bank_name", "Bank"))
            val last4 = obj.optString("accountLast4", obj.optString("account_last4", "0000"))
            val balance = obj.optDouble("balance", 0.0)
            val accType = obj.optString("accountType", obj.optString("account_type", "SAVINGS"))
            val isCc = obj.optBoolean("isCreditCard", obj.optBoolean("is_credit_card", false))
            val alias = if (obj.has("alias") && !obj.isNull("alias")) obj.optString("alias") else null
            val currency = obj.optString("currency", "INR")

            accounts.add(
                PennyWiseAccount(
                    id = obj.optLong("id", i.toLong() + 1),
                    bankName = bankName,
                    accountLast4 = last4,
                    balance = balance,
                    accountType = accType,
                    isCreditCard = isCc,
                    alias = alias?.takeIf { it.isNotBlank() },
                    currency = currency
                )
            )
        }

        // 2. Merchant Aliases
        val rawAliases = db.optJSONArray("merchant_aliases") ?: org.json.JSONArray()
        val merchantAliases = mutableListOf<PennyWiseMerchantAlias>()
        val aliasMap = mutableMapOf<String, String>()
        for (i in 0 until rawAliases.length()) {
            val obj = rawAliases.optJSONObject(i) ?: continue
            val raw = obj.optString("merchantName", obj.optString("merchant_name", "")).trim()
            val alias = obj.optString("alias", "").trim()
            if (raw.isNotBlank() && alias.isNotBlank()) {
                merchantAliases.add(PennyWiseMerchantAlias(raw, alias))
                aliasMap[raw.lowercase(java.util.Locale.ROOT)] = alias
            }
        }

        // 3. Merchant Mappings
        val rawMappings = db.optJSONArray("merchant_mappings") ?: org.json.JSONArray()
        val merchantMappings = mutableListOf<PennyWiseMerchantMapping>()
        val categoryMap = mutableMapOf<String, String>()
        for (i in 0 until rawMappings.length()) {
            val obj = rawMappings.optJSONObject(i) ?: continue
            val raw = obj.optString("merchantName", obj.optString("merchant_name", "")).trim()
            val cat = obj.optString("category", "").trim()
            if (raw.isNotBlank() && cat.isNotBlank()) {
                merchantMappings.add(PennyWiseMerchantMapping(raw, cat))
                categoryMap[raw.lowercase(java.util.Locale.ROOT)] = cat
            }
        }

        // 4. Transactions
        val rawTransactions = db.optJSONArray("transactions") ?: org.json.JSONArray()
        val transactions = mutableListOf<PennyWiseTransaction>()
        var totalIncome = 0.0
        var totalExpense = 0.0
        val merchantSpendMap = mutableMapOf<String, Double>()
        val detectedBanks = mutableSetOf<String>()

        for (i in 0 until rawTransactions.length()) {
            val obj = rawTransactions.optJSONObject(i) ?: continue
            val amount = obj.optDouble("amount", 0.0)
            val rawMerchant = obj.optString("merchantName", obj.optString("merchant_name", "Unknown")).trim()
            val rawCategory = obj.optString("category", "General").trim()
            val type = obj.optString("transactionType", obj.optString("transaction_type", "EXPENSE")).uppercase(java.util.Locale.ROOT)
            val desc = obj.optString("description", "").takeIf { it.isNotBlank() }
            val smsBody = obj.optString("smsBody", obj.optString("sms_body", "")).takeIf { it.isNotBlank() }
            val bankName = obj.optString("bankName", obj.optString("bank_name", "")).takeIf { it.isNotBlank() }
            val accNum = obj.optString("accountNumber", obj.optString("account_number", "")).takeIf { it.isNotBlank() }
            val balanceAfter = if (obj.has("balanceAfter")) obj.optDouble("balanceAfter") else null
            val currency = obj.optString("currency", "INR")

            val dateStr = obj.optString("dateTime", obj.optString("date_time", ""))
            val dateMillis = parseDateToMillis(dateStr)

            val resolvedMerchant = aliasMap[rawMerchant.lowercase(java.util.Locale.ROOT)] ?: rawMerchant
            val resolvedCategory = categoryMap[resolvedMerchant.lowercase(java.util.Locale.ROOT)]
                ?: categoryMap[rawMerchant.lowercase(java.util.Locale.ROOT)]
                ?: rawCategory

            if (type == "INCOME") {
                totalIncome += amount
            } else if (type == "EXPENSE" || type == "CREDIT") {
                totalExpense += amount
                merchantSpendMap[resolvedMerchant] = (merchantSpendMap[resolvedMerchant] ?: 0.0) + amount
            }

            if (!bankName.isNullOrBlank()) {
                detectedBanks.add(bankName)
            }

            transactions.add(
                PennyWiseTransaction(
                    id = obj.optLong("id", i.toLong() + 1),
                    amount = amount,
                    rawMerchant = rawMerchant,
                    resolvedMerchant = resolvedMerchant,
                    category = resolvedCategory,
                    transactionType = type,
                    dateMillis = dateMillis,
                    description = desc,
                    smsBody = smsBody,
                    bankName = bankName,
                    accountLast4 = accNum,
                    balanceAfter = balanceAfter,
                    currency = currency
                )
            )
        }

        // 5. Loans
        val rawLoans = db.optJSONArray("loans") ?: org.json.JSONArray()
        val loans = mutableListOf<PennyWiseLoan>()
        for (i in 0 until rawLoans.length()) {
            val obj = rawLoans.optJSONObject(i) ?: continue
            val person = obj.optString("personName", obj.optString("person_name", "Unknown")).trim()
            val initAmt = obj.optDouble("initialAmount", obj.optDouble("initial_amount", 0.0))
            val remAmt = obj.optDouble("remainingAmount", obj.optDouble("remaining_amount", initAmt))
            val loanType = obj.optString("loanType", obj.optString("loan_type", "LENT")).uppercase(java.util.Locale.ROOT)
            val isLent = loanType == "LENT"
            val notes = obj.optString("notes", "").takeIf { it.isNotBlank() }
            val dueDateStr = obj.optString("dueDate", obj.optString("due_date", ""))
            val dueDateMillis = if (dueDateStr.isNotBlank()) parseDateToMillis(dueDateStr) else null

            loans.add(
                PennyWiseLoan(
                    id = obj.optLong("id", i.toLong() + 1),
                    personName = person,
                    initialAmount = initAmt,
                    remainingAmount = remAmt,
                    isLent = isLent,
                    notes = notes,
                    dueDateMillis = dueDateMillis
                )
            )
        }

        val topMerchants = merchantSpendMap.toList()
            .sortedByDescending { it.second }
            .take(15)

        val markdownSummary = generateMarkdownSummary(
            accounts = accounts,
            merchantAliases = merchantAliases,
            merchantMappings = merchantMappings,
            transactions = transactions,
            loans = loans,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            topMerchants = topMerchants,
            detectedBanks = detectedBanks.toList()
        )

        val summaryResult = PennyWiseImportResult(
            accountsImported = accounts.size,
            merchantAliasesImported = merchantAliases.size,
            categoriesImported = merchantMappings.size,
            transactionsImported = transactions.size,
            loansImported = loans.size,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            accounts = accounts,
            topMerchants = topMerchants,
            detectedBanks = detectedBanks.toList(),
            markdownSummary = markdownSummary
        )

        return PennyWiseParsedPackage(
            result = summaryResult,
            accounts = accounts,
            merchantAliases = merchantAliases,
            merchantMappings = merchantMappings,
            transactions = transactions,
            loans = loans
        )
    }

    private fun parseDateToMillis(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val ldt = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                dateStr.toLongOrNull() ?: System.currentTimeMillis()
            } catch (ex: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun generateMarkdownSummary(
        accounts: List<PennyWiseAccount>,
        merchantAliases: List<PennyWiseMerchantAlias>,
        merchantMappings: List<PennyWiseMerchantMapping>,
        transactions: List<PennyWiseTransaction>,
        loans: List<PennyWiseLoan>,
        totalIncome: Double,
        totalExpense: Double,
        topMerchants: List<Pair<String, Double>>,
        detectedBanks: List<String>
    ): String {
        return buildString {
            append("# 👤 My Personal Financial Profile (PennyWise -> Super Center)\n\n")

            append("## 🏦 1. Accounts & Custom Display Aliases\n")
            if (accounts.isEmpty()) {
                append("_No accounts found in backup._\n\n")
            } else {
                append("| Bank Name | Last 4 | Type | Credit Card | Custom Display Alias |\n")
                append("|---|---|---|---|---|\n")
                accounts.forEach { acc ->
                    val isCc = if (acc.isCreditCard) "Yes" else "No"
                    val alias = acc.alias ?: "${acc.bankName} ••${acc.accountLast4}"
                    append("| **${acc.bankName}** | `${acc.accountLast4}` | ${acc.accountType ?: "SAVINGS"} | $isCc | **$alias** |\n")
                }
                append("\n")
            }

            append("## 🏷️ 2. Merchant Aliases & Clean Display Names (${merchantAliases.size})\n")
            if (merchantAliases.isEmpty()) {
                append("_No custom merchant aliases recorded._\n\n")
            } else {
                append("| Raw Bank SMS Text | Custom Clean Display Name |\n")
                append("|---|---|\n")
                merchantAliases.take(30).forEach { ma ->
                    append("| `${ma.merchantName}` | **${ma.alias}** |\n")
                }
                if (merchantAliases.size > 30) {
                    append("| ... and ${merchantAliases.size - 30} more aliases | |\n")
                }
                append("\n")
            }

            append("## 📂 3. Category Mappings (${merchantMappings.size})\n")
            if (merchantMappings.isEmpty()) {
                append("_No custom category mappings recorded._\n\n")
            } else {
                append("| Merchant | Category |\n")
                append("|---|---|\n")
                merchantMappings.take(20).forEach { mm ->
                    append("| ${mm.merchantName} | `${mm.category}` |\n")
                }
                if (merchantMappings.size > 20) {
                    append("| ... and ${merchantMappings.size - 20} more mappings | |\n")
                }
                append("\n")
            }

            append("## 💳 4. Top Spending Merchants\n")
            if (topMerchants.isEmpty()) {
                append("_No transactions available._\n\n")
            } else {
                append("| Merchant | Total Spend |\n")
                append("|---|---|\n")
                topMerchants.forEach { (merchant, spend) ->
                    append("| **$merchant** | ₹%,.2f |\n".format(spend))
                }
                append("\n")
            }

            append("## 🤝 5. Active Debts & Loans (${loans.size})\n")
            if (loans.isEmpty()) {
                append("_No loan or debt records found in backup._\n\n")
            } else {
                append("| Counterparty | Initial | Remaining | Direction | Notes |\n")
                append("|---|---|---|---|---|\n")
                loans.forEach { l ->
                    val direction = if (l.isLent) "Owed to Me (Receivable)" else "I Owe (Liability)"
                    append("| **${l.personName}** | ₹%,.2f | ₹%,.2f | $direction | ${l.notes ?: "-"} |\n".format(l.initialAmount, l.remainingAmount))
                }
                append("\n")
            }

            append("## 📊 6. Summary Statistics\n")
            append("- **Total Transactions**: ${transactions.size}\n")
            append("- **Total Income**: ₹%,.2f\n".format(totalIncome))
            append("- **Total Expenses**: ₹%,.2f\n".format(totalExpense))
            append("- **Detected Banks**: ${detectedBanks.joinToString(", ").ifEmpty { "None" }}\n\n")

            append("---\n")
            append("> 💡 **How to use**: Feed or email this summary to Antigravity so Super Center's SMS parsing, accounts, and debt modules are hyper-tailored to your exact financial life!\n")
        }
    }
}

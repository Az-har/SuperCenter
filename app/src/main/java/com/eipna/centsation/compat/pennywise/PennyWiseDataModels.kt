package com.eipna.centsation.compat.pennywise

data class PennyWiseAccount(
    val id: Long = 0,
    val bankName: String,
    val accountLast4: String,
    val balance: Double = 0.0,
    val accountType: String? = "SAVINGS",
    val isCreditCard: Boolean = false,
    val alias: String? = null,
    val currency: String = "INR"
) {
    val displayLabel: String
        get() = alias?.takeIf { it.isNotBlank() } ?: "$bankName ••$accountLast4"
}

data class PennyWiseMerchantAlias(
    val merchantName: String,
    val alias: String
)

data class PennyWiseMerchantMapping(
    val merchantName: String,
    val category: String
)

data class PennyWiseTransaction(
    val id: Long = 0,
    val amount: Double,
    val rawMerchant: String,
    val resolvedMerchant: String,
    val category: String,
    val transactionType: String, // EXPENSE, INCOME, CREDIT, TRANSFER, INVESTMENT
    val dateMillis: Long,
    val description: String? = null,
    val smsBody: String? = null,
    val bankName: String? = null,
    val accountLast4: String? = null,
    val balanceAfter: Double? = null,
    val currency: String = "INR"
)

data class PennyWiseLoan(
    val id: Long = 0,
    val personName: String,
    val initialAmount: Double,
    val remainingAmount: Double,
    val isLent: Boolean, // true = Owed to me (Lent), false = I owe (Borrowed)
    val notes: String? = null,
    val dueDateMillis: Long? = null
)

data class PennyWiseImportResult(
    val accountsImported: Int,
    val merchantAliasesImported: Int,
    val categoriesImported: Int,
    val transactionsImported: Int,
    val loansImported: Int,
    val totalIncome: Double,
    val totalExpense: Double,
    val accounts: List<PennyWiseAccount>,
    val topMerchants: List<Pair<String, Double>>,
    val detectedBanks: List<String>,
    val markdownSummary: String
)

data class PennyWiseParsedPackage(
    val result: PennyWiseImportResult,
    val accounts: List<PennyWiseAccount>,
    val merchantAliases: List<PennyWiseMerchantAlias>,
    val merchantMappings: List<PennyWiseMerchantMapping>,
    val transactions: List<PennyWiseTransaction>,
    val loans: List<PennyWiseLoan>
)

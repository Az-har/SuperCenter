package com.eipna.centsation.compat.pennywise

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.eipna.centsation.data.Database
import com.eipna.centsation.data.debt.Debt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object PennyWiseDatabaseImporter {

    suspend fun importPackage(
        context: Context,
        pkg: PennyWiseParsedPackage,
        importAccounts: Boolean = true,
        importTransactions: Boolean = true,
        importLoans: Boolean = true,
        saveAliases: Boolean = true
    ): ImportStats = withContext(Dispatchers.IO) {
        val registry = PennyWiseRegistry.getInstance(context)
        val dbHelper = Database.getInstance(context)
        val db: SQLiteDatabase = dbHelper.writableDatabase

        var accountsSaved = 0
        var transactionsSaved = 0
        var loansSaved = 0

        // 1. Save Aliases, Display Names & Categories to Registry
        if (saveAliases) {
            registry.saveAccountAliases(pkg.accounts)
            registry.saveMerchantAliases(pkg.merchantAliases)
            registry.saveMerchantCategories(pkg.merchantMappings)
        }

        db.beginTransaction()
        try {
            val accountBucketMap = mutableMapOf<String, String>()

            // 2. Import Accounts as Buckets in TABLE_SAVING
            if (importAccounts) {
                for (acc in pkg.accounts) {
                    val bucketId = "pw_acc_${acc.bankName}_${acc.accountLast4}"
                    accountBucketMap["${acc.bankName.uppercase()}_${acc.accountLast4}"] = bucketId
                    accountBucketMap[acc.bankName.uppercase()] = bucketId

                    val cursor = db.query(
                        Database.TABLE_SAVING,
                        arrayOf(Database.COLUMN_SAVING_ID),
                        "${Database.COLUMN_SAVING_ID} = ?",
                        arrayOf(bucketId),
                        null, null, null
                    )
                    val exists = cursor.use { it.moveToNext() }

                    val values = ContentValues().apply {
                        put(Database.COLUMN_SAVING_NAME, acc.displayLabel)
                        put(Database.COLUMN_SAVING_CURRENT_SAVING, acc.balance)
                        put(Database.COLUMN_SAVING_GOAL, if (acc.isCreditCard) 0.0 else acc.balance.coerceAtLeast(10000.0))
                        put(Database.COLUMN_SAVING_NOTES, "Imported from PennyWise (${acc.accountType ?: "SAVINGS"})")
                        put(Database.COLUMN_SAVING_CATEGORY, if (acc.isCreditCard) "Credit Card" else "Bank Account")
                        put(Database.COLUMN_SAVING_IS_ARCHIVED, 0)
                        put(Database.COLUMN_SAVING_DEADLINE, 0L)
                    }

                    if (exists) {
                        db.update(Database.TABLE_SAVING, values, "${Database.COLUMN_SAVING_ID} = ?", arrayOf(bucketId))
                    } else {
                        values.put(Database.COLUMN_SAVING_ID, bucketId)
                        db.insert(Database.TABLE_SAVING, null, values)
                    }
                    accountsSaved++
                }
            }

            // Default fallback bucket for transactions without an account
            val defaultBucketId = "pw_general_account"
            val defaultCursor = db.query(
                Database.TABLE_SAVING,
                arrayOf(Database.COLUMN_SAVING_ID),
                "${Database.COLUMN_SAVING_ID} = ?",
                arrayOf(defaultBucketId),
                null, null, null
            )
            val defaultExists = defaultCursor.use { it.moveToNext() }
            if (!defaultExists) {
                val defValues = ContentValues().apply {
                    put(Database.COLUMN_SAVING_ID, defaultBucketId)
                    put(Database.COLUMN_SAVING_NAME, "Main Transactions (PennyWise)")
                    put(Database.COLUMN_SAVING_CURRENT_SAVING, 0.0)
                    put(Database.COLUMN_SAVING_GOAL, 0.0)
                    put(Database.COLUMN_SAVING_NOTES, "Primary bucket for imported PennyWise transactions")
                    put(Database.COLUMN_SAVING_CATEGORY, "General")
                    put(Database.COLUMN_SAVING_IS_ARCHIVED, 0)
                    put(Database.COLUMN_SAVING_DEADLINE, 0L)
                }
                db.insert(Database.TABLE_SAVING, null, defValues)
            }

            // 3. Import Loans & Debts into Super Center TABLE_DEBT
            if (importLoans) {
                for (loan in pkg.loans) {
                    val debtId = "pw_loan_${loan.id}_${loan.personName.replace("\\s+".toRegex(), "_")}"
                    val debtType = if (loan.isLent) Debt.TYPE_OWED_TO_ME else Debt.TYPE_I_OWE
                    val amountPaid = (loan.initialAmount - loan.remainingAmount).coerceAtLeast(0.0)

                    val cursor = db.query(
                        Database.TABLE_DEBT,
                        arrayOf(Database.COLUMN_DEBT_ID),
                        "${Database.COLUMN_DEBT_ID} = ?",
                        arrayOf(debtId),
                        null, null, null
                    )
                    val exists = cursor.use { it.moveToNext() }

                    val values = ContentValues().apply {
                        put(Database.COLUMN_DEBT_NAME, loan.personName)
                        put(Database.COLUMN_DEBT_TOTAL_AMOUNT, loan.initialAmount)
                        put(Database.COLUMN_DEBT_AMOUNT_PAID, amountPaid)
                        put(Database.COLUMN_DEBT_NOTES, loan.notes ?: "Imported from PennyWise")
                        put(Database.COLUMN_DEBT_CATEGORY, "PennyWise Import")
                        put(Database.COLUMN_DEBT_IS_ARCHIVED, if (loan.remainingAmount <= 0.0) 1 else 0)
                        put(Database.COLUMN_DEBT_DEADLINE, loan.dueDateMillis ?: 0L)
                        put(Database.COLUMN_DEBT_CREATED_AT, System.currentTimeMillis())
                        put(Database.COLUMN_DEBT_TYPE, debtType)
                    }

                    if (exists) {
                        db.update(Database.TABLE_DEBT, values, "${Database.COLUMN_DEBT_ID} = ?", arrayOf(debtId))
                    } else {
                        values.put(Database.COLUMN_DEBT_ID, debtId)
                        db.insert(Database.TABLE_DEBT, null, values)
                    }
                    loansSaved++
                }
            }

            // 4. Import Transactions into TABLE_TRANSACTION
            if (importTransactions) {
                for (tx in pkg.transactions) {
                    val targetBucketId = (tx.bankName?.let { b ->
                        tx.accountLast4?.let { l -> accountBucketMap["${b.uppercase()}_$l"] }
                            ?: accountBucketMap[b.uppercase()]
                    }) ?: defaultBucketId

                    val typeString = if (tx.transactionType.equals("INCOME", ignoreCase = true)) "Deposit" else "Withdraw"
                    val values = ContentValues().apply {
                        put(Database.COLUMN_TRANSACTION_SAVING_ID, targetBucketId)
                        put(Database.COLUMN_TRANSACTION_AMOUNT, tx.amount)
                        put(Database.COLUMN_TRANSACTION_TYPE, typeString)
                        put(Database.COLUMN_TRANSACTION_DATE, tx.dateMillis)
                    }
                    db.insert(Database.TABLE_TRANSACTION, null, values)
                    transactionsSaved++
                }
            }

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }

        ImportStats(
            accountsSaved = accountsSaved,
            transactionsSaved = transactionsSaved,
            loansSaved = loansSaved
        )
    }

    data class ImportStats(
        val accountsSaved: Int,
        val transactionsSaved: Int,
        val loansSaved: Int
    )
}

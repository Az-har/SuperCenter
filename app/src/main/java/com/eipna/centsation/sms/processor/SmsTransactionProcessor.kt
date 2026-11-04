package com.eipna.centsation.sms.processor

import android.content.ContentValues
import android.content.Context
import com.eipna.centsation.compat.pennywise.PennyWiseRegistry
import com.eipna.centsation.data.Database
import com.eipna.centsation.sms.notification.SmsNotificationHelper
import com.eipna.centsation.sms.parser.BankSmsParserFactory
import com.eipna.centsation.sms.parser.ParsedBankTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsTransactionProcessor {

    data class ProcessedResult(
        val transactionId: Long,
        val savingId: String,
        val transaction: ParsedBankTransaction
    )

    suspend fun processSms(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        showNotification: Boolean = true
    ): ProcessedResult? = withContext(Dispatchers.IO) {
        val registry = PennyWiseRegistry.getInstance(context)
        if (!registry.isSmsAutoTrackingEnabled()) {
            return@withContext null
        }

        val parsed = BankSmsParserFactory.parse(sender, body, timestamp) ?: return@withContext null

        // Resolve clean display alias and category
        val resolvedMerchant = registry.resolveMerchantAlias(parsed.rawMerchant)
        val resolvedCategory = registry.resolveMerchantCategory(resolvedMerchant)

        val finalTx = parsed.copy(
            resolvedMerchant = resolvedMerchant,
            category = resolvedCategory
        )

        val dbHelper = Database.getInstance(context)
        val db = dbHelper.writableDatabase

        // Deduplication check
        if (TransactionDeduplication.isDuplicate(db, finalTx)) {
            return@withContext null
        }

        var insertedTxId = -1L
        var targetBucketId = "pw_general_account"

        db.beginTransaction()
        try {
            // Find or create bucket
            val candidateBucketId = if (!finalTx.accountLast4.isNullOrBlank()) {
                "pw_acc_${finalTx.bankName.replace("\\s+".toRegex(), "_")}_${finalTx.accountLast4}"
            } else {
                "pw_acc_${finalTx.bankName.replace("\\s+".toRegex(), "_")}"
            }

            val cursor = db.query(
                Database.TABLE_SAVING,
                arrayOf(Database.COLUMN_SAVING_ID, Database.COLUMN_SAVING_CURRENT_SAVING),
                "${Database.COLUMN_SAVING_ID} = ?",
                arrayOf(candidateBucketId),
                null, null, null
            )

            var bucketExists = false
            var currentSaving = 0.0

            cursor.use {
                if (it.moveToFirst()) {
                    bucketExists = true
                    targetBucketId = candidateBucketId
                    currentSaving = it.getDouble(1)
                }
            }

            if (!bucketExists) {
                // Check if general fallback bucket exists
                val generalCursor = db.query(
                    Database.TABLE_SAVING,
                    arrayOf(Database.COLUMN_SAVING_ID, Database.COLUMN_SAVING_CURRENT_SAVING),
                    "${Database.COLUMN_SAVING_ID} = ?",
                    arrayOf(targetBucketId),
                    null, null, null
                )
                val generalExists = generalCursor.use { it.moveToFirst() }

                if (generalExists) {
                    currentSaving = generalCursor.use {
                        if (it.moveToFirst()) it.getDouble(1) else 0.0
                    }
                } else {
                    // Create default general bucket
                    val defValues = ContentValues().apply {
                        put(Database.COLUMN_SAVING_ID, targetBucketId)
                        put(Database.COLUMN_SAVING_NAME, "${finalTx.bankName} Account")
                        put(Database.COLUMN_SAVING_CURRENT_SAVING, 0.0)
                        put(Database.COLUMN_SAVING_GOAL, 0.0)
                        put(Database.COLUMN_SAVING_NOTES, "Auto-created account for bank SMS")
                        put(Database.COLUMN_SAVING_CATEGORY, "Bank Account")
                        put(Database.COLUMN_SAVING_IS_ARCHIVED, 0)
                        put(Database.COLUMN_SAVING_DEADLINE, 0L)
                    }
                    db.insert(Database.TABLE_SAVING, null, defValues)
                }
            }

            // 1. Insert Transaction into TABLE_TRANSACTION
            val txValues = ContentValues().apply {
                put(Database.COLUMN_TRANSACTION_SAVING_ID, targetBucketId)
                put(Database.COLUMN_TRANSACTION_AMOUNT, finalTx.amount)
                put(Database.COLUMN_TRANSACTION_TYPE, if (finalTx.isExpense) "Withdraw" else "Deposit")
                put(Database.COLUMN_TRANSACTION_DATE, finalTx.timestamp)
            }
            insertedTxId = db.insert(Database.TABLE_TRANSACTION, null, txValues)

            // 2. Update balance on target bucket
            val updatedSaving = if (finalTx.isExpense) {
                currentSaving - finalTx.amount
            } else {
                currentSaving + finalTx.amount
            }
            val balanceValues = ContentValues().apply {
                put(Database.COLUMN_SAVING_CURRENT_SAVING, updatedSaving)
            }
            db.update(
                Database.TABLE_SAVING,
                balanceValues,
                "${Database.COLUMN_SAVING_ID} = ?",
                arrayOf(targetBucketId)
            )

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            db.endTransaction()
        }

        if (insertedTxId > 0) {
            // Record in recent auto-logged list
            registry.recordAutoLoggedSms(
                amount = finalTx.amount,
                isExpense = finalTx.isExpense,
                merchant = finalTx.resolvedMerchant,
                category = finalTx.category,
                bankName = finalTx.bankName,
                last4 = finalTx.accountLast4,
                timestamp = finalTx.timestamp
            )

            // Trigger notification
            if (showNotification) {
                SmsNotificationHelper.showTransactionNotification(
                    context = context,
                    transactionId = insertedTxId,
                    savingId = targetBucketId,
                    tx = finalTx
                )
            }

            // Update financial home screen widgets
            com.eipna.centsation.ui.widget.updateAllFinancialWidgets(context)

            ProcessedResult(
                transactionId = insertedTxId,
                savingId = targetBucketId,
                transaction = finalTx
            )
        } else {
            null
        }
    }
}

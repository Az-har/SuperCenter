package com.eipna.centsation.sms.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.eipna.centsation.data.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UNDO_TRANSACTION) {
            val txId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
            val savingId = intent.getStringExtra(EXTRA_SAVING_ID)
            val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
            val isExpense = intent.getBooleanExtra(EXTRA_IS_EXPENSE, true)

            if (txId == -1L || savingId.isNullOrBlank()) return

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dbHelper = Database.getInstance(context)
                    val db = dbHelper.writableDatabase

                    db.beginTransaction()
                    try {
                        // 1. Delete transaction
                        db.delete(
                            Database.TABLE_TRANSACTION,
                            "${Database.COLUMN_TRANSACTION_ID} = ?",
                            arrayOf(txId.toString())
                        )

                        // 2. Reverse balance update on saving bucket
                        val cursor = db.query(
                            Database.TABLE_SAVING,
                            arrayOf(Database.COLUMN_SAVING_CURRENT_SAVING),
                            "${Database.COLUMN_SAVING_ID} = ?",
                            arrayOf(savingId),
                            null, null, null
                        )
                        var currentBalance = 0.0
                        cursor.use {
                            if (it.moveToFirst()) {
                                currentBalance = it.getDouble(0)
                            }
                        }

                        // If it was an expense (withdrawal), adding back the amount reverses it.
                        // If it was income (deposit), subtracting reverses it.
                        val updatedBalance = if (isExpense) currentBalance + amount else currentBalance - amount
                        val values = ContentValues().apply {
                            put(Database.COLUMN_SAVING_CURRENT_SAVING, updatedBalance)
                        }
                        db.update(
                            Database.TABLE_SAVING,
                            values,
                            "${Database.COLUMN_SAVING_ID} = ?",
                            arrayOf(savingId)
                        )

                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }

                    // Cancel notification
                    val notificationId = (txId % 100000).toInt() + 20000
                    NotificationManagerCompat.from(context).cancel(notificationId)

                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Transaction undone", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_UNDO_TRANSACTION = "com.eipna.centsation.sms.ACTION_UNDO_TRANSACTION"
        const val EXTRA_TRANSACTION_ID = "extra_tx_id"
        const val EXTRA_SAVING_ID = "extra_saving_id"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_IS_EXPENSE = "extra_is_expense"
    }
}

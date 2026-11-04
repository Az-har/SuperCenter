package com.eipna.centsation.sms.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eipna.centsation.R
import com.eipna.centsation.sms.parser.ParsedBankTransaction
import com.eipna.centsation.sms.receiver.SmsActionReceiver
import com.eipna.centsation.ui.activities.MainActivity
import com.eipna.centsation.util.FormatUtil

object SmsNotificationHelper {

    const val CHANNEL_ID = "supercenter_bank_sms"
    private const val CHANNEL_NAME = "Bank Transactions"
    private const val CHANNEL_DESC = "Real-time bank SMS transaction notifications"

    fun showTransactionNotification(
        context: Context,
        transactionId: Long,
        savingId: String,
        tx: ParsedBankTransaction
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createChannel(context)

        val formattedAmount = FormatUtil.formatAmount(tx.amount, context)
        val actionType = if (tx.isExpense) "Expense" else "Income"
        val title = "$formattedAmount $actionType at ${tx.resolvedMerchant}"
        val last4Part = tx.accountLast4?.let { " ••$it" } ?: ""
        val subtitle = "${tx.category} • ${tx.bankName}$last4Part | Auto-logged in Super Center"

        // Open App Intent
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            transactionId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Undo Action Intent
        val undoIntent = Intent(context, SmsActionReceiver::class.java).apply {
            action = SmsActionReceiver.ACTION_UNDO_TRANSACTION
            putExtra(SmsActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(SmsActionReceiver.EXTRA_SAVING_ID, savingId)
            putExtra(SmsActionReceiver.EXTRA_AMOUNT, tx.amount)
            putExtra(SmsActionReceiver.EXTRA_IS_EXPENSE, tx.isExpense)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            transactionId.toInt(),
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$subtitle\n\nReference: ${tx.reference ?: "N/A"}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_clear, "Undo", undoPendingIntent)

        val notificationId = (transactionId % 100000).toInt() + 20000
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

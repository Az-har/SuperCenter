package com.eipna.centsation.sms.notification

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.eipna.centsation.compat.pennywise.PennyWiseRegistry
import com.eipna.centsation.sms.processor.SmsTransactionProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BankNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val SUPPORTED_PACKAGES = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
            "com.dreamplug.androidapp" to "CRED",
            "com.icicibank.mobile" to "ICICI Bank",
            "com.cityunionbank.mobile" to "City Union Bank",
            "in.org.npci.upiapp" to "BHIM UPI"
        )

        fun isPermissionGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val sender = SUPPORTED_PACKAGES[packageName] ?: return

        val registry = PennyWiseRegistry.getInstance(applicationContext)
        if (!registry.isSmsAutoTrackingEnabled()) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val bodyText = if (bigText.isNotBlank()) bigText else text
        if (bodyText.isBlank()) return

        val fullMessage = "$title: $bodyText"
        val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

        serviceScope.launch {
            SmsTransactionProcessor.processSms(
                context = applicationContext,
                sender = sender,
                body = fullMessage,
                timestamp = postTime,
                showNotification = true
            )
        }
    }
}

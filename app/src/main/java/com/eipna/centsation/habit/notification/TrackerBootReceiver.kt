package com.eipna.centsation.habit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.eipna.centsation.habit.widget.HabitWidgetSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class TrackerBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Device boot or package replaced received: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val appContext = context.applicationContext
            val pendingResult = goAsync()
            bootScope.launch {
                try {
                    withTimeoutOrNull(8500L) {
                        TrackerAlarmScheduler.rescheduleAllSuspending(appContext)
                        HabitWidgetSyncManager.performSynchronizedUpdate(appContext)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling boot/package-replace receiver", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "TrackerBootReceiver"
        private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

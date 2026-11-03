package com.eipna.centsation.habit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.eipna.centsation.habit.widget.updateAllHabitWidgets

class TrackerBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Device boot or package replaced received: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) {
            TrackerAlarmScheduler.rescheduleAll(context)
            updateAllHabitWidgets(context)
        }
    }

    companion object {
        private const val TAG = "TrackerBootReceiver"
    }
}

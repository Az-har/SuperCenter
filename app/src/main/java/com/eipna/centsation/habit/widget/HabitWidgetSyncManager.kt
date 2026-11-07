package com.eipna.centsation.habit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.eipna.centsation.util.DeviceHardwareOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized, debounced widget synchronization manager.
 * Eliminates uncoordinated coroutine storms and duplicate Binder IPC calls
 * by batching updates and adapting debounce window to device battery & thermal state.
 */
object HabitWidgetSyncManager {

    private val syncScope = CoroutineScope(Dispatchers.Default)
    private var debounceJob: Job? = null

    @Synchronized
    fun enqueueUpdate(context: Context) {
        val appContext = context.applicationContext
        val delayMs = DeviceHardwareOptimizer.getAdaptiveWidgetDebounceMs(appContext)
        debounceJob?.cancel()
        debounceJob = syncScope.launch {
            delay(delayMs)
            performSynchronizedUpdate(appContext)
        }
    }

    suspend fun performSynchronizedUpdate(context: Context) {
        val appWidgetManager = try {
            AppWidgetManager.getInstance(context)
        } catch (e: Exception) {
            null
        } ?: return

        // 1. SingleTrackerGlanceWidget - check active instances
        try {
            val singleComponent = ComponentName(context, SingleTrackerGlanceReceiver::class.java)
            val singleIds = appWidgetManager.getAppWidgetIds(singleComponent)
            if (singleIds != null && singleIds.isNotEmpty()) {
                SingleTrackerGlanceWidget().updateAll(context)
            }
        } catch (_: Exception) {}

        // 2. HabitHeatmapGlanceWidget - check active instances
        try {
            val heatmapComponent = ComponentName(context, HabitHeatmapGlanceReceiver::class.java)
            val heatmapIds = appWidgetManager.getAppWidgetIds(heatmapComponent)
            if (heatmapIds != null && heatmapIds.isNotEmpty()) {
                HabitHeatmapGlanceWidget().updateAll(context)
            }
        } catch (_: Exception) {}

        // 3. TrackerGlanceWidget (multi-habit list) - check active instances
        try {
            val multiComponent = ComponentName(context, TrackerGlanceReceiver::class.java)
            val multiIds = appWidgetManager.getAppWidgetIds(multiComponent)
            if (multiIds != null && multiIds.isNotEmpty()) {
                TrackerGlanceWidget().updateAll(context)
            }
        } catch (_: Exception) {}
    }
}

package com.eipna.centsation.habit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import com.eipna.centsation.util.DeviceHardwareOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Centralized, debounced widget synchronization manager.
 * Eliminates uncoordinated coroutine storms and duplicate Binder IPC calls
 * by batching updates and adapting debounce window to device battery & thermal state.
 */
object HabitWidgetSyncManager {

    private const val TAG = "HabitWidgetSyncManager"
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updateMutex = Mutex()
    private var pendingDebounceJob: Job? = null

    fun computeNextTrackerIndex(currentIndex: Int, totalCount: Int): Int {
        if (totalCount <= 1) return 0
        val safeCurrent = currentIndex.coerceIn(0, totalCount - 1)
        return (safeCurrent + 1) % totalCount
    }

    @JvmOverloads
    @Synchronized
    fun enqueueUpdate(context: Context, excludeReceiver: Class<*>? = null) {
        val appContext = context.applicationContext
        val delayMs = DeviceHardwareOptimizer.getAdaptiveWidgetDebounceMs(appContext)
        pendingDebounceJob?.cancel()
        pendingDebounceJob = syncScope.launch {
            delay(delayMs)
            withContext(NonCancellable) {
                performSynchronizedUpdate(appContext, excludeReceiver)
            }
        }
    }

    suspend fun performSynchronizedUpdate(context: Context, excludeReceiver: Class<*>? = null) {
        val appWidgetManager = try {
            AppWidgetManager.getInstance(context)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to obtain AppWidgetManager", e)
            null
        } ?: return

        updateMutex.withLock {
            if (excludeReceiver != SingleTrackerGlanceReceiver::class.java) {
                updateProviderIfActive(context, appWidgetManager, SingleTrackerGlanceReceiver::class.java, SingleTrackerGlanceWidget())
            }
            if (excludeReceiver != HabitHeatmapGlanceReceiver::class.java) {
                updateProviderIfActive(context, appWidgetManager, HabitHeatmapGlanceReceiver::class.java, HabitHeatmapGlanceWidget())
            }
            if (excludeReceiver != TrackerGlanceReceiver::class.java) {
                updateProviderIfActive(context, appWidgetManager, TrackerGlanceReceiver::class.java, TrackerGlanceWidget())
            }
        }
    }

    private suspend fun updateProviderIfActive(
        context: Context,
        appWidgetManager: AppWidgetManager,
        receiverClass: Class<*>,
        widget: GlanceAppWidget
    ) {
        try {
            val component = ComponentName(context, receiverClass)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids != null && ids.isNotEmpty()) {
                widget.updateAll(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating widget provider ${receiverClass.simpleName}", e)
        }
    }
}

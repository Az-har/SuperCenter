package com.eipna.centsation.habit.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.notification.TrackerNotificationHandler
import com.eipna.centsation.habit.ui.HabitTrackerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

val SingleTrackerCycleKey = ActionParameters.Key<Boolean>("cycle_next")

class SingleTrackerGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TrackerRepository.getInstance(context)
        val allTrackers = repository.getTrackersWithProgress().firstOrNull() ?: emptyList()

        val prefs = context.getSharedPreferences("single_tracker_widget_prefs", Context.MODE_PRIVATE)
        val savedIndex = prefs.getInt("tracker_index_${id.hashCode()}", 0)
        val activeIndex = if (allTrackers.isNotEmpty()) {
            savedIndex.coerceIn(0, allTrackers.size - 1)
        } else {
            0
        }

        val currentItem = allTrackers.getOrNull(activeIndex)

        provideContent {
            GlanceTheme {
                SingleWidgetRoot(
                    context = context,
                    item = currentItem,
                    totalCount = allTrackers.size,
                    currentIndex = activeIndex,
                    glanceIdHash = id.hashCode()
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SingleWidgetRoot(
        context: Context,
        item: TrackerWithProgress?,
        totalCount: Int,
        currentIndex: Int,
        glanceIdHash: Int
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
        ) {
            if (item == null) {
                EmptySingleWidgetView()
            } else {
                SingleTrackerContent(
                    item = item,
                    totalCount = totalCount,
                    currentIndex = currentIndex,
                    glanceIdHash = glanceIdHash
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun EmptySingleWidgetView() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity<HabitTrackerActivity>())
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎯 Daily Quota",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Tap to create a habit target",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.outline
                )
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun SingleTrackerContent(
        item: TrackerWithProgress,
        totalCount: Int,
        currentIndex: Int,
        glanceIdHash: Int
    ) {
        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit
        val debt = item.accumulatedBacklogDebt

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header Row: Title + Cycle Button (if multiple habits)
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tracker.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity<HabitTrackerActivity>())
                )

                if (totalCount > 1) {
                    Button(
                        text = "${currentIndex + 1}/$totalCount ❯",
                        onClick = actionRunCallback<SingleTrackerCycleCallback>(
                            actionParametersOf(
                                SingleTrackerCycleKey to true,
                                ActionParameters.Key<Int>("glance_hash") to glanceIdHash,
                                ActionParameters.Key<Int>("total_count") to totalCount,
                                ActionParameters.Key<Int>("current_index") to currentIndex
                            )
                        ),
                        modifier = GlanceModifier.height(28.dp).width(62.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Main Status / Counter
            if (remaining == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✅ Done!",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "Cleared for today",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.outline
                        )
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$remaining",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = if (debt > 0) GlanceTheme.colors.error else GlanceTheme.colors.primary
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "$unit left",
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    if (debt > 0) {
                        Text(
                            text = "+$debt debt",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = GlanceTheme.colors.error
                            )
                        )
                    } else {
                        Text(
                            text = "Target: ${tracker.baseDailyTarget}",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = GlanceTheme.colors.outline
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = item.completionRatio,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.outline
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Interactive Buttons Row: -1, +1, +Target, Done
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    text = "-1",
                    onClick = actionRunCallback<SingleTrackerLogCallback>(
                        actionParametersOf(
                            TrackerIdKey to tracker.id,
                            DeltaCountKey to -1
                        )
                    ),
                    modifier = GlanceModifier.width(48.dp).height(34.dp)
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                Button(
                    text = "+1",
                    onClick = actionRunCallback<SingleTrackerLogCallback>(
                        actionParametersOf(
                            TrackerIdKey to tracker.id,
                            DeltaCountKey to 1
                        )
                    ),
                    modifier = GlanceModifier.width(48.dp).height(34.dp)
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                if (remaining > 0) {
                    Button(
                        text = "+${tracker.baseDailyTarget}",
                        onClick = actionRunCallback<SingleTrackerLogCallback>(
                            actionParametersOf(
                                TrackerIdKey to tracker.id,
                                DeltaCountKey to tracker.baseDailyTarget
                            )
                        ),
                        modifier = GlanceModifier.width(58.dp).height(34.dp)
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Button(
                        text = "✓ Done",
                        onClick = actionRunCallback<SingleTrackerCompleteCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id)
                        ),
                        modifier = GlanceModifier.width(64.dp).height(34.dp)
                    )
                }
            }
        }
    }
}

class SingleTrackerGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleTrackerGlanceWidget()
}

class SingleTrackerCycleCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val glanceHash = parameters[ActionParameters.Key<Int>("glance_hash")] ?: glanceId.hashCode()
        val totalCount = parameters[ActionParameters.Key<Int>("total_count")] ?: 1
        val currentIndex = parameters[ActionParameters.Key<Int>("current_index")] ?: 0

        val nextIndex = (currentIndex + 1) % totalCount
        val prefs = context.getSharedPreferences("single_tracker_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("tracker_index_$glanceHash", nextIndex).apply()

        SingleTrackerGlanceWidget().update(context, glanceId)
    }
}

class SingleTrackerLogCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val trackerId = parameters[TrackerIdKey] ?: return
        val delta = parameters[DeltaCountKey] ?: return
        val repository = TrackerRepository.getInstance(context)

        val updated = repository.logReps(trackerId, delta)
        SingleTrackerGlanceWidget().update(context, glanceId)
        updateAllHabitWidgets(context)

        if (updated != null) {
            TrackerNotificationHandler.showTrackerNotification(context, updated)
        }
    }
}

class SingleTrackerCompleteCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val trackerId = parameters[TrackerIdKey] ?: return
        val repository = TrackerRepository.getInstance(context)
        val current = repository.getTrackerWithProgressById(trackerId) ?: return

        val updated = repository.logReps(trackerId, current.remainingToday)
        SingleTrackerGlanceWidget().update(context, glanceId)
        updateAllHabitWidgets(context)

        if (updated != null) {
            TrackerNotificationHandler.showTrackerNotification(context, updated)
        }
    }
}

fun updateAllSingleHabitWidgets(context: Context) {
    try {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val glanceComponent = android.content.ComponentName(context, SingleTrackerGlanceReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(glanceComponent)
        if (widgetIds == null || widgetIds.isEmpty()) return
    } catch (e: Exception) {
        // Fallback
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            SingleTrackerGlanceWidget().updateAll(context)
        } catch (e: Exception) {
            // Widget might not be placed
        }
    }
}

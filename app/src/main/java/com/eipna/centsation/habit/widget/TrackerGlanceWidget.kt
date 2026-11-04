package com.eipna.centsation.habit.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import com.eipna.centsation.habit.util.HabitHeatmapUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

val TrackerIdKey = ActionParameters.Key<Long>("tracker_id")
val DeltaCountKey = ActionParameters.Key<Int>("delta_count")

class TrackerGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_MEDIUM = DpSize(180.dp, 110.dp)
        private val SIZE_WIDE = DpSize(300.dp, 160.dp)
        private val SIZE_EXPANDED = DpSize(360.dp, 260.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_MEDIUM, SIZE_WIDE, SIZE_EXPANDED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TrackerRepository.getInstance(context)
        val items = repository.getTrackersWithProgress().firstOrNull() ?: emptyList()

        provideContent {
            GlanceTheme {
                WidgetRoot(context = context, items = items)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetRoot(context: Context, items: List<TrackerWithProgress>) {
        val doneCount = items.count { it.isCompletedToday }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
        ) {
            if (items.isEmpty()) {
                EmptyWidgetView()
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(actionStartActivity<HabitTrackerActivity>())
                        ) {
                            Text(
                                text = "🎯 Daily Quotas",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = GlanceTheme.colors.onSurface
                                )
                            )
                            Text(
                                text = "$doneCount of ${items.size} Completed",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (doneCount == items.size) GlanceTheme.colors.primary else GlanceTheme.colors.outline
                                )
                            )
                        }

                        Button(
                            text = "+ Add",
                            onClick = actionStartActivity<HabitTrackerActivity>(),
                            modifier = GlanceModifier.height(28.dp).width(58.dp)
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(items) { item ->
                            TrackerWidgetItem(item = item)
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun EmptyWidgetView() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity<HabitTrackerActivity>())
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎯 Daily Quotas",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Tap to set up your daily targets",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.primary
                )
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun TrackerWidgetItem(item: TrackerWithProgress) {
        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit
        val debt = item.accumulatedBacklogDebt
        val streakStats = HabitHeatmapUtil.calculateStreakStats(tracker, item.logs, item.todayDate)

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(14.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(10.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleDisplay = if (streakStats.currentStreak > 0) "${tracker.title} 🔥 ${streakStats.currentStreak}d" else tracker.title
                Text(
                    text = titleDisplay,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                if (remaining == 0) {
                    Text(
                        text = "✅ Done",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                } else {
                    val statusText = if (debt > 0) {
                        "$remaining $unit (+$debt debt)"
                    } else {
                        "$remaining $unit left"
                    }
                    Text(
                        text = statusText,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (debt > 0) GlanceTheme.colors.error else GlanceTheme.colors.primary
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Progress bar with percentage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = item.completionRatio,
                    modifier = GlanceModifier.defaultWeight().height(5.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.outline
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "${item.progressPercent}%",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.outline
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Stepper Control Buttons
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    text = "-1",
                    onClick = actionRunCallback<TrackerLogCallback>(
                        actionParametersOf(
                            TrackerIdKey to tracker.id,
                            DeltaCountKey to -1
                        )
                    ),
                    modifier = GlanceModifier.width(46.dp).height(30.dp)
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                Button(
                    text = "+1",
                    onClick = actionRunCallback<TrackerLogCallback>(
                        actionParametersOf(
                            TrackerIdKey to tracker.id,
                            DeltaCountKey to 1
                        )
                    ),
                    modifier = GlanceModifier.width(46.dp).height(30.dp)
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                if (remaining > 0) {
                    Button(
                        text = "+${tracker.baseDailyTarget}",
                        onClick = actionRunCallback<TrackerLogCallback>(
                            actionParametersOf(
                                TrackerIdKey to tracker.id,
                                DeltaCountKey to tracker.baseDailyTarget
                            )
                        ),
                        modifier = GlanceModifier.width(54.dp).height(30.dp)
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Button(
                        text = "✓ Done",
                        onClick = actionRunCallback<TrackerCompleteCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id)
                        ),
                        modifier = GlanceModifier.width(62.dp).height(30.dp)
                    )
                }
            }
        }
    }
}

class TrackerGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrackerGlanceWidget()
}

class TrackerLogCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val trackerId = parameters[TrackerIdKey] ?: return
        val delta = parameters[DeltaCountKey] ?: return
        val repository = TrackerRepository.getInstance(context)

        val updated = repository.logReps(trackerId, delta)
        TrackerGlanceWidget().update(context, glanceId)
        updateAllSingleHabitWidgets(context)

        if (updated != null) {
            TrackerNotificationHandler.showTrackerNotification(context, updated)
        }
    }
}

class TrackerCompleteCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val trackerId = parameters[TrackerIdKey] ?: return
        val repository = TrackerRepository.getInstance(context)
        val current = repository.getTrackerWithProgressById(trackerId) ?: return

        val updated = repository.logReps(trackerId, current.remainingToday)
        TrackerGlanceWidget().update(context, glanceId)
        updateAllSingleHabitWidgets(context)

        if (updated != null) {
            TrackerNotificationHandler.showTrackerNotification(context, updated)
        }
    }
}

fun updateAllHabitWidgets(context: Context) {
    updateAllSingleHabitWidgets(context)
    updateAllHabitHeatmapWidgets(context)

    try {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val glanceComponent = android.content.ComponentName(context, TrackerGlanceReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(glanceComponent)
        if (widgetIds == null || widgetIds.isEmpty()) {
            return
        }
    } catch (e: Exception) {
        // Fallback
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            TrackerGlanceWidget().updateAll(context)
        } catch (e: Exception) {
            // Widget might not be placed
        }
    }
}

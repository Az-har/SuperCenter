package com.eipna.centsation.habit.widget

import android.content.Context
import androidx.compose.ui.unit.dp
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
import androidx.glance.unit.ColorProvider
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import com.eipna.centsation.habit.calendar.CalendarSyncManager
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.notification.TrackerNotificationHandler
import com.eipna.centsation.habit.ui.HabitTrackerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

val TrackerIdKey = ActionParameters.Key<Long>("tracker_id")
val DeltaCountKey = ActionParameters.Key<Int>("delta_count")

class TrackerGlanceWidget : GlanceAppWidget() {

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
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(actionStartActivity<HabitTrackerActivity>())
        ) {
            if (items.isEmpty()) {
                EmptyWidgetView()
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Quotas",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onSurface
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Text(
                            text = "Super Center",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontWeight = FontWeight.Medium
                            )
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
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎯 Daily Quotas",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Tap to create your first habit tracker!",
                style = TextStyle(color = GlanceTheme.colors.outline)
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun TrackerWidgetItem(item: TrackerWithProgress) {
        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit
        val debt = item.accumulatedBacklogDebt

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(10.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tracker.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                if (remaining == 0) {
                    Text(
                        text = "✅ Done",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.primary
                        )
                    )
                } else {
                    val statusText = if (debt > 0) {
                        "$remaining $unit left (+$debt debt)"
                    } else {
                        "$remaining $unit left"
                    }
                    Text(
                        text = statusText,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = if (debt > 0) GlanceTheme.colors.error else GlanceTheme.colors.primary
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = item.completionRatio,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.outline
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Control Buttons
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
                    modifier = GlanceModifier.width(52.dp).height(34.dp)
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
                    modifier = GlanceModifier.width(52.dp).height(34.dp)
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
                        modifier = GlanceModifier.width(64.dp).height(34.dp)
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Button(
                        text = "Done",
                        onClick = actionRunCallback<TrackerCompleteCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id)
                        ),
                        modifier = GlanceModifier.width(62.dp).height(34.dp)
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

        if (updated != null) {
            CalendarSyncManager.syncTrackerEvent(context, updated)
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

        if (updated != null) {
            CalendarSyncManager.syncTrackerEvent(context, updated)
            TrackerNotificationHandler.showTrackerNotification(context, updated)
        }
    }
}

fun updateAllHabitWidgets(context: Context) {
    try {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val glanceComponent = android.content.ComponentName(context, TrackerGlanceReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(glanceComponent)
        // If widget is not placed on the home screen, skip recomposition and IPC to save CPU and battery
        if (widgetIds == null || widgetIds.isEmpty()) {
            return
        }
    } catch (e: Exception) {
        // Fallback if widget manager throws
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            TrackerGlanceWidget().updateAll(context)
        } catch (e: Exception) {
            // Widget might not be placed on home screen yet
        }
    }
}

package com.eipna.centsation.habit.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
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
import com.eipna.centsation.habit.util.TrackerDateUtil
import kotlinx.coroutines.flow.firstOrNull

val TrackerIdKey = ActionParameters.Key<Long>("tracker_id")
val DeltaCountKey = ActionParameters.Key<Int>("delta_count")

class TrackerGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_MEDIUM = DpSize(180.dp, 110.dp)
        private val SIZE_STANDARD = DpSize(270.dp, 200.dp)
        private val SIZE_WIDE = DpSize(300.dp, 160.dp)
        private val SIZE_EXPANDED = DpSize(360.dp, 260.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_MEDIUM, SIZE_STANDARD, SIZE_WIDE, SIZE_EXPANDED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TrackerRepository.getInstance(context)
        val initialItems = repository.getTrackersWithProgress().firstOrNull() ?: emptyList()

        provideContent {
            val items by repository.getTrackersWithProgress().collectAsState(initial = initialItems)
            val today = TrackerDateUtil.getTodayDateString()
            val streaks = remember(items, today) {
                items.associate {
                    it.tracker.id to HabitHeatmapUtil.calculateStreakStats(it.tracker, it.logs, it.todayDate)
                }
            }

            GlanceTheme(colors = HabitGlanceTheme.colorsFor(context)) {
                WidgetRoot(items = items, streaks = streaks)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetRoot(
        items: List<TrackerWithProgress>,
        streaks: Map<Long, com.eipna.centsation.habit.util.StreakStats>
    ) {
        val size = LocalSize.current
        val ultraCompact = size.height < 140.dp
        val compact = size.height < 200.dp
        val doneCount = items.count { it.isCompletedToday }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(if (ultraCompact) 8.dp else if (compact) 9.dp else 12.dp)
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
                                    fontSize = if (ultraCompact) 12.sp else if (compact) 13.sp else 15.sp,
                                    color = GlanceTheme.colors.onSurface
                                )
                            )
                            if (!ultraCompact) {
                                Text(
                                    text = "$doneCount of ${items.size} Completed",
                                    style = TextStyle(
                                        fontSize = if (compact) 10.sp else 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (doneCount == items.size) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(10.dp)
                                .background(GlanceTheme.colors.primary)
                                .clickable(actionStartActivity<HabitTrackerActivity>())
                                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 6.dp else 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ Add",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = GlanceTheme.colors.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(if (ultraCompact) 4.dp else if (compact) 5.dp else 8.dp))

                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(items = items, itemId = { it.tracker.id.toLong() }) { item ->
                            TrackerWidgetItem(
                                item = item,
                                streakStats = streaks[item.tracker.id],
                                compact = compact,
                                ultraCompact = ultraCompact
                            )
                            Spacer(modifier = GlanceModifier.height(if (ultraCompact) 4.dp else if (compact) 5.dp else 8.dp))
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
    private fun TrackerWidgetItem(
        item: TrackerWithProgress,
        streakStats: com.eipna.centsation.habit.util.StreakStats?,
        compact: Boolean,
        ultraCompact: Boolean
    ) {
        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit
        val debt = item.accumulatedBacklogDebt
        val currentStreak = streakStats?.currentStreak ?: 0
        val btnVPad = if (ultraCompact) 2.dp else if (compact) 3.dp else 5.dp
        val btnHPad = if (ultraCompact) 8.dp else if (compact) 9.dp else 12.dp
        val cardPad = if (ultraCompact) 6.dp else if (compact) 7.dp else 10.dp
        val rowGap = if (ultraCompact) 2.dp else if (compact) 3.dp else 5.dp

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(if (compact) 12.dp else 14.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(cardPad)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<HabitTrackerActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleDisplay = if (currentStreak > 0) "${tracker.title} 🔥 ${currentStreak}d" else tracker.title
                Text(
                    text = titleDisplay,
                    maxLines = 1,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 12.sp else 13.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                if (remaining == 0) {
                    Text(
                        text = "✅ Done",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 11.sp else 12.sp,
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
                            fontSize = if (compact) 10.sp else 11.sp,
                            color = if (debt > 0) GlanceTheme.colors.error else GlanceTheme.colors.primary
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(rowGap))

            // Progress bar with percentage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = item.completionRatio,
                    modifier = GlanceModifier.defaultWeight().height(if (compact) 4.dp else 5.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surface
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "${item.progressPercent}%",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(rowGap))

            // Stepper Control Buttons
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(9.dp)
                        .background(GlanceTheme.colors.surface)
                        .clickable(actionRunCallback<TrackerLogCallback>(
                            actionParametersOf(
                                TrackerIdKey to tracker.id,
                                DeltaCountKey to -1
                            )
                        ))
                        .padding(horizontal = btnHPad, vertical = btnVPad),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = if (compact) 11.sp else 12.sp, color = GlanceTheme.colors.onSurface)
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Box(
                    modifier = GlanceModifier
                        .cornerRadius(9.dp)
                        .background(GlanceTheme.colors.surface)
                        .clickable(actionRunCallback<TrackerLogCallback>(
                            actionParametersOf(
                                TrackerIdKey to tracker.id,
                                DeltaCountKey to 1
                            )
                        ))
                        .padding(horizontal = btnHPad, vertical = btnVPad),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = if (compact) 11.sp else 12.sp, color = GlanceTheme.colors.onSurface)
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                if (remaining > 0) {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(9.dp)
                            .background(GlanceTheme.colors.surface)
                            .clickable(actionRunCallback<TrackerLogCallback>(
                                actionParametersOf(
                                    TrackerIdKey to tracker.id,
                                    DeltaCountKey to tracker.baseDailyTarget
                                )
                            ))
                            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = btnVPad),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${tracker.baseDailyTarget}",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 11.sp, color = GlanceTheme.colors.onSurface)
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(9.dp)
                            .background(GlanceTheme.colors.primary)
                            .clickable(actionRunCallback<TrackerCompleteCallback>(
                                actionParametersOf(TrackerIdKey to tracker.id)
                            ))
                            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = btnVPad),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Done",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 11.sp, color = GlanceTheme.colors.onPrimary)
                        )
                    }
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
        HabitWidgetSyncManager.enqueueUpdate(context, excludeReceiver = TrackerGlanceReceiver::class.java)

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
        HabitWidgetSyncManager.enqueueUpdate(context, excludeReceiver = TrackerGlanceReceiver::class.java)

        if (updated != null) {
            TrackerNotificationHandler.showTrackerNotification(context, updated)
        }
    }
}

fun updateAllHabitWidgets(context: Context) {
    HabitWidgetSyncManager.enqueueUpdate(context)
}

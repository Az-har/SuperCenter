package com.eipna.centsation.habit.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
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

class SingleTrackerGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_COMPACT = DpSize(130.dp, 90.dp)
        private val SIZE_MEDIUM = DpSize(220.dp, 120.dp)
        private val SIZE_EXPANDED = DpSize(320.dp, 180.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_COMPACT, SIZE_MEDIUM, SIZE_EXPANDED)
    )

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
        val streakStats = currentItem?.let {
            HabitHeatmapUtil.calculateStreakStats(it.tracker, it.logs, it.todayDate)
        }

        provideContent {
            GlanceTheme(colors = HabitGlanceTheme.Colors) {
                SingleWidgetContainer(
                    context = context,
                    item = currentItem,
                    stats = streakStats,
                    totalCount = allTrackers.size,
                    currentIndex = activeIndex,
                    glanceIdHash = id.hashCode()
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SingleWidgetContainer(
        context: Context,
        item: TrackerWithProgress?,
        stats: com.eipna.centsation.habit.util.StreakStats?,
        totalCount: Int,
        currentIndex: Int,
        glanceIdHash: Int
    ) {
        val size = LocalSize.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
        ) {
            if (item == null) {
                EmptyWidgetState()
            } else if (size.width < 180.dp || size.height < 110.dp) {
                CompactLayout(item = item, stats = stats)
            } else if (size.width >= 300.dp && size.height >= 160.dp) {
                ExpandedLayout(
                    item = item,
                    stats = stats,
                    totalCount = totalCount,
                    currentIndex = currentIndex,
                    glanceIdHash = glanceIdHash
                )
            } else {
                MediumLayout(
                    item = item,
                    stats = stats,
                    totalCount = totalCount,
                    currentIndex = currentIndex,
                    glanceIdHash = glanceIdHash
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun EmptyWidgetState() {
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
                    fontSize = 16.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Tap to create your first habit",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.primary
                )
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun CompactLayout(
        item: TrackerWithProgress,
        stats: com.eipna.centsation.habit.util.StreakStats?
    ) {
        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit
        val streak = stats?.currentStreak ?: 0

        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<HabitTrackerActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tracker.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (streak > 0) {
                    Text(
                        text = "🔥 ${streak}d",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
                if (remaining == 0) {
                    Text(
                        text = "✅ Done",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                } else {
                    Text(
                        text = "$remaining $unit",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (item.accumulatedBacklogDebt > 0) GlanceTheme.colors.error else GlanceTheme.colors.primary
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            LinearProgressIndicator(
                progress = item.completionRatio,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.outline
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<SingleTrackerLogCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id, DeltaCountKey to -1)
                        ))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<SingleTrackerLogCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id, DeltaCountKey to 1)
                        ))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(GlanceTheme.colors.primary)
                        .clickable(actionRunCallback<SingleTrackerCompleteCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id)
                        ))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onPrimary)
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun MediumLayout(
        item: TrackerWithProgress,
        stats: com.eipna.centsation.habit.util.StreakStats?,
        totalCount: Int,
        currentIndex: Int,
        glanceIdHash: Int
    ) {
        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit
        val debt = item.accumulatedBacklogDebt
        val streak = stats?.currentStreak ?: 0

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header Row: Title & Cycle Selector
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

                if (streak > 0) {
                    Text(
                        text = "🔥 ${streak}d",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }

                if (totalCount > 1) {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(10.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionRunCallback<SingleTrackerCycleCallback>(
                                actionParametersOf(
                                    ActionParameters.Key<Int>("glance_hash") to glanceIdHash,
                                    ActionParameters.Key<Int>("total_count") to totalCount,
                                    ActionParameters.Key<Int>("current_index") to currentIndex
                                )
                            ))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${currentIndex + 1}/$totalCount ❯",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Counter Display & Status Badge
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (remaining == 0) {
                    Text(
                        text = "✅ Done Today!",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "Target cleared",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.outline
                        )
                    )
                } else {
                    Text(
                        text = "$remaining",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = if (debt > 0) GlanceTheme.colors.error else GlanceTheme.colors.primary
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Column {
                        Text(
                            text = "$unit remaining",
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = GlanceTheme.colors.onSurface
                            )
                        )
                        Text(
                            text = if (debt > 0) "+$debt backlog debt" else "Daily Target: ${tracker.baseDailyTarget}",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (debt > 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (debt > 0) GlanceTheme.colors.error else GlanceTheme.colors.outline
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Progress Bar with Percentage Indicator
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = item.completionRatio,
                    modifier = GlanceModifier.defaultWeight().height(6.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.outline
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "${item.progressPercent}%",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.primary
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Action Buttons
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<SingleTrackerLogCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id, DeltaCountKey to -1)
                        ))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<SingleTrackerLogCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id, DeltaCountKey to 1)
                        ))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                if (remaining > 0) {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionRunCallback<SingleTrackerLogCallback>(
                                actionParametersOf(TrackerIdKey to tracker.id, DeltaCountKey to tracker.baseDailyTarget)
                            ))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${tracker.baseDailyTarget}",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(GlanceTheme.colors.primary)
                            .clickable(actionRunCallback<SingleTrackerCompleteCallback>(
                                actionParametersOf(TrackerIdKey to tracker.id)
                            ))
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Clear Quota",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlanceTheme.colors.onPrimary)
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ExpandedLayout(
        item: TrackerWithProgress,
        stats: com.eipna.centsation.habit.util.StreakStats?,
        totalCount: Int,
        currentIndex: Int,
        glanceIdHash: Int
    ) {
        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit
        val debt = item.accumulatedBacklogDebt
        val streak = stats?.currentStreak ?: 0

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Top Bar
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
                        text = tracker.title,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Text(
                        text = "🔥 ${streak}d Streak • Base: ${tracker.baseDailyTarget} $unit/day",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                }

                if (totalCount > 1) {
                    Button(
                        text = "Habit ${currentIndex + 1} of $totalCount ❯",
                        onClick = actionRunCallback<SingleTrackerCycleCallback>(
                            actionParametersOf(
                                ActionParameters.Key<Int>("glance_hash") to glanceIdHash,
                                ActionParameters.Key<Int>("total_count") to totalCount,
                                ActionParameters.Key<Int>("current_index") to currentIndex
                            )
                        ),
                        modifier = GlanceModifier.height(30.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Statistics Grid Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    if (remaining == 0) {
                        Text(
                            text = "🎉 Quota Cleared!",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = GlanceTheme.colors.primary
                            )
                        )
                        Text(
                            text = "All caught up for today",
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.outline)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$remaining",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = if (debt > 0) GlanceTheme.colors.error else GlanceTheme.colors.primary
                                )
                            )
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(
                                text = "$unit due today",
                                style = TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = GlanceTheme.colors.onSurface
                                )
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Done Today: ${item.todayCompletedReps} $unit",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.primary
                        )
                    )
                    if (debt > 0) {
                        Text(
                            text = "Backlog Debt: +$debt $unit",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.error
                            )
                        )
                    } else {
                        Text(
                            text = "Days Active: ${item.daysActive}",
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.outline)
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Progress Bar
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = item.completionRatio,
                    modifier = GlanceModifier.defaultWeight().height(8.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.outline
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "${item.progressPercent}%",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.primary
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Action Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(14.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<SingleTrackerLogCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id, DeltaCountKey to -1)
                        ))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(modifier = GlanceModifier.width(10.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(14.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<SingleTrackerLogCallback>(
                            actionParametersOf(TrackerIdKey to tracker.id, DeltaCountKey to 1)
                        ))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(modifier = GlanceModifier.width(10.dp))
                if (remaining > 0) {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(14.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionRunCallback<SingleTrackerLogCallback>(
                                actionParametersOf(TrackerIdKey to tracker.id, DeltaCountKey to tracker.baseDailyTarget)
                            ))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${tracker.baseDailyTarget}",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(14.dp)
                            .background(GlanceTheme.colors.primary)
                            .clickable(actionRunCallback<SingleTrackerCompleteCallback>(
                                actionParametersOf(TrackerIdKey to tracker.id)
                            ))
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Clear Quota",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onPrimary)
                        )
                    }
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
        updateAllHabitWidgets(context)

        if (updated != null) {
            TrackerNotificationHandler.showTrackerNotification(context, updated)
        }
    }
}

fun updateAllSingleHabitWidgets(context: Context) {
    HabitWidgetSyncManager.enqueueUpdate(context)
}

package com.eipna.centsation.habit.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
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
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.notification.TrackerNotificationHandler
import com.eipna.centsation.habit.ui.HabitTrackerActivity
import com.eipna.centsation.habit.util.HabitHeatmapUtil
import com.eipna.centsation.habit.util.TrackerDateUtil
import kotlinx.coroutines.flow.firstOrNull

class SingleTrackerGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_COMPACT = DpSize(130.dp, 90.dp)
        private val SIZE_MEDIUM = DpSize(220.dp, 120.dp)
        private val SIZE_EXPANDED = DpSize(320.dp, 180.dp)
        val KEY_TRACKER_INDEX = intPreferencesKey("single_tracker_index")
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_COMPACT, SIZE_MEDIUM, SIZE_EXPANDED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TrackerRepository.getInstance(context)
        val initialTrackers = repository.getTrackersWithProgress().firstOrNull() ?: emptyList()

        provideContent {
            val allTrackers by repository.getTrackersWithProgress().collectAsState(initial = initialTrackers)
            val prefs = currentState<Preferences>()
            val savedIndex = prefs[KEY_TRACKER_INDEX] ?: 0
            val activeIndex = if (allTrackers.isNotEmpty()) {
                savedIndex.coerceIn(0, allTrackers.size - 1)
            } else {
                0
            }

            val currentItem = allTrackers.getOrNull(activeIndex)
            val today = TrackerDateUtil.getTodayDateString()
            val streakStats = remember(currentItem, today) {
                currentItem?.let {
                    HabitHeatmapUtil.calculateStreakStats(it.tracker, it.logs, it.todayDate)
                }
            }

            GlanceTheme(colors = HabitGlanceTheme.colorsFor(context)) {
                SingleWidgetContainer(
                    item = currentItem,
                    stats = streakStats,
                    totalCount = allTrackers.size,
                    currentIndex = activeIndex
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SingleWidgetContainer(
        item: TrackerWithProgress?,
        stats: com.eipna.centsation.habit.util.StreakStats?,
        totalCount: Int,
        currentIndex: Int
    ) {
        val size = LocalSize.current
        val isCompact = size.width < 180.dp || size.height < 110.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(if (isCompact) 8.dp else 12.dp)
        ) {
            if (item == null) {
                EmptyWidgetState()
            } else if (isCompact) {
                CompactLayout(
                    item = item,
                    stats = stats,
                    totalCount = totalCount,
                    currentIndex = currentIndex
                )
            } else if (size.width >= 300.dp && size.height >= 160.dp) {
                ExpandedLayout(
                    item = item,
                    stats = stats,
                    totalCount = totalCount,
                    currentIndex = currentIndex
                )
            } else {
                MediumLayout(
                    item = item,
                    stats = stats,
                    totalCount = totalCount,
                    currentIndex = currentIndex
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
        stats: com.eipna.centsation.habit.util.StreakStats?,
        totalCount: Int,
        currentIndex: Int
    ) {
        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit
        val streak = stats?.currentStreak ?: 0

        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity<HabitTrackerActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tracker.title,
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = GlanceTheme.colors.onSurface
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    if (streak > 0 && totalCount <= 1) {
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
                    Text(
                        text = if (remaining == 0) "✅" else "$remaining $unit",
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (remaining > 0 && item.accumulatedBacklogDebt > 0) GlanceTheme.colors.error else GlanceTheme.colors.primary
                        )
                    )
                }

                if (totalCount > 1) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    CyclePillButton(
                        label = "${currentIndex + 1}/$totalCount ❯",
                        fontSize = 11.sp,
                        cornerRadius = 10.dp,
                        horizontalPadding = 8.dp,
                        verticalPadding = 4.dp,
                        minHeight = 28.dp
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            LinearProgressIndicator(
                progress = item.completionRatio,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            StepperActionRow(
                trackerId = tracker.id,
                baseDailyTarget = tracker.baseDailyTarget,
                remaining = remaining,
                pillRadius = 12.dp,
                hPad = 12.dp,
                targetHPad = 12.dp,
                completeHPad = 14.dp,
                vPad = 6.dp,
                buttonHeight = 32.dp,
                fontSize = 13.sp,
                targetFontSize = 13.sp,
                gap = 8.dp,
                showBaseTarget = false,
                completeLabel = "✓"
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun MediumLayout(
        item: TrackerWithProgress,
        stats: com.eipna.centsation.habit.util.StreakStats?,
        totalCount: Int,
        currentIndex: Int
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
                    maxLines = 1,
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
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "🔥 ${streak}d",
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                }

                if (totalCount > 1) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    CyclePillButton(
                        label = "Habit ${currentIndex + 1}/$totalCount ❯",
                        fontSize = 12.sp,
                        cornerRadius = 14.dp,
                        horizontalPadding = 14.dp,
                        verticalPadding = 8.dp,
                        minHeight = 40.dp
                    )
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
                            color = GlanceTheme.colors.onSurfaceVariant
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
                                color = if (debt > 0) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant
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
                    backgroundColor = GlanceTheme.colors.surfaceVariant
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

            StepperActionRow(
                trackerId = tracker.id,
                baseDailyTarget = tracker.baseDailyTarget,
                remaining = remaining,
                pillRadius = 12.dp,
                hPad = 14.dp,
                targetHPad = 12.dp,
                completeHPad = 16.dp,
                vPad = 7.dp,
                buttonHeight = 38.dp,
                fontSize = 14.sp,
                targetFontSize = 13.sp,
                gap = 8.dp,
                showBaseTarget = true,
                completeLabel = "✓ Clear Quota"
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun ExpandedLayout(
        item: TrackerWithProgress,
        stats: com.eipna.centsation.habit.util.StreakStats?,
        totalCount: Int,
        currentIndex: Int
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
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Text(
                        text = "🔥 ${streak}d Streak • ${tracker.baseDailyTarget} $unit/day",
                        maxLines = 1,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                }

                if (totalCount > 1) {
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    CyclePillButton(
                        label = "Habit ${currentIndex + 1} of $totalCount ❯",
                        fontSize = 14.sp,
                        cornerRadius = 16.dp,
                        horizontalPadding = 20.dp,
                        verticalPadding = 10.dp,
                        minHeight = 48.dp
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
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
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
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
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
                    backgroundColor = GlanceTheme.colors.surfaceVariant
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

            StepperActionRow(
                trackerId = tracker.id,
                baseDailyTarget = tracker.baseDailyTarget,
                remaining = remaining,
                pillRadius = 14.dp,
                hPad = 16.dp,
                targetHPad = 14.dp,
                completeHPad = 20.dp,
                vPad = 10.dp,
                buttonHeight = 44.dp,
                fontSize = 15.sp,
                targetFontSize = 14.sp,
                gap = 10.dp,
                showBaseTarget = true,
                completeLabel = "✓ Clear Quota"
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun CyclePillButton(
        label: String,
        fontSize: TextUnit,
        cornerRadius: Dp,
        horizontalPadding: Dp,
        verticalPadding: Dp,
        minHeight: Dp? = null
    ) {
        PillButton(
            label = label,
            textColor = GlanceTheme.colors.onPrimaryContainer,
            backgroundColor = GlanceTheme.colors.primaryContainer,
            fontSize = fontSize,
            cornerRadius = cornerRadius,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            minHeight = minHeight,
            onClick = actionRunCallback<SingleTrackerCycleCallback>()
        )
    }

    @androidx.compose.runtime.Composable
    private fun StepperActionRow(
        trackerId: Long,
        baseDailyTarget: Int,
        remaining: Int,
        pillRadius: Dp,
        hPad: Dp,
        targetHPad: Dp,
        completeHPad: Dp,
        vPad: Dp,
        buttonHeight: Dp? = null,
        fontSize: TextUnit,
        targetFontSize: TextUnit,
        gap: Dp,
        showBaseTarget: Boolean,
        completeLabel: String
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton(
                label = "−1",
                textColor = GlanceTheme.colors.onSurfaceVariant,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                fontSize = fontSize,
                cornerRadius = pillRadius,
                horizontalPadding = hPad,
                verticalPadding = vPad,
                minHeight = buttonHeight,
                onClick = actionRunCallback<SingleTrackerLogCallback>(
                    actionParametersOf(TrackerIdKey to trackerId, DeltaCountKey to -1)
                )
            )
            Spacer(modifier = GlanceModifier.width(gap))
            PillButton(
                label = "+1",
                textColor = GlanceTheme.colors.onSurfaceVariant,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                fontSize = fontSize,
                cornerRadius = pillRadius,
                horizontalPadding = hPad,
                verticalPadding = vPad,
                minHeight = buttonHeight,
                onClick = actionRunCallback<SingleTrackerLogCallback>(
                    actionParametersOf(TrackerIdKey to trackerId, DeltaCountKey to 1)
                )
            )
            if (remaining > 0) {
                if (showBaseTarget && baseDailyTarget > 1) {
                    Spacer(modifier = GlanceModifier.width(gap))
                    PillButton(
                        label = "+$baseDailyTarget",
                        textColor = GlanceTheme.colors.onSurfaceVariant,
                        backgroundColor = GlanceTheme.colors.surfaceVariant,
                        fontSize = targetFontSize,
                        cornerRadius = pillRadius,
                        horizontalPadding = targetHPad,
                        verticalPadding = vPad,
                        minHeight = buttonHeight,
                        onClick = actionRunCallback<SingleTrackerLogCallback>(
                            actionParametersOf(TrackerIdKey to trackerId, DeltaCountKey to baseDailyTarget)
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                PillButton(
                    label = completeLabel,
                    textColor = GlanceTheme.colors.onPrimary,
                    backgroundColor = GlanceTheme.colors.primary,
                    fontSize = targetFontSize,
                    cornerRadius = pillRadius,
                    horizontalPadding = completeHPad,
                    verticalPadding = vPad,
                    minHeight = buttonHeight,
                    onClick = actionRunCallback<SingleTrackerCompleteCallback>(
                        actionParametersOf(TrackerIdKey to trackerId)
                    )
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun PillButton(
        label: String,
        textColor: ColorProvider,
        backgroundColor: ColorProvider,
        fontSize: TextUnit,
        cornerRadius: Dp,
        horizontalPadding: Dp,
        verticalPadding: Dp,
        minHeight: Dp? = null,
        onClick: Action
    ) {
        val baseModifier = if (minHeight != null) {
            GlanceModifier.height(minHeight)
        } else {
            GlanceModifier
        }
        Box(
            modifier = baseModifier
                .cornerRadius(cornerRadius)
                .background(backgroundColor)
                .clickable(onClick)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fontSize, color = textColor)
            )
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
        val repository = TrackerRepository.getInstance(context)
        val totalCount = repository.getActiveTrackerCount()
        if (totalCount <= 0) return

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val current = this[SingleTrackerGlanceWidget.KEY_TRACKER_INDEX] ?: 0
                this[SingleTrackerGlanceWidget.KEY_TRACKER_INDEX] =
                    HabitWidgetSyncManager.computeNextTrackerIndex(current, totalCount)
            }
        }

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
        HabitWidgetSyncManager.enqueueUpdate(context, excludeReceiver = SingleTrackerGlanceReceiver::class.java)

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
        HabitWidgetSyncManager.enqueueUpdate(context, excludeReceiver = SingleTrackerGlanceReceiver::class.java)

        if (updated != null) {
            TrackerNotificationHandler.showTrackerNotification(context, updated)
        }
    }
}

package com.eipna.centsation.habit.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.eipna.centsation.habit.ui.HabitTrackerActivity
import com.eipna.centsation.habit.util.HabitHeatmapUtil
import com.eipna.centsation.habit.util.StreakStats
import com.eipna.centsation.habit.util.TrackerDateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val HeatmapTrackerIdKey = ActionParameters.Key<Long>("heatmap_tracker_id")
val HeatmapDeltaKey = ActionParameters.Key<Int>("heatmap_delta")

class HabitHeatmapGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitHeatmapGlanceWidget()
}

class HabitHeatmapGlanceWidget : GlanceAppWidget() {

    companion object {
        val SIZE_COMPACT = DpSize(180.dp, 110.dp)
        val SIZE_MEDIUM = DpSize(260.dp, 130.dp)
        val SIZE_EXPANDED = DpSize(340.dp, 180.dp)
        const val PREFS_NAME = "habit_heatmap_widget_prefs"
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_COMPACT, SIZE_MEDIUM, SIZE_EXPANDED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TrackerRepository.getInstance(context)
        val allTrackers = repository.getTrackersWithProgress().firstOrNull() ?: emptyList()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val idHash = id.hashCode()
        val savedIndex = prefs.getInt("tracker_index_$idHash", 0)
        val is365Days = prefs.getBoolean("is_365_$idHash", false)

        val activeIndex = if (allTrackers.isNotEmpty()) {
            savedIndex.coerceIn(0, allTrackers.size - 1)
        } else {
            0
        }

        val currentItem = allTrackers.getOrNull(activeIndex)

        // Precompute streak & heatmap bitmap for active item
        val streakStats = currentItem?.let {
            HabitHeatmapUtil.calculateStreakStats(
                tracker = it.tracker,
                logs = it.logs,
                todayDate = TrackerDateUtil.getTodayDateString()
            )
        }

        provideContent {
            GlanceTheme {
                HeatmapWidgetContainer(
                    context = context,
                    item = currentItem,
                    stats = streakStats,
                    totalCount = allTrackers.size,
                    currentIndex = activeIndex,
                    is365Days = is365Days,
                    glanceIdHash = idHash
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun HeatmapWidgetContainer(
        context: Context,
        item: TrackerWithProgress?,
        stats: StreakStats?,
        totalCount: Int,
        currentIndex: Int,
        is365Days: Boolean,
        glanceIdHash: Int
    ) {
        val size = LocalSize.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(10.dp)
        ) {
            if (item == null || stats == null) {
                EmptyHeatmapState()
            } else if (size.width < 240.dp || size.height < 120.dp) {
                CompactHeatmapLayout(
                    context = context,
                    item = item,
                    stats = stats,
                    totalCount = totalCount,
                    currentIndex = currentIndex
                )
            } else if (size.width >= 320.dp && size.height >= 160.dp) {
                ExpandedHeatmapLayout(
                    context = context,
                    item = item,
                    stats = stats,
                    totalCount = totalCount,
                    currentIndex = currentIndex,
                    is365Days = is365Days,
                    glanceIdHash = glanceIdHash
                )
            } else {
                MediumHeatmapLayout(
                    context = context,
                    item = item,
                    stats = stats,
                    totalCount = totalCount,
                    currentIndex = currentIndex,
                    is365Days = is365Days,
                    glanceIdHash = glanceIdHash
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun EmptyHeatmapState() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity<HabitTrackerActivity>())
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🟩 Habit Heatmap",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Tap to set up habits and view streak activity",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.primary
                )
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun CompactHeatmapLayout(
        context: Context,
        item: TrackerWithProgress,
        stats: StreakStats,
        totalCount: Int,
        currentIndex: Int
    ) {
        val tracker = item.tracker
        val isDark = isSystemInDarkTheme(context)
        val density = context.resources.displayMetrics.density

        // Generate 35-day bitmap
        val (gridItems, numWeeks) = HabitHeatmapUtil.generateHeatmapGrid(
            tracker = tracker,
            logs = item.logs,
            is365Days = false
        )
        val bitmap = HabitHeatmapUtil.renderHeatmapBitmap(
            items = gridItems,
            numWeeks = numWeeks,
            is365Days = false,
            isDarkMode = isDark,
            widthPx = (160 * density).roundToInt(),
            heightPx = (52 * density).roundToInt(),
            density = density
        )

        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // Header Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
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

                if (stats.currentStreak > 0) {
                    Text(
                        text = "🔥 ${stats.currentStreak}d",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                }

                if (totalCount > 1) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "${currentIndex + 1}/$totalCount ❯",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = GlanceTheme.colors.outline
                        ),
                        modifier = GlanceModifier.clickable(actionRunCallback<HabitHeatmapCycleAction>())
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Heatmap Canvas Image
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<HabitTrackerActivity>())
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "30-Day Habit Heatmap Grid",
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Footer with quick log
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.isCompletedToday) "✅ Done" else "${item.todayCompletedReps}/${tracker.baseDailyTarget} ${tracker.unit}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = if (item.isCompletedToday) GlanceTheme.colors.primary else GlanceTheme.colors.outline
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Button(
                    text = "+1",
                    onClick = actionRunCallback<HabitHeatmapLogAction>(
                        actionParametersOf(
                            HeatmapTrackerIdKey to tracker.id,
                            HeatmapDeltaKey to 1
                        )
                    ),
                    modifier = GlanceModifier.height(28.dp)
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun MediumHeatmapLayout(
        context: Context,
        item: TrackerWithProgress,
        stats: StreakStats,
        totalCount: Int,
        currentIndex: Int,
        is365Days: Boolean,
        glanceIdHash: Int
    ) {
        val tracker = item.tracker
        val isDark = isSystemInDarkTheme(context)
        val density = context.resources.displayMetrics.density

        val (gridItems, numWeeks) = HabitHeatmapUtil.generateHeatmapGrid(
            tracker = tracker,
            logs = item.logs,
            is365Days = is365Days
        )

        val targetWidth = if (is365Days) 240 else 220
        val targetHeight = if (is365Days) 62 else 56
        val bitmap = HabitHeatmapUtil.renderHeatmapBitmap(
            items = gridItems,
            numWeeks = numWeeks,
            is365Days = is365Days,
            isDarkMode = isDark,
            widthPx = (targetWidth * density).roundToInt(),
            heightPx = (targetHeight * density).roundToInt(),
            density = density
        )

        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // Header Row: Title, Streak, Range Toggle, Switcher
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tracker.title,
                    maxLines = 1,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Text(
                    text = "🔥 ${stats.currentStreak}d",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.primary
                    )
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                // Range Toggle Button
                Text(
                    text = if (is365Days) "[365D]" else "[30D]",
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.primary
                    ),
                    modifier = GlanceModifier.clickable(actionRunCallback<HabitHeatmapToggleRangeAction>())
                )

                if (totalCount > 1) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "${currentIndex + 1}/$totalCount ❯",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.outline
                        ),
                        modifier = GlanceModifier.clickable(actionRunCallback<HabitHeatmapCycleAction>())
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Heatmap Graphic
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<HabitTrackerActivity>())
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "Contribution Heatmap Grid",
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Footer controls
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = if (item.isCompletedToday) {
                    "✅ Met (${item.todayCompletedReps}/${tracker.baseDailyTarget})"
                } else {
                    "⏳ ${item.remainingToday} ${tracker.unit} due"
                }

                Text(
                    text = statusText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = if (item.isCompletedToday) GlanceTheme.colors.primary else GlanceTheme.colors.outline
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Button(
                    text = "-1",
                    onClick = actionRunCallback<HabitHeatmapLogAction>(
                        actionParametersOf(
                            HeatmapTrackerIdKey to tracker.id,
                            HeatmapDeltaKey to -1
                        )
                    ),
                    modifier = GlanceModifier.height(28.dp)
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                Button(
                    text = "+1",
                    onClick = actionRunCallback<HabitHeatmapLogAction>(
                        actionParametersOf(
                            HeatmapTrackerIdKey to tracker.id,
                            HeatmapDeltaKey to 1
                        )
                    ),
                    modifier = GlanceModifier.height(28.dp)
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                Button(
                    text = "+${tracker.baseDailyTarget}",
                    onClick = actionRunCallback<HabitHeatmapLogAction>(
                        actionParametersOf(
                            HeatmapTrackerIdKey to tracker.id,
                            HeatmapDeltaKey to tracker.baseDailyTarget
                        )
                    ),
                    modifier = GlanceModifier.height(28.dp)
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ExpandedHeatmapLayout(
        context: Context,
        item: TrackerWithProgress,
        stats: StreakStats,
        totalCount: Int,
        currentIndex: Int,
        is365Days: Boolean,
        glanceIdHash: Int
    ) {
        val tracker = item.tracker
        val isDark = isSystemInDarkTheme(context)
        val density = context.resources.displayMetrics.density

        val (gridItems, numWeeks) = HabitHeatmapUtil.generateHeatmapGrid(
            tracker = tracker,
            logs = item.logs,
            is365Days = is365Days
        )

        val bitmap = HabitHeatmapUtil.renderHeatmapBitmap(
            items = gridItems,
            numWeeks = numWeeks,
            is365Days = is365Days,
            isDarkMode = isDark,
            widthPx = (310 * density).roundToInt(),
            heightPx = (72 * density).roundToInt(),
            density = density
        )

        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 ${tracker.title}",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                // Range Toggle
                Text(
                    text = if (is365Days) "View: 365 Days ▾" else "View: 35 Days ▾",
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.primary
                    ),
                    modifier = GlanceModifier.clickable(actionRunCallback<HabitHeatmapToggleRangeAction>())
                )

                if (totalCount > 1) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "Habit ${currentIndex + 1}/$totalCount ❯",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.outline
                        ),
                        modifier = GlanceModifier.clickable(actionRunCallback<HabitHeatmapCycleAction>())
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Subheader Stats Badges
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔥 ${stats.currentStreak}d Streak",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.primary
                    )
                )

                Spacer(modifier = GlanceModifier.width(10.dp))

                Text(
                    text = "🏆 ${stats.longestStreak}d Best",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )

                Spacer(modifier = GlanceModifier.width(10.dp))

                val pct = (stats.completionRate * 100f).roundToInt()
                Text(
                    text = "📊 $pct% Consistency",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.outline
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Heatmap Canvas Image
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<HabitTrackerActivity>())
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "Contribution Streak Heatmap Grid",
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Footer controls
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = if (item.isCompletedToday) {
                    "✅ Target Cleared Today (${item.todayCompletedReps}/${tracker.baseDailyTarget} ${tracker.unit})"
                } else if (item.accumulatedBacklogDebt > 0) {
                    "⚠️ ${item.remainingToday} ${tracker.unit} due (+${item.accumulatedBacklogDebt} debt)"
                } else {
                    "⏳ ${item.remainingToday} ${tracker.unit} remaining today"
                }

                Text(
                    text = statusText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = if (item.isCompletedToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Button(
                    text = "-1",
                    onClick = actionRunCallback<HabitHeatmapLogAction>(
                        actionParametersOf(
                            HeatmapTrackerIdKey to tracker.id,
                            HeatmapDeltaKey to -1
                        )
                    ),
                    modifier = GlanceModifier.height(30.dp)
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                Button(
                    text = "+1",
                    onClick = actionRunCallback<HabitHeatmapLogAction>(
                        actionParametersOf(
                            HeatmapTrackerIdKey to tracker.id,
                            HeatmapDeltaKey to 1
                        )
                    ),
                    modifier = GlanceModifier.height(30.dp)
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                Button(
                    text = "+${tracker.baseDailyTarget}",
                    onClick = actionRunCallback<HabitHeatmapLogAction>(
                        actionParametersOf(
                            HeatmapTrackerIdKey to tracker.id,
                            HeatmapDeltaKey to tracker.baseDailyTarget
                        )
                    ),
                    modifier = GlanceModifier.height(30.dp)
                )
            }
        }
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

class HabitHeatmapCycleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences(HabitHeatmapGlanceWidget.PREFS_NAME, Context.MODE_PRIVATE)
        val key = "tracker_index_${glanceId.hashCode()}"

        val repository = TrackerRepository.getInstance(context)
        val allTrackers = repository.getTrackersWithProgress().firstOrNull() ?: emptyList()
        if (allTrackers.isEmpty()) return

        val current = prefs.getInt(key, 0)
        val next = (current + 1) % allTrackers.size
        prefs.edit().putInt(key, next).apply()

        HabitHeatmapGlanceWidget().update(context, glanceId)
    }
}

class HabitHeatmapToggleRangeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences(HabitHeatmapGlanceWidget.PREFS_NAME, Context.MODE_PRIVATE)
        val key = "is_365_${glanceId.hashCode()}"
        val current = prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, !current).apply()

        HabitHeatmapGlanceWidget().update(context, glanceId)
    }
}

class HabitHeatmapLogAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val trackerId = parameters[HeatmapTrackerIdKey] ?: return
        val delta = parameters[HeatmapDeltaKey] ?: return

        val repository = TrackerRepository.getInstance(context)
        repository.logReps(trackerId, delta)
        updateAllHabitWidgets(context)
    }
}

fun updateAllHabitHeatmapWidgets(context: Context) {
    try {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val glanceComponent = android.content.ComponentName(context, HabitHeatmapGlanceReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(glanceComponent)
        if (widgetIds == null || widgetIds.isEmpty()) {
            return
        }
    } catch (e: Exception) {
        // Fallback
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            HabitHeatmapGlanceWidget().updateAll(context)
        } catch (e: Exception) {
            // Widget might not be placed
        }
    }
}

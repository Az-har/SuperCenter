package com.eipna.centsation.habit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.Color
import com.eipna.centsation.R
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.ui.HabitTrackerActivity
import com.eipna.centsation.habit.util.DayHeatmapItem
import com.eipna.centsation.habit.util.HabitHeatmapUtil
import com.eipna.centsation.habit.util.StreakStats
import com.eipna.centsation.habit.util.TrackerDateUtil
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.roundToInt

class HabitHeatmapGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitHeatmapGlanceWidget()
}

private data class HeatmapLayoutSpec(
    val targetWeeks: Int,
    val stackedBands: Boolean,
    val showFooterLegend: Boolean,
    val showMonthLabels: Boolean,
    val padDp: Float,
    val availWidthDp: Float,
    val availHeightDp: Float
)

class HabitHeatmapGlanceWidget : GlanceAppWidget() {

    companion object {
        val SIZE_COMPACT = DpSize(180.dp, 100.dp)
        val SIZE_MEDIUM = DpSize(250.dp, 115.dp)
        val SIZE_WIDE = DpSize(320.dp, 130.dp)
        val SIZE_TALL_MEDIUM = DpSize(250.dp, 200.dp)
        val SIZE_LARGE = DpSize(320.dp, 200.dp)
        val SIZE_FULL_PAGE = DpSize(320.dp, 280.dp)
        val KEY_TRACKER_INDEX = intPreferencesKey("heatmap_tracker_index")
        val KEY_IS_ONE_WEEK = booleanPreferencesKey("heatmap_is_one_week")
        val KEY_IS_365_DAYS = booleanPreferencesKey("heatmap_is_365_days")
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_COMPACT, SIZE_MEDIUM, SIZE_WIDE, SIZE_TALL_MEDIUM, SIZE_LARGE, SIZE_FULL_PAGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TrackerRepository.getInstance(context)
        val initialTrackers = repository.getTrackersWithProgress().firstOrNull() ?: emptyList()

        provideContent {
            val allTrackers by repository.getTrackersWithProgress().collectAsState(initial = initialTrackers)
            val prefs = currentState<Preferences>()
            val savedIndex = prefs[KEY_TRACKER_INDEX] ?: 0
            val isOneWeek = prefs[KEY_IS_ONE_WEEK] ?: false

            val activeIndex = if (allTrackers.isNotEmpty()) {
                savedIndex.coerceIn(0, allTrackers.size - 1)
            } else {
                0
            }

            val currentItem = allTrackers.getOrNull(activeIndex)
            val today = TrackerDateUtil.getTodayDateString()

            val streakStats = remember(currentItem, today) {
                currentItem?.let {
                    HabitHeatmapUtil.calculateStreakStats(
                        tracker = it.tracker,
                        logs = it.logs,
                        todayDate = today
                    )
                }
            }

            val gridItems = remember(currentItem, today, isOneWeek) {
                currentItem?.let {
                    HabitHeatmapUtil.generateHeatmapGridForWeeks(
                        tracker = it.tracker,
                        logs = it.logs,
                        numWeeks = if (isOneWeek) 1 else 5,
                        todayDate = today
                    ).first
                } ?: emptyList()
            }

            GlanceTheme(colors = HabitGlanceTheme.colorsFor(context)) {
                HeatmapWidgetContainer(
                    context = context,
                    item = currentItem,
                    stats = streakStats,
                    gridItems = gridItems,
                    totalCount = allTrackers.size,
                    currentIndex = activeIndex,
                    isOneWeek = isOneWeek
                )
            }
        }
    }

    private fun computeLayoutSpec(size: DpSize, isOneWeek: Boolean): HeatmapLayoutSpec {
        val padDp = if (size.width < 220.dp) 8f else 10f
        val showFooterLegend = size.height >= 120.dp && !isOneWeek
        val headerHeightDp = 34f
        val footerHeightDp = if (showFooterLegend) 18f else 0f
        val spacingDp = if (showFooterLegend) 8f else 4f

        val availWidthDp = (size.width.value - padDp * 2f).coerceAtLeast(130f)
        val availHeightDp = (size.height.value - padDp * 2f - headerHeightDp - footerHeightDp - spacingDp).coerceAtLeast(62f)

        val targetWeeks = if (isOneWeek) 1 else 5

        return HeatmapLayoutSpec(
            targetWeeks = targetWeeks,
            stackedBands = false,
            showFooterLegend = showFooterLegend,
            showMonthLabels = !isOneWeek && size.width >= 240.dp,
            padDp = padDp,
            availWidthDp = availWidthDp,
            availHeightDp = availHeightDp
        )
    }

    @Composable
    private fun HeatmapWidgetContainer(
        context: Context,
        item: TrackerWithProgress?,
        stats: StreakStats?,
        gridItems: List<DayHeatmapItem>,
        totalCount: Int,
        currentIndex: Int,
        isOneWeek: Boolean
    ) {
        val size = LocalSize.current
        val padDp = if (size.width < 220.dp) 8.dp else 10.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(padDp)
        ) {
            if (item == null || stats == null) {
                EmptyHeatmapState()
            } else {
                val spec = computeLayoutSpec(size, isOneWeek)
                DedicatedHeatmapLayout(
                    context = context,
                    item = item,
                    stats = stats,
                    gridItems = gridItems,
                    spec = spec,
                    totalCount = totalCount,
                    currentIndex = currentIndex,
                    isOneWeek = isOneWeek
                )
            }
        }
    }

    @Composable
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
                text = "Habit Activity Heatmap",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Tap to create a habit and track your contribution grid",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.primary
                )
            )
        }
    }

    @Composable
    private fun DedicatedHeatmapLayout(
        context: Context,
        item: TrackerWithProgress,
        stats: StreakStats,
        gridItems: List<DayHeatmapItem>,
        spec: HeatmapLayoutSpec,
        totalCount: Int,
        currentIndex: Int,
        isOneWeek: Boolean
    ) {
        val tracker = item.tracker
        val isDark = HabitGlanceTheme.resolveDarkMode(context)
        val renderDensity = context.resources.displayMetrics.density.coerceIn(1.0f, 1.5f)
        val isSurplus = item.isSurplusToday

        val bitmap = remember(gridItems, spec, isDark, renderDensity) {
            HabitHeatmapUtil.renderFullBleedHeatmapBitmap(
                items = gridItems,
                numWeeks = spec.targetWeeks,
                showMonthLabels = spec.showMonthLabels,
                stackedBands = spec.stackedBands,
                isDarkMode = isDark,
                widthPx = (spec.availWidthDp * renderDensity).roundToInt(),
                heightPx = (spec.availHeightDp * renderDensity).roundToInt(),
                density = renderDensity
            )
        }

        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // Slim GitHub-style Header Bar
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(34.dp),
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
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity<HabitTrackerActivity>())
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                Text(
                    text = "${item.todayCompletedReps}/${item.effectiveTargetToday}",
                    maxLines = 1,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isSurplus) {
                            ColorProvider(if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                        } else if (item.todayCompletedReps > 0) {
                            GlanceTheme.colors.primary
                        } else {
                            GlanceTheme.colors.onSurfaceVariant
                        }
                    )
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(
                            if (isSurplus) R.drawable.ic_fire_blue
                            else if (stats.currentStreak > 0) R.drawable.ic_fire_amber
                            else R.drawable.ic_fire_muted
                        ),
                        contentDescription = "Streak Fire",
                        modifier = GlanceModifier.size(13.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        text = "${stats.currentStreak}d",
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isSurplus) {
                                ColorProvider(if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                            } else if (stats.currentStreak > 0) {
                                GlanceTheme.colors.primary
                            } else {
                                GlanceTheme.colors.onSurfaceVariant
                            }
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Box(
                    modifier = GlanceModifier
                        .height(32.dp)
                        .cornerRadius(10.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .clickable(actionRunCallback<HabitHeatmapToggleRangeAction>())
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isOneWeek) "1W" else "1M",
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onPrimaryContainer
                        )
                    )
                }

                if (totalCount > 1) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .height(32.dp)
                            .cornerRadius(10.dp)
                            .background(GlanceTheme.colors.secondaryContainer)
                            .clickable(actionRunCallback<HabitHeatmapCycleAction>())
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${currentIndex + 1}/$totalCount ❯",
                            maxLines = 1,
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = GlanceTheme.colors.onSecondaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Dedicated Full-Bleed Heatmap Matrix taking all remaining widget area
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionStartActivity<HabitTrackerActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "Habit Contribution Heatmap",
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxSize()
                )
            }

            if (spec.showFooterLegend) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                HeatmapFooterLegend(
                    completedDays = stats.completedDays,
                    totalLogged = stats.totalLogged,
                    unit = tracker.unit,
                    isDark = isDark
                )
            }
        }
    }

    @Composable
    private fun HeatmapFooterLegend(
        completedDays: Int,
        totalLogged: Int,
        unit: String,
        isDark: Boolean
    ) {
        val swatchColors = if (isDark) {
            listOf(
                Color(0xFF1E2634),
                Color(0xFF00E676),
                Color(0xFF00E676),
                Color(0xFF38BDF8)
            )
        } else {
            listOf(
                Color(0xFFF1F5F9),
                Color(0xFF10B981),
                Color(0xFF10B981),
                Color(0xFF0284C7)
            )
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val goalsLabel = if (completedDays == 1) "goal" else "goals"
            Text(
                text = "$completedDays $goalsLabel met • $totalLogged $unit total",
                maxLines = 1,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Less",
                    style = TextStyle(fontSize = 9.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(end = 3.dp)
                )

                swatchColors.forEach { swatch ->
                    Box(modifier = GlanceModifier.padding(end = 2.dp)) {
                        Box(
                            modifier = GlanceModifier
                                .size(7.dp)
                                .cornerRadius(2.dp)
                                .background(ColorProvider(swatch))
                        ) {}
                    }
                }

                Text(
                    text = "More",
                    style = TextStyle(fontSize = 9.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(start = 1.dp)
                )
            }
        }
    }
}

class HabitHeatmapCycleAction : ActionCallback {
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
                val current = this[HabitHeatmapGlanceWidget.KEY_TRACKER_INDEX] ?: 0
                this[HabitHeatmapGlanceWidget.KEY_TRACKER_INDEX] =
                    HabitWidgetSyncManager.computeNextTrackerIndex(current, totalCount)
            }
        }

        HabitHeatmapGlanceWidget().update(context, glanceId)
    }
}

class HabitHeatmapToggleRangeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val current = this[HabitHeatmapGlanceWidget.KEY_IS_ONE_WEEK] ?: false
                this[HabitHeatmapGlanceWidget.KEY_IS_ONE_WEEK] = !current
            }
        }

        HabitHeatmapGlanceWidget().update(context, glanceId)
    }
}

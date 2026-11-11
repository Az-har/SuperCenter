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
            val is365Days = prefs[KEY_IS_365_DAYS] ?: false

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

            val fullYearGrid = remember(currentItem, today) {
                currentItem?.let {
                    HabitHeatmapUtil.generateHeatmapGridForWeeks(
                        tracker = it.tracker,
                        logs = it.logs,
                        numWeeks = 53,
                        todayDate = today
                    ).first
                } ?: emptyList()
            }

            GlanceTheme(colors = HabitGlanceTheme.colorsFor(context)) {
                HeatmapWidgetContainer(
                    context = context,
                    item = currentItem,
                    stats = streakStats,
                    fullYearGrid = fullYearGrid,
                    totalCount = allTrackers.size,
                    currentIndex = activeIndex,
                    is365Days = is365Days
                )
            }
        }
    }

    private fun computeLayoutSpec(size: DpSize, is365Days: Boolean): HeatmapLayoutSpec {
        val padDp = if (size.width < 220.dp) 8f else 10f
        val showFooterLegend = size.height >= 120.dp
        val headerHeightDp = 34f
        val footerHeightDp = if (showFooterLegend) 18f else 0f
        val spacingDp = if (showFooterLegend) 8f else 4f

        val availWidthDp = (size.width.value - padDp * 2f).coerceAtLeast(130f)
        val availHeightDp = (size.height.value - padDp * 2f - headerHeightDp - footerHeightDp - spacingDp).coerceAtLeast(62f)

        val isTall = availHeightDp >= 135f || (is365Days && availHeightDp >= 95f)
        val stackedBands = isTall

        val targetWeeks = if (is365Days) {
            53
        } else if (stackedBands) {
            val singleBandH = (availHeightDp - 8f) / 2f
            val pitch = ((singleBandH - 12f) / 7f).coerceAtLeast(5f)
            val colsPerBand = ((availWidthDp - 14f) / pitch).roundToInt().coerceIn(10, 26)
            (colsPerBand * 2).coerceIn(20, 52)
        } else {
            val showMonths = size.width >= 240.dp
            val headerReserve = if (showMonths) 12f else 2f
            val pitch = ((availHeightDp - headerReserve) / 7f).coerceAtLeast(5f)
            ((availWidthDp - 14f) / pitch).roundToInt().coerceIn(8, 28)
        }

        val showMonthLabels = is365Days || size.width >= 240.dp
        return HeatmapLayoutSpec(
            targetWeeks = targetWeeks,
            stackedBands = stackedBands,
            showFooterLegend = showFooterLegend,
            showMonthLabels = showMonthLabels,
            padDp = padDp,
            availWidthDp = availWidthDp,
            availHeightDp = availHeightDp
        )
    }

    /**
     * Slices the trailing [targetWeeks] from the 53-week master grid and re-indexes weekIndex cleanly from 0.
     */
    private fun sliceWeeks(fullYearGrid: List<DayHeatmapItem>, targetWeeks: Int): List<DayHeatmapItem> {
        val clampedWeeks = targetWeeks.coerceIn(1, 53)
        val startWeek = 53 - clampedWeeks
        if (startWeek == 0) return fullYearGrid
        return fullYearGrid
            .filter { it.weekIndex >= startWeek }
            .map { it.copy(weekIndex = it.weekIndex - startWeek) }
    }

    @Composable
    private fun HeatmapWidgetContainer(
        context: Context,
        item: TrackerWithProgress?,
        stats: StreakStats?,
        fullYearGrid: List<DayHeatmapItem>,
        totalCount: Int,
        currentIndex: Int,
        is365Days: Boolean
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
                val spec = computeLayoutSpec(size, is365Days)
                val gridSlice = sliceWeeks(fullYearGrid, spec.targetWeeks)
                DedicatedHeatmapLayout(
                    context = context,
                    item = item,
                    stats = stats,
                    gridItems = gridSlice,
                    spec = spec,
                    totalCount = totalCount,
                    currentIndex = currentIndex,
                    is365Days = is365Days
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
        is365Days: Boolean
    ) {
        val tracker = item.tracker
        val isDark = HabitGlanceTheme.resolveDarkMode(context)
        val renderDensity = context.resources.displayMetrics.density.coerceIn(1.0f, 1.5f)

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
                        color = if (item.todayCompletedReps > 0) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
                    )
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                Text(
                    text = "🔥${stats.currentStreak}d",
                    maxLines = 1,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (stats.currentStreak > 0) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
                    )
                )

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
                        text = if (is365Days) "365D" else "${spec.targetWeeks}W",
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
                Color(0xFF218C54),
                Color(0xFF2CB55C),
                Color(0xFF00E676),
                Color(0xFF69F0AE)
            )
        } else {
            listOf(
                Color(0xFFF1F5F9),
                Color(0xFF6EE7B7),
                Color(0xFF34D399),
                Color(0xFF10B981),
                Color(0xFF047857)
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
                val current = this[HabitHeatmapGlanceWidget.KEY_IS_365_DAYS] ?: false
                this[HabitHeatmapGlanceWidget.KEY_IS_365_DAYS] = !current
            }
        }

        HabitHeatmapGlanceWidget().update(context, glanceId)
    }
}

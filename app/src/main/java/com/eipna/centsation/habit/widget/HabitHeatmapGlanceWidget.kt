package com.eipna.centsation.habit.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.eipna.centsation.R
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.ui.HabitTrackerActivity
import com.eipna.centsation.habit.util.HabitHeatmapUtil
import com.eipna.centsation.habit.util.TrackerDateUtil
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.roundToInt

class HabitHeatmapGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitHeatmapGlanceWidget()
}

private data class HeatmapLayoutSpec(
    val targetWeeks: Int,
    val showFooterLegend: Boolean,
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
            val isOneWeek = prefs[KEY_IS_ONE_WEEK] ?: false
            val trackerIndexPref = prefs[KEY_TRACKER_INDEX] ?: 0

            val today = TrackerDateUtil.getTodayDateString()

            GlanceTheme(colors = HabitGlanceTheme.colorsFor(context)) {
                HeatmapWidgetRoot(
                    context = context,
                    allTrackers = allTrackers,
                    trackerIndexPref = trackerIndexPref,
                    isOneWeek = isOneWeek,
                    today = today
                )
            }
        }
    }

    private fun computeLayoutSpec(size: DpSize, isOneWeek: Boolean): HeatmapLayoutSpec {
        val padDp = if (size.width < 220.dp) 8f else 10f
        val showFooterLegend = size.height >= 120.dp
        val headerHeightDp = 34f
        val footerHeightDp = if (showFooterLegend) 18f else 0f
        val spacingDp = if (showFooterLegend) 6f else 4f

        val availWidthDp = (size.width.value - padDp * 2f).coerceAtLeast(130f)
        val availHeightDp = (size.height.value - padDp * 2f - headerHeightDp - footerHeightDp - spacingDp).coerceAtLeast(60f)

        val targetWeeks = if (isOneWeek) 1 else 5

        return HeatmapLayoutSpec(
            targetWeeks = targetWeeks,
            showFooterLegend = showFooterLegend,
            padDp = padDp,
            availWidthDp = availWidthDp,
            availHeightDp = availHeightDp
        )
    }

    @Composable
    private fun HeatmapWidgetRoot(
        context: Context,
        allTrackers: List<TrackerWithProgress>,
        trackerIndexPref: Int,
        isOneWeek: Boolean,
        today: String
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
            if (allTrackers.isEmpty()) {
                EmptyHeatmapState()
                return@Box
            }

            val totalOptions = allTrackers.size + 1
            val activeIndex = ((trackerIndexPref % totalOptions) + totalOptions) % totalOptions
            val isCombinedView = (activeIndex == allTrackers.size)

            val spec = computeLayoutSpec(size, isOneWeek)
            val isDark = HabitGlanceTheme.resolveDarkMode(context)
            val renderDensity = context.resources.displayMetrics.density.coerceIn(1.0f, 1.5f)
            val widthPx = (spec.availWidthDp * renderDensity).roundToInt()
            val heightPx = (spec.availHeightDp * renderDensity).roundToInt()

            if (isCombinedView) {
                val combinedStats = remember(allTrackers, today) {
                    HabitHeatmapUtil.calculateCombinedStreakStats(allTrackers, today)
                }
                val combinedItems = remember(allTrackers, today, isOneWeek) {
                    HabitHeatmapUtil.generateCombinedHeatmapGridForWeeks(allTrackers, spec.targetWeeks, today).first
                }
                val dataHash = remember(combinedItems) {
                    combinedItems.joinToString("|") { "${it.dateString}-${it.level}-${it.trackersCompleted}-${it.isToday}" }.hashCode()
                }
                val bitmap = remember(dataHash, spec.targetWeeks, widthPx, heightPx, isDark) {
                    HabitHeatmapUtil.renderCombinedHeatmapBitmap(
                        items = combinedItems,
                        numWeeks = spec.targetWeeks,
                        showMonthLabels = !isOneWeek,
                        isDarkMode = isDark,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        density = renderDensity
                    )
                }

                HeatmapScaffold(
                    title = "All Habits",
                    streakCount = combinedStats.currentStreak,
                    isSurplus = combinedStats.isSurplusToday,
                    cycleLabel = "All (${allTrackers.size}) ❯",
                    totalOptions = totalOptions,
                    isOneWeek = isOneWeek,
                    bitmap = bitmap,
                    footerStatus = "Today: ${combinedStats.trackersCompletedToday}/${combinedStats.totalTrackers} habits met",
                    showFooterLegend = spec.showFooterLegend,
                    isDark = isDark
                )
            } else {
                val current = allTrackers[activeIndex]
                val tracker = current.tracker
                val streakStats = remember(current, today) {
                    HabitHeatmapUtil.calculateStreakStats(tracker, current.logs, today)
                }
                val singleItems = remember(current, today, isOneWeek) {
                    HabitHeatmapUtil.generateHeatmapGridForWeeks(tracker, current.logs, spec.targetWeeks, today).first
                }
                val dataHash = remember(singleItems) {
                    singleItems.joinToString("|") { "${it.dateString}-${it.level}-${it.completedCount}-${it.isToday}" }.hashCode()
                }
                val bitmap = remember(dataHash, spec.targetWeeks, widthPx, heightPx, isDark) {
                    HabitHeatmapUtil.renderFullBleedHeatmapBitmap(
                        items = singleItems,
                        numWeeks = spec.targetWeeks,
                        showMonthLabels = !isOneWeek,
                        stackedBands = false,
                        isDarkMode = isDark,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        density = renderDensity
                    )
                }

                val target = tracker.baseDailyTarget.coerceAtLeast(1)
                val reps = current.todayCompletedReps
                val footerText = when {
                    reps > target -> "⚡ $reps ${tracker.unit} (+${reps - target} surplus!)"
                    reps == target -> "✓ $target ${tracker.unit} completed today"
                    reps > 0 -> "Today: $reps / $target ${tracker.unit} (${reps * 100 / target}%)"
                    else -> "Today: 0 / $target ${tracker.unit} due"
                }

                HeatmapScaffold(
                    title = tracker.title,
                    streakCount = streakStats.currentStreak,
                    isSurplus = streakStats.isSurplusToday,
                    cycleLabel = "${activeIndex + 1}/${allTrackers.size} ❯",
                    totalOptions = totalOptions,
                    isOneWeek = isOneWeek,
                    bitmap = bitmap,
                    footerStatus = footerText,
                    showFooterLegend = spec.showFooterLegend,
                    isDark = isDark
                )
            }
        }
    }

    @Composable
    private fun HeatmapScaffold(
        title: String,
        streakCount: Int,
        isSurplus: Boolean,
        cycleLabel: String,
        totalOptions: Int,
        isOneWeek: Boolean,
        bitmap: Bitmap,
        footerStatus: String,
        showFooterLegend: Boolean,
        isDark: Boolean
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = title,
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

                Spacer(modifier = GlanceModifier.width(4.dp))

                // Streak Flame + Streak Count
                val flameRes = when {
                    isSurplus -> R.drawable.ic_fire_blue
                    streakCount > 0 -> R.drawable.ic_fire_amber
                    else -> R.drawable.ic_fire_muted
                }
                val streakTextColor = when {
                    isSurplus -> ColorProvider(Color(0xFF38BDF8))
                    streakCount > 0 -> ColorProvider(if (isDark) Color(0xFF00E676) else Color(0xFF10B981))
                    else -> GlanceTheme.colors.onSurfaceVariant
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.clickable(actionStartActivity<HabitTrackerActivity>())
                ) {
                    Image(
                        provider = ImageProvider(flameRes),
                        contentDescription = "Streak Flame",
                        modifier = GlanceModifier.size(15.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        text = "${streakCount}d",
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = streakTextColor
                        )
                    )
                }

                // Cycle Pill (if multiple options exist)
                if (totalOptions > 1) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .height(28.dp)
                            .cornerRadius(8.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionRunCallback<HabitHeatmapCycleAction>(
                                actionParametersOf(HabitHeatmapCycleAction.TotalOptionsKey to totalOptions)
                            ))
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cycleLabel,
                            maxLines = 1,
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                    }
                }

                // Range Toggle (1W / 1M)
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .height(28.dp)
                        .cornerRadius(8.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .clickable(actionRunCallback<HabitHeatmapToggleRangeAction>())
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isOneWeek) "1W" else "1M",
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = GlanceTheme.colors.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Heatmap Matrix Canvas Bitmap
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

            // Footer (Status + Legend)
            if (showFooterLegend) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                HeatmapFooter(footerStatus = footerStatus, isDark = isDark)
            }
        }
    }

    @Composable
    private fun HeatmapFooter(footerStatus: String, isDark: Boolean) {
        val swatches = if (isDark) {
            listOf(
                Color(0xFF1E2634), // Empty
                Color(0xFF00E676), // Partial Outline
                Color(0xFF00E676), // Completed Solid
                Color(0xFF38BDF8)  // Surplus Blue
            )
        } else {
            listOf(
                Color(0xFFF1F5F9), // Empty
                Color(0xFF10B981), // Partial Outline
                Color(0xFF10B981), // Completed Solid
                Color(0xFF0284C7)  // Surplus Blue
            )
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = footerStatus,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Less ",
                    style = TextStyle(fontSize = 9.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(end = 2.dp)
                )

                swatches.forEachIndexed { idx, swatch ->
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
}

class HabitHeatmapCycleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val totalOptions = parameters[TotalOptionsKey] ?: 1
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val current = this[HabitHeatmapGlanceWidget.KEY_TRACKER_INDEX] ?: 0
                this[HabitHeatmapGlanceWidget.KEY_TRACKER_INDEX] = (current + 1) % totalOptions
            }
        }
        HabitHeatmapGlanceWidget().update(context, glanceId)
    }

    companion object {
        val TotalOptionsKey = ActionParameters.Key<Int>("total_options")
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

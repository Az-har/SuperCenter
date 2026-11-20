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
            val trackerIndexPref = prefs[KEY_TRACKER_INDEX] ?: 0

            val today = TrackerDateUtil.getTodayDateString()

            GlanceTheme(colors = HabitGlanceTheme.colorsFor(context)) {
                HeatmapWidgetRoot(
                    context = context,
                    allTrackers = allTrackers,
                    trackerIndexPref = trackerIndexPref,
                    today = today
                )
            }
        }
    }

    private fun computeLayoutSpec(size: DpSize): HeatmapLayoutSpec {
        val padDp = if (size.width < 220.dp) 6f else 8f
        val headerHeightDp = 30f
        val spacingDp = 3f

        val availWidthDp = (size.width.value - padDp * 2f).coerceAtLeast(160f)
        val availHeightDp = (size.height.value - padDp * 2f - headerHeightDp - spacingDp).coerceAtLeast(60f)

        return HeatmapLayoutSpec(
            targetWeeks = 53, // Always 53 weeks (full 1 year GitHub contribution graph!)
            showFooterLegend = true,
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
        today: String
    ) {
        val size = LocalSize.current
        val padDp = if (size.width < 220.dp) 6.dp else 8.dp
        val isDark = HabitGlanceTheme.resolveDarkMode(context)

        val widgetBg = if (isDark) ColorProvider(Color(0xFF0D1117)) else GlanceTheme.colors.surface

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(widgetBg)
                .padding(padDp)
        ) {
            if (allTrackers.isEmpty()) {
                EmptyHeatmapState()
                return@Box
            }

            val totalOptions = allTrackers.size + 1
            val activeIndex = ((trackerIndexPref % totalOptions) + totalOptions) % totalOptions
            val isCombinedView = (activeIndex == allTrackers.size)

            val spec = computeLayoutSpec(size)
            val renderDensity = context.resources.displayMetrics.density.coerceIn(1.5f, 2.5f)
            val widthPx = (spec.availWidthDp * renderDensity).roundToInt()
            val heightPx = (spec.availHeightDp * renderDensity).roundToInt()

            if (isCombinedView) {
                val combinedStats = remember(allTrackers, today) {
                    HabitHeatmapUtil.calculateCombinedStreakStats(allTrackers, today)
                }
                val combinedItems = remember(allTrackers, today) {
                    HabitHeatmapUtil.generateCombinedHeatmapGridForWeeks(allTrackers, spec.targetWeeks, today).first
                }
                val footerText = "Today: ${combinedStats.trackersCompletedToday}/${combinedStats.totalTrackers} habits met"
                val dataHash = remember(combinedItems, footerText) {
                    "${combinedItems.joinToString("|") { "${it.dateString}-${it.level}-${it.trackersCompleted}-${it.isToday}" }}#$footerText".hashCode()
                }
                val bitmap = remember(dataHash, spec.targetWeeks, widthPx, heightPx, isDark) {
                    HabitHeatmapUtil.renderCombinedHeatmapBitmap(
                        items = combinedItems,
                        numWeeks = spec.targetWeeks,
                        showMonthLabels = true,
                        isDarkMode = isDark,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        density = renderDensity,
                        footerStatus = footerText
                    )
                }

                HeatmapScaffold(
                    title = "All Habits",
                    streakCount = combinedStats.currentStreak,
                    isSurplus = combinedStats.isSurplusToday,
                    cycleLabel = "All (${allTrackers.size}) ❯",
                    totalOptions = totalOptions,
                    bitmap = bitmap,
                    isDark = isDark
                )
            } else {
                val current = allTrackers[activeIndex]
                val tracker = current.tracker
                val streakStats = remember(current, today) {
                    HabitHeatmapUtil.calculateStreakStats(tracker, current.logs, today)
                }
                val singleItems = remember(current, today) {
                    HabitHeatmapUtil.generateHeatmapGridForWeeks(tracker, current.logs, spec.targetWeeks, today).first
                }

                val target = tracker.baseDailyTarget.coerceAtLeast(1)
                val reps = current.todayCompletedReps
                val footerText = when {
                    reps > target -> "⚡ $reps ${tracker.unit} (+${reps - target} surplus!)"
                    reps == target -> "✓ $target ${tracker.unit} completed today"
                    reps > 0 -> "Today: $reps / $target ${tracker.unit}"
                    else -> "Today: 0 / $target ${tracker.unit}"
                }
                val dataHash = remember(singleItems, footerText) {
                    "${singleItems.joinToString("|") { "${it.dateString}-${it.level}-${it.completedCount}-${it.isToday}" }}#$footerText".hashCode()
                }
                val bitmap = remember(dataHash, spec.targetWeeks, widthPx, heightPx, isDark) {
                    HabitHeatmapUtil.renderFullBleedHeatmapBitmap(
                        items = singleItems,
                        numWeeks = spec.targetWeeks,
                        showMonthLabels = true,
                        stackedBands = false,
                        isDarkMode = isDark,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        density = renderDensity,
                        footerStatus = footerText
                    )
                }

                HeatmapScaffold(
                    title = tracker.title,
                    streakCount = streakStats.currentStreak,
                    isSurplus = streakStats.isSurplusToday,
                    cycleLabel = "${activeIndex + 1}/${allTrackers.size} ❯",
                    totalOptions = totalOptions,
                    bitmap = bitmap,
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
        bitmap: Bitmap,
        isDark: Boolean
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                val titleColor = if (isDark) ColorProvider(Color(0xFFF0F6FC)) else GlanceTheme.colors.onSurface
                Text(
                    text = title,
                    maxLines = 1,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = titleColor
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
                    streakCount > 0 -> ColorProvider(if (isDark) Color(0xFF26A641) else Color(0xFF10B981))
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
                    val pillBg = if (isDark) ColorProvider(Color(0xFF21262D)) else GlanceTheme.colors.surfaceVariant
                    val pillTextColor = if (isDark) ColorProvider(Color(0xFF8B949E)) else GlanceTheme.colors.onSurfaceVariant
                    Box(
                        modifier = GlanceModifier
                            .height(26.dp)
                            .cornerRadius(6.dp)
                            .background(pillBg)
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
                                color = pillTextColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Heatmap Matrix Canvas Bitmap (fills entire remaining space)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionStartActivity<HabitTrackerActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "GitHub Contribution Heatmap",
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxSize()
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

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
        val KEY_IS_ONE_MONTH = booleanPreferencesKey("heatmap_is_one_month")
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TrackerRepository.getInstance(context)
        val initialTrackers = repository.getTrackersWithProgress().firstOrNull() ?: emptyList()

        provideContent {
            val allTrackers by repository.getTrackersWithProgress().collectAsState(initial = initialTrackers)
            val prefs = currentState<Preferences>()
            val isOneMonth = prefs[KEY_IS_ONE_MONTH] ?: false
            val today = TrackerDateUtil.getTodayDateString()

            GlanceTheme(colors = HabitGlanceTheme.colorsFor(context)) {
                HeatmapWidgetRoot(
                    context = context,
                    allTrackers = allTrackers,
                    isOneMonth = isOneMonth,
                    today = today
                )
            }
        }
    }

    private fun computeLayoutSpec(size: DpSize, targetWeeks: Int): HeatmapLayoutSpec {
        val padDp = if (size.width < 220.dp) 6f else 8f
        val headerHeightDp = 30f
        val spacingDp = 2f

        val availWidthDp = (size.width.value - padDp * 2f).coerceAtLeast(140f)
        val availHeightDp = (size.height.value - padDp * 2f - headerHeightDp - spacingDp).coerceAtLeast(60f)

        return HeatmapLayoutSpec(
            targetWeeks = targetWeeks,
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
        isOneMonth: Boolean,
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

            val targetWeeks = if (isOneMonth) 5 else 1
            val spec = computeLayoutSpec(size, targetWeeks)
            val renderDensity = context.resources.displayMetrics.density.coerceIn(1.5f, 2.5f)
            val widthPx = (spec.availWidthDp * renderDensity).roundToInt()
            val heightPx = (spec.availHeightDp * renderDensity).roundToInt()

            val combinedStats = remember(allTrackers, today) {
                HabitHeatmapUtil.calculateCombinedStreakStats(allTrackers, today)
            }
            val combinedItems = remember(allTrackers, today, targetWeeks) {
                HabitHeatmapUtil.generateCombinedHeatmapGridForWeeks(allTrackers, targetWeeks, today).first
            }
            val footerText = "Today: ${combinedStats.trackersCompletedToday}/${combinedStats.totalTrackers} habits met"
            val dataHash = remember(combinedItems, footerText, isOneMonth, widthPx, heightPx) {
                "${combinedItems.joinToString("|") { "${it.dateString}-${it.level}-${it.trackersCompleted}-${it.isToday}" }}#$footerText#$isOneMonth#$widthPx#$heightPx".hashCode()
            }
            val bitmap = remember(dataHash, targetWeeks, widthPx, heightPx, isDark) {
                if (isOneMonth) {
                    HabitHeatmapUtil.renderCombinedOneMonthBitmap(
                        items = combinedItems,
                        isDarkMode = isDark,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        density = renderDensity,
                        footerStatus = footerText
                    )
                } else {
                    HabitHeatmapUtil.renderCombinedOneWeekBitmap(
                        items = combinedItems,
                        isDarkMode = isDark,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        density = renderDensity,
                        footerStatus = footerText
                    )
                }
            }

            HeatmapScaffold(
                title = "All Habits",
                streakCount = combinedStats.currentStreak,
                isSurplus = combinedStats.isSurplusToday,
                isOneMonth = isOneMonth,
                bitmap = bitmap,
                isDark = isDark
            )
        }
    }

    @Composable
    private fun HeatmapScaffold(
        title: String,
        streakCount: Int,
        isSurplus: Boolean,
        isOneMonth: Boolean,
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

                Spacer(modifier = GlanceModifier.width(6.dp))

                // 1W / 1M Toggle Pill
                val toggleLabel = if (isOneMonth) "1M" else "1W"
                val pillBg = if (isDark) ColorProvider(Color(0xFF21262D)) else GlanceTheme.colors.surfaceVariant
                val pillTextColor = if (isDark) ColorProvider(Color(0xFF8B949E)) else GlanceTheme.colors.onSurfaceVariant
                Box(
                    modifier = GlanceModifier
                        .height(26.dp)
                        .cornerRadius(6.dp)
                        .background(pillBg)
                        .clickable(actionRunCallback<HabitHeatmapToggleRangeAction>())
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = toggleLabel,
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = pillTextColor
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Heatmap Canvas Image
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionStartActivity<HabitTrackerActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "Habit Activity Heatmap",
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

class HabitHeatmapToggleRangeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val current = this[HabitHeatmapGlanceWidget.KEY_IS_ONE_MONTH] ?: false
                this[HabitHeatmapGlanceWidget.KEY_IS_ONE_MONTH] = !current
            }
        }
        HabitHeatmapGlanceWidget().update(context, glanceId)
    }
}

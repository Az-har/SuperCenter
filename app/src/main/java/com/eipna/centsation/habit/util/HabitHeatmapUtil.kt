package com.eipna.centsation.habit.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.entity.TrackerLogEntity
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class DayHeatmapItem(
    val dateString: String,
    val weekIndex: Int,
    val dayOfWeek: Int, // 0 = Sun, 1 = Mon, ..., 6 = Sat
    val completedCount: Int,
    val target: Int,
    val level: Int, // 0: None, 1: 1..<target (outline), 2: ==target (green), 3: >target (blue)
    val isToday: Boolean,
    val isFuture: Boolean
)

data class StreakStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalLogged: Int,
    val completedDays: Int,
    val totalDays: Int,
    val completionRate: Float,
    val isSurplusToday: Boolean = false
)

/**
 * Represents aggregated heatmap data across all trackers for a single day.
 */
data class CombinedDayHeatmapItem(
    val dateString: String,
    val weekIndex: Int,
    val dayOfWeek: Int,
    val totalCompletedCount: Int,
    val totalTarget: Int,
    val trackersCompleted: Int,      // Number of trackers that met their goal
    val totalTrackers: Int,          // Total active trackers for that day
    val level: Int,                  // 0: None, 1: Some activity, 2: All goals met, 3: Surplus
    val isToday: Boolean,
    val isFuture: Boolean
)

data class CombinedStreakStats(
    val totalGoalsMet: Int,
    val totalLogged: Int,
    val totalTrackers: Int,
    val activeTrackersToday: Int,
    val trackersCompletedToday: Int,
    val isSurplusToday: Boolean = false,
    val currentStreak: Int = 0
)

object HabitHeatmapUtil {

    private val MONTH_NAMES = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    /**
     * Generates a combined heatmap grid aggregating all trackers.
     * Each cell shows the total completion level across all active trackers for that day.
     */
    fun generateCombinedHeatmapGridForWeeks(
        trackersWithProgress: List<com.eipna.centsation.habit.data.model.TrackerWithProgress>,
        numWeeks: Int,
        todayDate: String = TrackerDateUtil.getTodayDateString()
    ): Pair<List<CombinedDayHeatmapItem>, Int> {
        if (trackersWithProgress.isEmpty()) {
            return Pair(emptyList(), 0)
        }

        val safeWeeks = numWeeks.coerceIn(1, 54)
        val todayCal = parseDate(todayDate)
        val todayDow = todayCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val startCal = (todayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -todayDow - (safeWeeks - 1) * 7)
        }

        // Build a map of date -> list of (count, target) for all trackers
        val dateToTrackerData = mutableMapOf<String, MutableList<Pair<Int, Int>>>()
        for (twp in trackersWithProgress) {
            val target = twp.tracker.baseDailyTarget.coerceAtLeast(1)
            for (log in twp.logs) {
                dateToTrackerData.getOrPut(log.date) { mutableListOf() }
                    .add(Pair(log.completedCount, target))
            }
        }

        val todayAbsoluteIdx = (safeWeeks - 1) * 7 + todayDow
        val items = ArrayList<CombinedDayHeatmapItem>(safeWeeks * 7)
        val walkCal = startCal.clone() as Calendar
        val totalTrackers = trackersWithProgress.size

        for (week in 0 until safeWeeks) {
            for (dow in 0 until 7) {
                val idx = week * 7 + dow
                val dStr = formatDate(walkCal)
                val isFuture = idx > todayAbsoluteIdx
                val isToday = idx == todayAbsoluteIdx

                val trackerData = if (isFuture) emptyList() else (dateToTrackerData[dStr] ?: emptyList())
                val totalCompletedCount = trackerData.sumOf { it.first }
                val totalTarget = trackerData.sumOf { it.second }
                val trackersCompleted = trackerData.count { (count, target) -> count >= target }
                val activeTrackersForDay = trackerData.size

                val isSurplus = totalTrackers > 0 && trackersCompleted == totalTrackers && totalCompletedCount > totalTarget
                val level = when {
                    isFuture || activeTrackersForDay == 0 || totalCompletedCount == 0 -> 0
                    isSurplus -> 4  // Surplus (exceeded all targets)
                    trackersCompleted == totalTrackers -> 3  // All goals met
                    trackersCompleted > 0 -> 2  // Partial completion (at least 1 goal met)
                    totalCompletedCount > 0 -> 1  // Some activity, no goals met
                    else -> 0
                }

                items.add(
                    CombinedDayHeatmapItem(
                        dateString = dStr,
                        weekIndex = week,
                        dayOfWeek = dow,
                        totalCompletedCount = totalCompletedCount,
                        totalTarget = totalTarget,
                        trackersCompleted = trackersCompleted,
                        totalTrackers = totalTrackers,
                        level = level,
                        isToday = isToday,
                        isFuture = isFuture
                    )
                )
                walkCal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return Pair(items, safeWeeks)
    }

    /**
     * Calculates combined streak statistics across all trackers.
     */
    fun calculateCombinedStreakStats(
        trackersWithProgress: List<com.eipna.centsation.habit.data.model.TrackerWithProgress>,
        todayDate: String = TrackerDateUtil.getTodayDateString()
    ): CombinedStreakStats {
        if (trackersWithProgress.isEmpty()) {
            return CombinedStreakStats(0, 0, 0, 0, 0, false, 0)
        }

        var totalGoalsMet = 0
        var totalLogged = 0
        var trackersCompletedToday = 0
        var anySurplusToday = false

        for (twp in trackersWithProgress) {
            val target = twp.tracker.baseDailyTarget.coerceAtLeast(1)
            for (log in twp.logs) {
                totalLogged += log.completedCount
                if (log.completedCount >= target) {
                    totalGoalsMet++
                }
            }
            val todayLog = twp.logs.firstOrNull { it.date == todayDate }
            val count = todayLog?.completedCount ?: 0
            if (count >= target) {
                trackersCompletedToday++
            }
            if (count > target) {
                anySurplusToday = true
            }
        }

        val allCompletedToday = trackersCompletedToday == trackersWithProgress.size && trackersWithProgress.isNotEmpty()
        val isSurplusToday = allCompletedToday && anySurplusToday

        // Calculate combined streak
        val dateToMetCount = mutableMapOf<String, Int>()
        for (twp in trackersWithProgress) {
            val target = twp.tracker.baseDailyTarget.coerceAtLeast(1)
            for (log in twp.logs) {
                if (log.completedCount >= target) {
                    dateToMetCount[log.date] = (dateToMetCount[log.date] ?: 0) + 1
                }
            }
        }
        val totalTrackers = trackersWithProgress.size
        var currentStreak = 0
        val walkCal = parseDate(todayDate)

        if (allCompletedToday) {
            currentStreak = 1
            walkCal.add(Calendar.DAY_OF_MONTH, -1)
            while (true) {
                val dStr = formatDate(walkCal)
                if ((dateToMetCount[dStr] ?: 0) >= totalTrackers) {
                    currentStreak++
                    walkCal.add(Calendar.DAY_OF_MONTH, -1)
                } else {
                    break
                }
            }
        } else {
            walkCal.add(Calendar.DAY_OF_MONTH, -1)
            val yStr = formatDate(walkCal)
            if ((dateToMetCount[yStr] ?: 0) >= totalTrackers) {
                currentStreak = 1
                walkCal.add(Calendar.DAY_OF_MONTH, -1)
                while (true) {
                    val dStr = formatDate(walkCal)
                    if ((dateToMetCount[dStr] ?: 0) >= totalTrackers) {
                        currentStreak++
                        walkCal.add(Calendar.DAY_OF_MONTH, -1)
                    } else {
                        break
                    }
                }
            }
        }

        return CombinedStreakStats(
            totalGoalsMet = totalGoalsMet,
            totalLogged = totalLogged,
            totalTrackers = totalTrackers,
            activeTrackersToday = totalTrackers,
            trackersCompletedToday = trackersCompletedToday,
            isSurplusToday = isSurplusToday,
            currentStreak = currentStreak
        )
    }

    fun formatDate(cal: Calendar): String {
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun parseDate(dateStr: String): Calendar {
        val cal = Calendar.getInstance()
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR)
            val m = (parts[1].toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
            val d = parts[2].toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
            cal.set(y, m, d, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return cal
    }

    /**
     * Calculates current streak, longest streak, and completion statistics.
     */
    fun calculateStreakStats(
        tracker: TrackerEntity,
        logs: List<TrackerLogEntity>,
        todayDate: String = TrackerDateUtil.getTodayDateString()
    ): StreakStats {
        val logsByDate = logs.associate { it.date to it.completedCount }
        val target = tracker.baseDailyTarget.coerceAtLeast(1)

        val todayCount = logsByDate[todayDate] ?: 0
        val isTodayCompleted = todayCount >= target

        var currentStreak = 0
        val walkCal = parseDate(todayDate)

        if (isTodayCompleted) {
            currentStreak = 1
            walkCal.add(Calendar.DAY_OF_MONTH, -1)
            while (true) {
                val dStr = formatDate(walkCal)
                val count = logsByDate[dStr] ?: 0
                if (count >= target) {
                    currentStreak++
                    walkCal.add(Calendar.DAY_OF_MONTH, -1)
                } else {
                    break
                }
            }
        } else {
            walkCal.add(Calendar.DAY_OF_MONTH, -1)
            val yesterdayCount = logsByDate[formatDate(walkCal)] ?: 0
            if (yesterdayCount >= target) {
                currentStreak = 1
                walkCal.add(Calendar.DAY_OF_MONTH, -1)
                while (true) {
                    val dStr = formatDate(walkCal)
                    val count = logsByDate[dStr] ?: 0
                    if (count >= target) {
                        currentStreak++
                        walkCal.add(Calendar.DAY_OF_MONTH, -1)
                    } else {
                        break
                    }
                }
            } else {
                currentStreak = 0
            }
        }

        var maxStreak = 0
        var tempStreak = 0

        val scanStartCal = parseDate(todayDate).apply {
            add(Calendar.DAY_OF_MONTH, -364)
        }
        val scanWalk = scanStartCal.clone() as Calendar

        var completedDays365 = 0
        var totalLogged365 = 0

        for (i in 0 until 365) {
            val dStr = formatDate(scanWalk)
            val count = logsByDate[dStr] ?: 0
            totalLogged365 += count

            if (count >= target) {
                completedDays365++
                tempStreak++
                if (tempStreak > maxStreak) {
                    maxStreak = tempStreak
                }
            } else {
                tempStreak = 0
            }
            scanWalk.add(Calendar.DAY_OF_MONTH, 1)
        }

        val longestStreak = max(maxStreak, currentStreak)
        val completionRate = if (tracker.createdAt > 0) {
            val activeDays = TrackerDateUtil.calculateDaysActive(tracker.createdAt, todayDate)
            val windowDays = min(365L, activeDays).coerceAtLeast(1L)
            (completedDays365.toFloat() / windowDays.toFloat()).coerceIn(0f, 1f)
        } else {
            (completedDays365.toFloat() / 365f).coerceIn(0f, 1f)
        }

        return StreakStats(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalLogged = totalLogged365,
            completedDays = completedDays365,
            totalDays = 365,
            completionRate = completionRate,
            isSurplusToday = todayCount > target
        )
    }

    /**
     * Generates a 35-day or 365-day Sunday-aligned heatmap grid ending on today's week.
     */
    fun generateHeatmapGrid(
        tracker: TrackerEntity,
        logs: List<TrackerLogEntity>,
        is365Days: Boolean,
        todayDate: String = TrackerDateUtil.getTodayDateString()
    ): Pair<List<DayHeatmapItem>, Int> {
        val daysWindow = if (is365Days) 365 else 35
        val weeks = ((daysWindow + 6) / 7).coerceIn(1, 54)
        return generateHeatmapGridForWeeks(tracker, logs, weeks, todayDate)
    }

    /**
     * Generates an exact [numWeeks]-column Sunday-aligned heatmap grid ending on today's week.
     * Ensures even 1 completed rep produces a visible level (>= 1).
     */
    fun generateHeatmapGridForWeeks(
        tracker: TrackerEntity,
        logs: List<TrackerLogEntity>,
        numWeeks: Int,
        todayDate: String = TrackerDateUtil.getTodayDateString()
    ): Pair<List<DayHeatmapItem>, Int> {
        val safeWeeks = numWeeks.coerceIn(1, 54)
        val logsByDate = logs.associate { it.date to it.completedCount }
        val target = tracker.baseDailyTarget.coerceAtLeast(1)

        val todayCal = parseDate(todayDate)
        val todayDow = todayCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val startCal = (todayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -todayDow - (safeWeeks - 1) * 7)
        }

        val todayAbsoluteIdx = (safeWeeks - 1) * 7 + todayDow
        val items = ArrayList<DayHeatmapItem>(safeWeeks * 7)
        val walkCal = startCal.clone() as Calendar

        for (week in 0 until safeWeeks) {
            for (dow in 0 until 7) {
                val idx = week * 7 + dow
                val dStr = formatDate(walkCal)
                val isFuture = idx > todayAbsoluteIdx
                val isToday = idx == todayAbsoluteIdx
                val count = if (isFuture) 0 else (logsByDate[dStr] ?: 0)

                val level = when {
                    isFuture || count <= 0 -> 0
                    count < target -> 1
                    count == target -> 2
                    else -> 3
                }

                items.add(
                    DayHeatmapItem(
                        dateString = dStr,
                        weekIndex = week,
                        dayOfWeek = dow,
                        completedCount = count,
                        target = target,
                        level = level,
                        isToday = isToday,
                        isFuture = isFuture
                    )
                )
                walkCal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return Pair(items, safeWeeks)
    }

    private data class HeatmapRenderCell(
        val weekIndex: Int,
        val dayOfWeek: Int,
        val dateString: String,
        val level: Int,
        val isToday: Boolean,
        val isFuture: Boolean
    )

    /**
     * Renders an authentic GitHub contribution graph matching Image 1:
     * - Top: Month labels (Nov, Dec, Jan, Feb...) aligned above week start
     * - Left: Day of week labels (Mon, Wed, Fri) aligned with rows 1, 3, 5
     * - Grid: 53 columns x 7 rows of crisp square tiles with rounded corners
     * - Colors:
     *     - Level 0: GitHub slate #161B22 with #21262D border
     *     - Level 1: #26A641 outline + subtle tint (even 1 rep!)
     *     - Level 2: GitHub green #006D32 (partial goals) / #26A641 (target met)
     *     - Level 3: Vibrant GitHub green #26A641 / #39D353 (all goals)
     *     - Level 4 / Surplus: Electric blue #38BDF8
     *     - Today: Highlighted with a crisp white ring
     * - Footer: Status text on bottom left + Less [ ][ ][ ][ ][ ] More legend on bottom right
     */
    private fun renderGitHubContributionGraph(
        cells: List<HeatmapRenderCell>,
        numWeeks: Int,
        showMonthLabels: Boolean,
        isDarkMode: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        isCombined: Boolean,
        footerStatus: String? = null
    ): Bitmap {
        val cappedDensity = density.coerceIn(1.0f, 3.0f)
        val safeWidth = widthPx.coerceAtLeast(180)
        val safeHeight = heightPx.coerceAtLeast(80)
        val cols = numWeeks.coerceIn(1, 54)

        val bitmap = createBitmap(safeWidth, safeHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        // Palette matching GitHub Dark theme (#0D1117)
        val canvasBg = if (isDarkMode) 0xFF0D1117.toInt() else 0xFFFFFFFF.toInt()
        val textMuted = if (isDarkMode) 0xFF7D8590.toInt() else 0xFF57606A.toInt()
        val emptyFill = if (isDarkMode) 0xFF161B22.toInt() else 0xFFEBEDF0.toInt()
        val emptyStroke = if (isDarkMode) 0xFF21262D.toInt() else 0xFFD0D7DE.toInt()

        // GitHub Contribution Green Progression
        val greenL1 = if (isDarkMode) 0xFF0E4429.toInt() else 0xFF9BE9A8.toInt()
        val greenL2 = if (isDarkMode) 0xFF006D32.toInt() else 0xFF40C463.toInt()
        val greenL3 = if (isDarkMode) 0xFF26A641.toInt() else 0xFF30A14E.toInt()
        val greenL4 = if (isDarkMode) 0xFF39D353.toInt() else 0xFF216E39.toInt()
        val surplusBlue = if (isDarkMode) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()

        val partialOutline = if (isDarkMode) 0xFF26A641.toInt() else 0xFF10B981.toInt()
        val partialFill = if (isDarkMode) 0x2626A641.toInt() else 0x2610B981.toInt()
        val todayRing = if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val todayRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = todayRing
            strokeWidth = 1.6f * cappedDensity
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        canvas.drawColor(canvasBg)

        // Layout Geometry
        val padX = 6f * cappedDensity
        val padY = 4f * cappedDensity
        val headerH = if (showMonthLabels) 14f * cappedDensity else 2f * cappedDensity
        val footerH = 15f * cappedDensity

        // Measure left gutter for Day of Week labels ("Mon", "Wed", "Fri")
        textPaint.textSize = (11f * cappedDensity).coerceIn(7.5f * cappedDensity, 9.5f * cappedDensity)
        val wedW = textPaint.measureText("Wed")
        val leftGutter = wedW + 4f * cappedDensity

        val availW = (safeWidth - padX * 2f - leftGutter).coerceAtLeast(40f)
        val availH = (safeHeight - padY * 2f - headerH - footerH).coerceAtLeast(30f)

        val gapRatio = 0.22f
        val rawTileW = availW / (cols + (cols - 1) * gapRatio)
        val rawTileH = availH / (7f + 6f * gapRatio)
        val tileSize = min(rawTileW, rawTileH).coerceAtLeast(2.5f)
        val gap = (tileSize * gapRatio).coerceIn(1.2f * cappedDensity, 3.5f * cappedDensity)
        val tileRadius = (tileSize * 0.22f).coerceIn(1.0f * cappedDensity, 3.0f * cappedDensity)

        val actualGridW = cols * tileSize + (cols - 1) * gap
        val actualGridH = 7f * tileSize + 6f * gap
        val gridStartX = padX + leftGutter + max(0f, (availW - actualGridW) / 2f)
        val gridStartY = padY + headerH + max(0f, (availH - actualGridH) / 2f)

        val rect = RectF()
        val ringRect = RectF()

        // 1. Draw Month Labels (Top Header)
        if (showMonthLabels && headerH >= 10f * cappedDensity) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = (headerH * 0.65f).coerceIn(7.5f * cappedDensity, 10f * cappedDensity)
            val monthY = gridStartY - 3f * cappedDensity
            var lastDrawnX = -1000f

            for (w in 0 until cols) {
                val cell = cells.firstOrNull { it.weekIndex == w } ?: continue
                val cal = parseDate(cell.dateString)
                val dom = cal.get(Calendar.DAY_OF_MONTH)
                if (w == 0 || dom <= 7) {
                    val mName = MONTH_NAMES[cal.get(Calendar.MONTH)]
                    val x = gridStartX + w * (tileSize + gap)
                    if (x - lastDrawnX >= 22f * cappedDensity && (gridStartX + actualGridW - x) >= 14f * cappedDensity) {
                        canvas.drawText(mName, x, monthY, textPaint)
                        lastDrawnX = x
                    }
                }
            }
        }

        // 2. Draw Day of Week Labels (Left Gutter: Mon, Wed, Fri)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = (tileSize * 0.85f).coerceIn(7f * cappedDensity, 9.5f * cappedDensity)
        val fm = textPaint.fontMetrics
        val vCenterOffset = (fm.descent + fm.ascent) / 2f
        val dayLabelX = gridStartX - 3f * cappedDensity

        val monY = gridStartY + 1 * (tileSize + gap) + tileSize / 2f - vCenterOffset
        canvas.drawText("Mon", dayLabelX, monY, textPaint)

        val wedY = gridStartY + 3 * (tileSize + gap) + tileSize / 2f - vCenterOffset
        canvas.drawText("Wed", dayLabelX, wedY, textPaint)

        val friY = gridStartY + 5 * (tileSize + gap) + tileSize / 2f - vCenterOffset
        canvas.drawText("Fri", dayLabelX, friY, textPaint)

        // 3. Draw Grid Tiles (53 columns x 7 rows)
        for (cell in cells) {
            if (cell.weekIndex !in 0 until cols || cell.dayOfWeek !in 0 until 7) continue
            val x = gridStartX + cell.weekIndex * (tileSize + gap)
            val y = gridStartY + cell.dayOfWeek * (tileSize + gap)
            rect.set(x, y, x + tileSize, y + tileSize)

            if (cell.isFuture) {
                fillPaint.color = emptyFill
                canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                strokePaint.color = emptyStroke
                strokePaint.strokeWidth = 0.6f * cappedDensity
                canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                continue
            }

            if (isCombined) {
                when (cell.level) {
                    0 -> {
                        fillPaint.color = emptyFill
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = emptyStroke
                        strokePaint.strokeWidth = 0.8f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    1 -> {
                        fillPaint.color = partialFill
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = partialOutline
                        strokePaint.strokeWidth = 1.2f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    2 -> {
                        fillPaint.color = greenL2
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                    3 -> {
                        fillPaint.color = greenL3
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                    else -> {
                        fillPaint.color = surplusBlue
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                }
            } else {
                when (cell.level) {
                    0 -> {
                        fillPaint.color = emptyFill
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = emptyStroke
                        strokePaint.strokeWidth = 0.8f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    1 -> {
                        fillPaint.color = partialFill
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = partialOutline
                        strokePaint.strokeWidth = 1.2f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    2 -> {
                        fillPaint.color = greenL3
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                    else -> {
                        fillPaint.color = surplusBlue
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                }
            }

            if (cell.isToday) {
                val pad = 0.8f * cappedDensity
                ringRect.set(x - pad, y - pad, x + tileSize + pad, y + tileSize + pad)
                canvas.drawRoundRect(ringRect, tileRadius + pad, tileRadius + pad, todayRingPaint)
            }
        }

        // 4. Draw Footer (Bottom Left Status + Bottom Right Legend)
        drawFooterWithLegend(
            canvas = canvas,
            safeWidth = safeWidth,
            safeHeight = safeHeight,
            padX = padX,
            padY = padY,
            footerH = footerH,
            cappedDensity = cappedDensity,
            textPaint = textPaint,
            fillPaint = fillPaint,
            strokePaint = strokePaint,
            emptyFill = emptyFill,
            emptyStroke = emptyStroke,
            partialFill = partialFill,
            partialOutline = partialOutline,
            greenL3 = greenL3,
            surplusBlue = surplusBlue,
            footerStatus = footerStatus
        )

        return bitmap
    }

    private fun drawFooterWithLegend(
        canvas: Canvas,
        safeWidth: Int,
        safeHeight: Int,
        padX: Float,
        padY: Float,
        footerH: Float,
        cappedDensity: Float,
        textPaint: TextPaint,
        fillPaint: Paint,
        strokePaint: Paint,
        emptyFill: Int,
        emptyStroke: Int,
        partialFill: Int,
        partialOutline: Int,
        greenL3: Int,
        surplusBlue: Int,
        footerStatus: String?
    ) {
        textPaint.textSize = (footerH * 0.60f).coerceIn(7.5f * cappedDensity, 9.5f * cappedDensity)
        val footerBaseline = safeHeight - padY - 2f * cappedDensity

        // Bottom Right Legend: Less [empty] [outline green] [solid green] [solid blue] More
        val swatchSize = (8f * cappedDensity).coerceIn(7f * cappedDensity, 9.5f * cappedDensity)
        val swatchGap = (2f * cappedDensity).coerceIn(1.5f * cappedDensity, 2.5f * cappedDensity)
        val lessW = textPaint.measureText("Less")
        val moreW = textPaint.measureText("More")
        val legendSwatchesW = 4 * swatchSize + 3 * swatchGap
        val totalLegendW = lessW + 4f * cappedDensity + legendSwatchesW + 4f * cappedDensity + moreW
        var curX = safeWidth - padX - totalLegendW

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Less", curX, footerBaseline, textPaint)
        curX += lessW + 4f * cappedDensity

        for (i in 0 until 4) {
            val sRect = RectF(
                curX,
                footerBaseline - swatchSize + textPaint.descent(),
                curX + swatchSize,
                footerBaseline + textPaint.descent()
            )
            when (i) {
                0 -> {
                    fillPaint.color = emptyFill
                    canvas.drawRoundRect(sRect, swatchSize * 0.22f, swatchSize * 0.22f, fillPaint)
                    strokePaint.color = emptyStroke
                    strokePaint.strokeWidth = 0.6f * cappedDensity
                    canvas.drawRoundRect(sRect, swatchSize * 0.22f, swatchSize * 0.22f, strokePaint)
                }
                1 -> {
                    fillPaint.color = partialFill
                    canvas.drawRoundRect(sRect, swatchSize * 0.22f, swatchSize * 0.22f, fillPaint)
                    strokePaint.color = partialOutline
                    strokePaint.strokeWidth = 0.9f * cappedDensity
                    canvas.drawRoundRect(sRect, swatchSize * 0.22f, swatchSize * 0.22f, strokePaint)
                }
                2 -> {
                    fillPaint.color = greenL3
                    canvas.drawRoundRect(sRect, swatchSize * 0.22f, swatchSize * 0.22f, fillPaint)
                }
                3 -> {
                    fillPaint.color = surplusBlue
                    canvas.drawRoundRect(sRect, swatchSize * 0.22f, swatchSize * 0.22f, fillPaint)
                }
            }
            curX += swatchSize + swatchGap
        }

        curX += 4f * cappedDensity - swatchGap
        canvas.drawText("More", curX, footerBaseline, textPaint)

        // Bottom Left Status
        if (!footerStatus.isNullOrEmpty()) {
            val maxStatusW = safeWidth - padX - totalLegendW - padX - 8f * cappedDensity
            if (maxStatusW > 30f * cappedDensity) {
                val displayStatus = if (textPaint.measureText(footerStatus) > maxStatusW) {
                    TextUtils.ellipsize(footerStatus, textPaint, maxStatusW, TextUtils.TruncateAt.END).toString()
                } else {
                    footerStatus
                }
                canvas.drawText(displayStatus, padX, footerBaseline, textPaint)
            }
        }
    }

    /**
     * Renders a full-bleed GitHub contribution heatmap scaling edge-to-edge across the widget bitmap.
     */
    fun renderFullBleedHeatmapBitmap(
        items: List<DayHeatmapItem>,
        numWeeks: Int,
        showMonthLabels: Boolean = true,
        stackedBands: Boolean = false,
        isDarkMode: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        footerStatus: String? = null
    ): Bitmap {
        val cells = items.map {
            HeatmapRenderCell(
                weekIndex = it.weekIndex,
                dayOfWeek = it.dayOfWeek,
                dateString = it.dateString,
                level = it.level,
                isToday = it.isToday,
                isFuture = it.isFuture
            )
        }
        return renderGitHubContributionGraph(
            cells = cells,
            numWeeks = numWeeks,
            showMonthLabels = showMonthLabels,
            isDarkMode = isDarkMode,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            isCombined = false,
            footerStatus = footerStatus
        )
    }

    /**
     * Renders a 1-Month (5 weeks, ~35 days) combined heatmap bitmap in authentic GitHub contribution style.
     */
    fun renderCombinedOneMonthBitmap(
        items: List<CombinedDayHeatmapItem>,
        isDarkMode: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        footerStatus: String? = null
    ): Bitmap {
        val cells = items.map {
            HeatmapRenderCell(
                weekIndex = it.weekIndex,
                dayOfWeek = it.dayOfWeek,
                dateString = it.dateString,
                level = it.level,
                isToday = it.isToday,
                isFuture = it.isFuture
            )
        }
        return renderGitHubContributionGraph(
            cells = cells,
            numWeeks = 5,
            showMonthLabels = true,
            isDarkMode = isDarkMode,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            isCombined = true,
            footerStatus = footerStatus
        )
    }

    /**
     * Renders a 1-Week (7 days) combined heatmap bitmap with square tiles, day headers, today indicator, and legend.
     */
    fun renderCombinedOneWeekBitmap(
        items: List<CombinedDayHeatmapItem>,
        isDarkMode: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        footerStatus: String? = null
    ): Bitmap {
        val cappedDensity = density.coerceIn(1.0f, 3.0f)
        val safeWidth = widthPx.coerceAtLeast(180)
        val safeHeight = heightPx.coerceAtLeast(80)

        val bitmap = createBitmap(safeWidth, safeHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        val canvasBg = if (isDarkMode) 0xFF0D1117.toInt() else 0xFFFFFFFF.toInt()
        val textMuted = if (isDarkMode) 0xFF7D8590.toInt() else 0xFF57606A.toInt()
        val textPrimary = if (isDarkMode) 0xFFF0F6FC.toInt() else 0xFF1F2328.toInt()
        val emptyFill = if (isDarkMode) 0xFF161B22.toInt() else 0xFFEBEDF0.toInt()
        val emptyStroke = if (isDarkMode) 0xFF21262D.toInt() else 0xFFD0D7DE.toInt()
        val partialFill = if (isDarkMode) 0x2626A641.toInt() else 0x2610B981.toInt()
        val partialOutline = if (isDarkMode) 0xFF26A641.toInt() else 0xFF10B981.toInt()
        val greenL3 = if (isDarkMode) 0xFF26A641.toInt() else 0xFF30A14E.toInt()
        val surplusBlue = if (isDarkMode) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
        val todayRing = if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val todayRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = todayRing
            strokeWidth = 1.6f * cappedDensity
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        canvas.drawColor(canvasBg)

        val padX = 6f * cappedDensity
        val padY = 4f * cappedDensity
        val headerH = 14f * cappedDensity
        val footerH = 15f * cappedDensity
        val subtextH = 12f * cappedDensity

        val availW = (safeWidth - padX * 2f).coerceAtLeast(40f)
        val availH = (safeHeight - padY * 2f - headerH - footerH - subtextH).coerceAtLeast(20f)

        val cols = 7
        val rawTileW = (availW / cols) * 0.72f
        val tileSize = min(rawTileW, availH).coerceIn(16f * cappedDensity, 34f * cappedDensity)
        val colStep = availW / cols
        val tileRadius = (tileSize * 0.22f).coerceIn(2.5f * cappedDensity, 6.0f * cappedDensity)

        val gridStartY = padY + headerH + max(0f, (availH - tileSize) / 2f)
        val rect = RectF()
        val ringRect = RectF()

        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        for (i in 0 until cols) {
            val cell = items.getOrNull(i)
            val colCenterX = padX + i * colStep + colStep / 2f
            val tileLeft = colCenterX - tileSize / 2f
            val tileTop = gridStartY
            rect.set(tileLeft, tileTop, tileLeft + tileSize, tileTop + tileSize)

            val cal = if (cell != null) parseDate(cell.dateString) else null
            val dayName = if (cal != null) dayNames[cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY] else dayNames[i]
            val dayOfMonth = cal?.get(Calendar.DAY_OF_MONTH)?.toString() ?: ""
            val headerText = if (dayOfMonth.isNotEmpty()) "$dayName $dayOfMonth" else dayName

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = (8.5f * cappedDensity).coerceIn(7f * cappedDensity, 9.5f * cappedDensity)
            textPaint.color = if (cell?.isToday == true) textPrimary else textMuted
            textPaint.isFakeBoldText = (cell?.isToday == true)
            canvas.drawText(headerText, colCenterX, padY + headerH - 3f * cappedDensity, textPaint)
            textPaint.isFakeBoldText = false

            if (cell == null || cell.isFuture) {
                fillPaint.color = emptyFill
                canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                strokePaint.color = emptyStroke
                strokePaint.strokeWidth = 0.8f * cappedDensity
                canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
            } else {
                when {
                    cell.level == 0 -> {
                        fillPaint.color = emptyFill
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = emptyStroke
                        strokePaint.strokeWidth = 0.8f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    cell.level in 1..2 -> {
                        fillPaint.color = partialFill
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = partialOutline
                        strokePaint.strokeWidth = 1.3f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    cell.level == 3 -> {
                        fillPaint.color = greenL3
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                    else -> {
                        fillPaint.color = surplusBlue
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                }

                if (cell.isToday) {
                    val pad = 1.0f * cappedDensity
                    ringRect.set(tileLeft - pad, tileTop - pad, tileLeft + tileSize + pad, tileTop + tileSize + pad)
                    canvas.drawRoundRect(ringRect, tileRadius + pad, tileRadius + pad, todayRingPaint)
                }

                // Subtext below tile (e.g. 0/6, 2/6, ✓)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = (7.5f * cappedDensity).coerceIn(6.5f * cappedDensity, 8.5f * cappedDensity)
                textPaint.color = when {
                    cell.level >= 4 -> surplusBlue
                    cell.level == 3 -> greenL3
                    cell.level in 1..2 -> partialOutline
                    else -> textMuted
                }
                val subText = when {
                    cell.level >= 4 && cell.totalCompletedCount > cell.totalTarget -> "+${cell.totalCompletedCount - cell.totalTarget}"
                    cell.level == 3 -> "✓"
                    cell.totalTrackers > 0 -> "${cell.trackersCompleted}/${cell.totalTrackers}"
                    else -> ""
                }
                if (subText.isNotEmpty()) {
                    canvas.drawText(subText, colCenterX, tileTop + tileSize + subtextH - 1f * cappedDensity, textPaint)
                }
            }
        }

        drawFooterWithLegend(
            canvas = canvas,
            safeWidth = safeWidth,
            safeHeight = safeHeight,
            padX = padX,
            padY = padY,
            footerH = footerH,
            cappedDensity = cappedDensity,
            textPaint = textPaint,
            fillPaint = fillPaint,
            strokePaint = strokePaint,
            emptyFill = emptyFill,
            emptyStroke = emptyStroke,
            partialFill = partialFill,
            partialOutline = partialOutline,
            greenL3 = greenL3,
            surplusBlue = surplusBlue,
            footerStatus = footerStatus
        )

        return bitmap
    }

    /**
     * Renders a combined heatmap bitmap aggregating all trackers into an authentic GitHub contribution graph.
     */
    fun renderCombinedHeatmapBitmap(
        items: List<CombinedDayHeatmapItem>,
        numWeeks: Int,
        showMonthLabels: Boolean = true,
        isDarkMode: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        footerStatus: String? = null
    ): Bitmap {
        return if (numWeeks <= 1) {
            renderCombinedOneWeekBitmap(items, isDarkMode, widthPx, heightPx, density, footerStatus)
        } else {
            val cells = items.map {
                HeatmapRenderCell(
                    weekIndex = it.weekIndex,
                    dayOfWeek = it.dayOfWeek,
                    dateString = it.dateString,
                    level = it.level,
                    isToday = it.isToday,
                    isFuture = it.isFuture
                )
            }
            renderGitHubContributionGraph(
                cells = cells,
                numWeeks = numWeeks,
                showMonthLabels = showMonthLabels,
                isDarkMode = isDarkMode,
                widthPx = widthPx,
                heightPx = heightPx,
                density = density,
                isCombined = true,
                footerStatus = footerStatus
            )
        }
    }
}

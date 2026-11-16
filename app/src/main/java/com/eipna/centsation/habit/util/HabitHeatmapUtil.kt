package com.eipna.centsation.habit.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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

    /**
     * Renders a full-bleed contribution heatmap scaling edge-to-edge across the widget bitmap.
     * 1W Mode: 7 columns (Sun..Sat) across the full width with date numbers and large cards.
     * 1M Mode: 7-column x 5-row monthly contribution calendar with S M T W T F S headers and rounded tiles.
     * 4-Level Color Scheme:
     * Level 0: Empty
     * Level 1: Light green outline + translucent tint (even 1 rep!)
     * Level 2: Solid emerald green (target met)
     * Level 3: Electric blue (surplus / backlog catchup)
     */
    fun renderFullBleedHeatmapBitmap(
        items: List<DayHeatmapItem>,
        numWeeks: Int,
        showMonthLabels: Boolean,
        stackedBands: Boolean,
        isDarkMode: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float
    ): Bitmap {
        val cappedDensity = density.coerceIn(1.0f, 1.5f)
        val safeWidth = widthPx.coerceAtLeast(140)
        val safeHeight = heightPx.coerceAtLeast(70)
        val safeWeeks = numWeeks.coerceAtLeast(1)

        val bitmap = createBitmap(safeWidth, safeHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        val bgColor = if (isDarkMode) 0xFF111620.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawColor(bgColor)

        val emptyColor = if (isDarkMode) 0xFF1E2634.toInt() else 0xFFF1F5F9.toInt()
        val emptyBorderColor = if (isDarkMode) 0xFF2D384B.toInt() else 0xFFCBD5E1.toInt()
        val partialOutlineColor = if (isDarkMode) 0xFF00E676.toInt() else 0xFF10B981.toInt()
        val partialFillColor = if (isDarkMode) 0x1A00E676.toInt() else 0x1A10B981.toInt()
        val completedColor = if (isDarkMode) 0xFF00E676.toInt() else 0xFF10B981.toInt()
        val surplusBlueColor = if (isDarkMode) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()

        val labelColor = if (isDarkMode) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
        val todayRingColor = if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * cappedDensity
        }
        val todayRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = todayRingColor
            strokeWidth = 1.6f * cappedDensity
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 9f * cappedDensity
        }

        val rect = RectF()
        val ringRect = RectF()

        // ----------------- 1-WEEK MODE (7 Days across full width) -----------------
        if (safeWeeks == 1) {
            val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val cols = 7
            val padX = 4f * cappedDensity
            val availW = (safeWidth - padX * 2f).coerceAtLeast(40f)
            val availH = (safeHeight - 4f * cappedDensity).coerceAtLeast(30f)

            val gap = (availW * 0.02f).coerceIn(2f * cappedDensity, 6f * cappedDensity)
            val colW = (availW - (cols - 1) * gap) / cols
            val cellW = colW.coerceAtLeast(10f)

            val headerH = (availH * 0.28f).coerceIn(12f * cappedDensity, 18f * cappedDensity)
            val tileH = (availH - headerH - 4f * cappedDensity).coerceAtLeast(16f)
            val startX = padX
            val startY = 2f * cappedDensity
            val tileRadius = (cellW * 0.22f).coerceIn(3f * cappedDensity, 8f * cappedDensity)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = (cellW * 0.38f).coerceIn(8.5f * cappedDensity, 12f * cappedDensity)

            for (i in 0 until 7) {
                val item = items.getOrNull(i) ?: continue
                val x = startX + i * (cellW + gap)
                val centerX = x + cellW / 2f

                val label = if (cellW >= 24f * cappedDensity) dayNames[item.dayOfWeek] else dayNames[item.dayOfWeek].substring(0, 1)
                val cal = parseDate(item.dateString)
                val dom = cal.get(Calendar.DAY_OF_MONTH)
                val headerText = if (cellW >= 34f * cappedDensity) "$label $dom" else label

                textPaint.color = if (item.isToday) (if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()) else labelColor
                textPaint.isFakeBoldText = item.isToday
                canvas.drawText(headerText, centerX, startY + headerH - 3f * cappedDensity, textPaint)

                val tileTop = startY + headerH
                rect.set(x, tileTop, x + cellW, tileTop + tileH)

                when {
                    item.isFuture || item.level == 0 -> {
                        fillPaint.color = emptyColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = emptyBorderColor
                        strokePaint.strokeWidth = 1f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    item.level == 1 || item.level == 2 -> {
                        fillPaint.color = partialFillColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = partialOutlineColor
                        strokePaint.strokeWidth = 1.8f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    item.level == 3 -> {
                        fillPaint.color = completedColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                    else -> {
                        fillPaint.color = surplusBlueColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                }

                if (item.completedCount > 0 && tileH >= 16f * cappedDensity) {
                    val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textAlign = Paint.Align.CENTER
                        textSize = (tileH * 0.42f).coerceIn(8.5f * cappedDensity, 14f * cappedDensity)
                        color = if (item.level == 1) {
                            partialOutlineColor
                        } else {
                            if (isDarkMode) 0xFF0B0E14.toInt() else 0xFFFFFFFF.toInt()
                        }
                        isFakeBoldText = true
                    }
                    val yCenter = tileTop + tileH / 2f + countPaint.textSize * 0.35f
                    canvas.drawText("${item.completedCount}", centerX, yCenter, countPaint)
                }

                if (item.isToday) {
                    val pad = 1.4f * cappedDensity
                    ringRect.set(x - pad, tileTop - pad, x + cellW + pad, tileTop + tileH + pad)
                    canvas.drawRoundRect(ringRect, tileRadius + pad, tileRadius + pad, todayRingPaint)
                }
            }
            return bitmap
        }

        // ----------------- 1-MONTH CALENDAR GRID MODE (7 Columns x 5 Rows) -----------------
        if (safeWeeks in 2..6) {
            val dayHeaders = arrayOf("S", "M", "T", "W", "T", "F", "S")
            val cols = 7
            val rows = safeWeeks
            val padX = 4f * cappedDensity
            val padY = 2f * cappedDensity
            val availW = (safeWidth - padX * 2f).coerceAtLeast(40f)
            val availH = (safeHeight - padY * 2f).coerceAtLeast(30f)

            val headerH = (availH * 0.16f).coerceIn(9f * cappedDensity, 14f * cappedDensity)
            val gapX = (availW * 0.015f).coerceIn(2f * cappedDensity, 5f * cappedDensity)
            val gapY = (availH * 0.02f).coerceIn(2f * cappedDensity, 4f * cappedDensity)

            val cellW = (availW - (cols - 1) * gapX) / cols
            val cellH = (availH - headerH - (rows - 1) * gapY) / rows
            val tileRadius = (min(cellW, cellH) * 0.22f).coerceIn(2f * cappedDensity, 6f * cappedDensity)

            // Draw Column Headers (S M T W T F S)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = (headerH * 0.75f).coerceIn(7.5f * cappedDensity, 10.5f * cappedDensity)
            textPaint.color = labelColor
            textPaint.isFakeBoldText = true

            for (col in 0 until 7) {
                val cx = padX + col * (cellW + gapX) + cellW / 2f
                canvas.drawText(dayHeaders[col], cx, padY + headerH - 2f * cappedDensity, textPaint)
            }

            // Draw Month Grid Tiles
            val startY = padY + headerH
            for (item in items) {
                if (item.weekIndex !in 0 until rows || item.dayOfWeek !in 0 until cols) continue
                val x = padX + item.dayOfWeek * (cellW + gapX)
                val y = startY + item.weekIndex * (cellH + gapY)
                val centerX = x + cellW / 2f
                rect.set(x, y, x + cellW, y + cellH)

                when {
                    item.isFuture -> {
                        fillPaint.color = emptyColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                    item.level == 0 -> {
                        fillPaint.color = emptyColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = emptyBorderColor
                        strokePaint.strokeWidth = 0.8f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)

                        if (cellH >= 11f * cappedDensity && cellW >= 16f * cappedDensity) {
                            val cal = parseDate(item.dateString)
                            val dom = cal.get(Calendar.DAY_OF_MONTH)
                            val domPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = Paint.Align.CENTER
                                textSize = (cellH * 0.5f).coerceIn(6.5f * cappedDensity, 9.5f * cappedDensity)
                                color = emptyBorderColor
                            }
                            canvas.drawText("$dom", centerX, y + cellH / 2f + domPaint.textSize * 0.35f, domPaint)
                        }
                    }
                    item.level == 1 -> {
                        fillPaint.color = partialFillColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = partialOutlineColor
                        strokePaint.strokeWidth = 1.6f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)

                        if (item.completedCount > 0 && cellH >= 9f * cappedDensity) {
                            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = Paint.Align.CENTER
                                textSize = (cellH * 0.55f).coerceIn(7f * cappedDensity, 11f * cappedDensity)
                                color = partialOutlineColor
                                isFakeBoldText = true
                            }
                            canvas.drawText("${item.completedCount}", centerX, y + cellH / 2f + countPaint.textSize * 0.35f, countPaint)
                        }
                    }
                    item.level == 2 -> {
                        fillPaint.color = completedColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)

                        if (cellH >= 9f * cappedDensity) {
                            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = Paint.Align.CENTER
                                textSize = (cellH * 0.55f).coerceIn(7f * cappedDensity, 11f * cappedDensity)
                                color = if (isDarkMode) 0xFF0B0E14.toInt() else 0xFFFFFFFF.toInt()
                                isFakeBoldText = true
                            }
                            val text = if (cellW >= 24f * cappedDensity) "${item.completedCount}" else "✓"
                            canvas.drawText(text, centerX, y + cellH / 2f + countPaint.textSize * 0.35f, countPaint)
                        }
                    }
                    else -> {
                        fillPaint.color = surplusBlueColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)

                        if (cellH >= 9f * cappedDensity) {
                            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = Paint.Align.CENTER
                                textSize = (cellH * 0.55f).coerceIn(7f * cappedDensity, 11f * cappedDensity)
                                color = if (isDarkMode) 0xFF0B0E14.toInt() else 0xFFFFFFFF.toInt()
                                isFakeBoldText = true
                            }
                            canvas.drawText("${item.completedCount}", centerX, y + cellH / 2f + countPaint.textSize * 0.35f, countPaint)
                        }
                    }
                }

                if (item.isToday) {
                    val pad = 1.2f * cappedDensity
                    ringRect.set(x - pad, y - pad, x + cellW + pad, y + cellH + pad)
                    canvas.drawRoundRect(ringRect, tileRadius + pad, tileRadius + pad, todayRingPaint)
                }
            }
            return bitmap
        }

        // ----------------- MULTI-WEEK BANDS (Fallback for > 6 weeks) -----------------
        val leftGutter = 13f * cappedDensity
        val rightMargin = 4f * cappedDensity
        val monthH = if (showMonthLabels) 12f * cappedDensity else 2f * cappedDensity
        val availW = (safeWidth - leftGutter - rightMargin).coerceAtLeast(40f)
        val availH = (safeHeight - monthH - 3f * cappedDensity).coerceAtLeast(30f)

        val rawPitch = min(availW / safeWeeks, availH / 7f)
        val gap = (rawPitch * 0.16f).coerceIn(1.5f * cappedDensity, 4f * cappedDensity)
        val cellW = (availW - (safeWeeks - 1) * gap) / safeWeeks.toFloat()
        val cellH = (availH - 6f * gap) / 7f
        val cellSize = min(cellW, cellH).coerceAtLeast(1.5f)
        val radius = (cellSize * 0.25f).coerceIn(1.5f * cappedDensity, 5f * cappedDensity)

        val gridW = safeWeeks * cellSize + (safeWeeks - 1) * gap
        val gridH = 7f * cellSize + 6f * gap
        val startX = leftGutter + max(0f, (availW - gridW) / 2f)
        val startY = monthH + max(0f, (availH - gridH) / 2f)

        for (item in items) {
            if (item.isFuture) continue
            val x = startX + item.weekIndex * (cellSize + gap)
            val y = startY + item.dayOfWeek * (cellSize + gap)
            rect.set(x, y, x + cellSize, y + cellSize)

            when (item.level) {
                0 -> {
                    fillPaint.color = emptyColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                }
                1, 2 -> {
                    fillPaint.color = partialFillColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                    strokePaint.color = partialOutlineColor
                    strokePaint.strokeWidth = 1.2f * cappedDensity
                    canvas.drawRoundRect(rect, radius, radius, strokePaint)
                }
                3 -> {
                    fillPaint.color = completedColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                }
                else -> {
                    fillPaint.color = surplusBlueColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                }
            }

            if (item.isToday) {
                val pad = 1.2f * cappedDensity
                ringRect.set(x - pad, y - pad, x + cellSize + pad, y + cellSize + pad)
                canvas.drawRoundRect(ringRect, radius + pad, radius + pad, todayRingPaint)
            }
        }

        return bitmap
    }

    /**
     * Renders a combined heatmap bitmap aggregating all trackers.
     * Uses the exact same 7-column calendar grid and 4-level color scheme:
     * Level 0: None
     * Level 1: Some activity (light green outline + translucent tint)
     * Level 2: All goals met (solid emerald green)
     * Level 3: Surplus (electric blue)
     */
    fun renderCombinedHeatmapBitmap(
        items: List<CombinedDayHeatmapItem>,
        numWeeks: Int,
        showMonthLabels: Boolean,
        isDarkMode: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float
    ): Bitmap {
        val cappedDensity = density.coerceIn(1.0f, 1.5f)
        val safeWidth = widthPx.coerceAtLeast(140)
        val safeHeight = heightPx.coerceAtLeast(70)
        val safeWeeks = numWeeks.coerceAtLeast(1)

        val bitmap = createBitmap(safeWidth, safeHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        val bgColor = if (isDarkMode) 0xFF111620.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawColor(bgColor)

        val emptyColor = if (isDarkMode) 0xFF1E2634.toInt() else 0xFFF1F5F9.toInt()
        val emptyBorderColor = if (isDarkMode) 0xFF2D384B.toInt() else 0xFFCBD5E1.toInt()
        val partialOutlineColor = if (isDarkMode) 0xFF00E676.toInt() else 0xFF10B981.toInt()
        val partialFillColor = if (isDarkMode) 0x1A00E676.toInt() else 0x1A10B981.toInt()
        val completedColor = if (isDarkMode) 0xFF00E676.toInt() else 0xFF10B981.toInt()
        val surplusBlueColor = if (isDarkMode) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()

        val labelColor = if (isDarkMode) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
        val todayRingColor = if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * cappedDensity
        }
        val todayRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = todayRingColor
            strokeWidth = 1.6f * cappedDensity
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 9f * cappedDensity
        }

        val rect = RectF()
        val ringRect = RectF()

        // ----------------- COMBINED 1-WEEK MODE (7 Columns) -----------------
        if (safeWeeks == 1) {
            val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val cols = 7
            val padX = 4f * cappedDensity
            val availW = (safeWidth - padX * 2f).coerceAtLeast(40f)
            val availH = (safeHeight - 4f * cappedDensity).coerceAtLeast(30f)

            val gap = (availW * 0.02f).coerceIn(2f * cappedDensity, 6f * cappedDensity)
            val colW = (availW - (cols - 1) * gap) / cols
            val cellW = colW.coerceAtLeast(10f)

            val headerH = (availH * 0.28f).coerceIn(12f * cappedDensity, 18f * cappedDensity)
            val tileH = (availH - headerH - 4f * cappedDensity).coerceAtLeast(16f)
            val startX = padX
            val startY = 2f * cappedDensity
            val tileRadius = (cellW * 0.22f).coerceIn(3f * cappedDensity, 8f * cappedDensity)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = (cellW * 0.38f).coerceIn(8.5f * cappedDensity, 12f * cappedDensity)

            for (i in 0 until 7) {
                val item = items.getOrNull(i) ?: continue
                val x = startX + i * (cellW + gap)
                val centerX = x + cellW / 2f

                val label = if (cellW >= 24f * cappedDensity) dayNames[item.dayOfWeek] else dayNames[item.dayOfWeek].substring(0, 1)
                val cal = parseDate(item.dateString)
                val dom = cal.get(Calendar.DAY_OF_MONTH)
                val headerText = if (cellW >= 34f * cappedDensity) "$label $dom" else label

                textPaint.color = if (item.isToday) (if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()) else labelColor
                textPaint.isFakeBoldText = item.isToday
                canvas.drawText(headerText, centerX, startY + headerH - 3f * cappedDensity, textPaint)

                val tileTop = startY + headerH
                rect.set(x, tileTop, x + cellW, tileTop + tileH)

                when {
                    item.isFuture || item.level == 0 -> {
                        fillPaint.color = emptyColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = emptyBorderColor
                        strokePaint.strokeWidth = 1f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    item.level == 1 || item.level == 2 -> {
                        fillPaint.color = partialFillColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = partialOutlineColor
                        strokePaint.strokeWidth = 1.8f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)
                    }
                    item.level == 3 -> {
                        fillPaint.color = completedColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                    else -> {
                        fillPaint.color = surplusBlueColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                }

                if (item.totalCompletedCount > 0 && tileH >= 16f * cappedDensity) {
                    val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textAlign = Paint.Align.CENTER
                        textSize = (tileH * 0.42f).coerceIn(8f * cappedDensity, 13f * cappedDensity)
                        color = if (item.level == 1) {
                            partialOutlineColor
                        } else {
                            if (isDarkMode) 0xFF0B0E14.toInt() else 0xFFFFFFFF.toInt()
                        }
                        isFakeBoldText = true
                    }
                    val yCenter = tileTop + tileH / 2f + countPaint.textSize * 0.35f
                    val labelText = "${item.trackersCompleted}/${item.totalTrackers}"
                    canvas.drawText(labelText, centerX, yCenter, countPaint)
                }

                if (item.isToday) {
                    val pad = 1.4f * cappedDensity
                    ringRect.set(x - pad, tileTop - pad, x + cellW + pad, tileTop + tileH + pad)
                    canvas.drawRoundRect(ringRect, tileRadius + pad, tileRadius + pad, todayRingPaint)
                }
            }
            return bitmap
        }

        // ----------------- COMBINED 1-MONTH CALENDAR GRID MODE (7 Columns x 5 Rows) -----------------
        if (safeWeeks in 2..6) {
            val dayHeaders = arrayOf("S", "M", "T", "W", "T", "F", "S")
            val cols = 7
            val rows = safeWeeks
            val padX = 4f * cappedDensity
            val padY = 2f * cappedDensity
            val availW = (safeWidth - padX * 2f).coerceAtLeast(40f)
            val availH = (safeHeight - padY * 2f).coerceAtLeast(30f)

            val headerH = (availH * 0.16f).coerceIn(9f * cappedDensity, 14f * cappedDensity)
            val gapX = (availW * 0.015f).coerceIn(2f * cappedDensity, 5f * cappedDensity)
            val gapY = (availH * 0.02f).coerceIn(2f * cappedDensity, 4f * cappedDensity)

            val cellW = (availW - (cols - 1) * gapX) / cols
            val cellH = (availH - headerH - (rows - 1) * gapY) / rows
            val tileRadius = (min(cellW, cellH) * 0.22f).coerceIn(2f * cappedDensity, 6f * cappedDensity)

            // Draw Column Headers (S M T W T F S)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = (headerH * 0.75f).coerceIn(7.5f * cappedDensity, 10.5f * cappedDensity)
            textPaint.color = labelColor
            textPaint.isFakeBoldText = true

            for (col in 0 until 7) {
                val cx = padX + col * (cellW + gapX) + cellW / 2f
                canvas.drawText(dayHeaders[col], cx, padY + headerH - 2f * cappedDensity, textPaint)
            }

            // Draw Month Grid Tiles
            val startY = padY + headerH
            for (item in items) {
                if (item.weekIndex !in 0 until rows || item.dayOfWeek !in 0 until cols) continue
                val x = padX + item.dayOfWeek * (cellW + gapX)
                val y = startY + item.weekIndex * (cellH + gapY)
                val centerX = x + cellW / 2f
                rect.set(x, y, x + cellW, y + cellH)

                when {
                    item.isFuture -> {
                        fillPaint.color = emptyColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                    }
                    item.level == 0 -> {
                        fillPaint.color = emptyColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = emptyBorderColor
                        strokePaint.strokeWidth = 0.8f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)

                        if (cellH >= 11f * cappedDensity && cellW >= 16f * cappedDensity) {
                            val cal = parseDate(item.dateString)
                            val dom = cal.get(Calendar.DAY_OF_MONTH)
                            val domPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = Paint.Align.CENTER
                                textSize = (cellH * 0.5f).coerceIn(6.5f * cappedDensity, 9.5f * cappedDensity)
                                color = emptyBorderColor
                            }
                            canvas.drawText("$dom", centerX, y + cellH / 2f + domPaint.textSize * 0.35f, domPaint)
                        }
                    }
                    item.level == 1 || item.level == 2 -> {
                        fillPaint.color = partialFillColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)
                        strokePaint.color = partialOutlineColor
                        strokePaint.strokeWidth = 1.6f * cappedDensity
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, strokePaint)

                        if (item.totalCompletedCount > 0 && cellH >= 9f * cappedDensity) {
                            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = Paint.Align.CENTER
                                textSize = (cellH * 0.55f).coerceIn(7f * cappedDensity, 10f * cappedDensity)
                                color = partialOutlineColor
                                isFakeBoldText = true
                            }
                            canvas.drawText("${item.trackersCompleted}/${item.totalTrackers}", centerX, y + cellH / 2f + countPaint.textSize * 0.35f, countPaint)
                        }
                    }
                    item.level == 3 -> {
                        fillPaint.color = completedColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)

                        if (cellH >= 9f * cappedDensity) {
                            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = Paint.Align.CENTER
                                textSize = (cellH * 0.55f).coerceIn(7f * cappedDensity, 10f * cappedDensity)
                                color = if (isDarkMode) 0xFF0B0E14.toInt() else 0xFFFFFFFF.toInt()
                                isFakeBoldText = true
                            }
                            val text = if (cellW >= 24f * cappedDensity) "${item.trackersCompleted}/${item.totalTrackers}" else "✓"
                            canvas.drawText(text, centerX, y + cellH / 2f + countPaint.textSize * 0.35f, countPaint)
                        }
                    }
                    else -> {
                        fillPaint.color = surplusBlueColor
                        canvas.drawRoundRect(rect, tileRadius, tileRadius, fillPaint)

                        if (cellH >= 9f * cappedDensity) {
                            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = Paint.Align.CENTER
                                textSize = (cellH * 0.55f).coerceIn(7f * cappedDensity, 10f * cappedDensity)
                                color = if (isDarkMode) 0xFF0B0E14.toInt() else 0xFFFFFFFF.toInt()
                                isFakeBoldText = true
                            }
                            canvas.drawText("${item.trackersCompleted}/${item.totalTrackers}", centerX, y + cellH / 2f + countPaint.textSize * 0.35f, countPaint)
                        }
                    }
                }

                if (item.isToday) {
                    val pad = 1.2f * cappedDensity
                    ringRect.set(x - pad, y - pad, x + cellW + pad, y + cellH + pad)
                    canvas.drawRoundRect(ringRect, tileRadius + pad, tileRadius + pad, todayRingPaint)
                }
            }
            return bitmap
        }

        // ----------------- MULTI-WEEK BANDS (Fallback for > 6 weeks) -----------------
        val leftGutter = 13f * cappedDensity
        val rightMargin = 4f * cappedDensity
        val monthH = if (showMonthLabels) 12f * cappedDensity else 2f * cappedDensity
        val availW = (safeWidth - leftGutter - rightMargin).coerceAtLeast(40f)
        val availH = (safeHeight - monthH - 3f * cappedDensity).coerceAtLeast(30f)

        val rawPitch = min(availW / safeWeeks, availH / 7f)
        val gap = (rawPitch * 0.16f).coerceIn(1.5f * cappedDensity, 4f * cappedDensity)
        val cellW = (availW - (safeWeeks - 1) * gap) / safeWeeks.toFloat()
        val cellH = (availH - 6f * gap) / 7f
        val cellSize = min(cellW, cellH).coerceAtLeast(1.5f)
        val radius = (cellSize * 0.25f).coerceIn(1.5f * cappedDensity, 5f * cappedDensity)

        val gridW = safeWeeks * cellSize + (safeWeeks - 1) * gap
        val gridH = 7f * cellSize + 6f * gap
        val startX = leftGutter + max(0f, (availW - gridW) / 2f)
        val startY = monthH + max(0f, (availH - gridH) / 2f)

        for (item in items) {
            if (item.isFuture) continue
            val x = startX + item.weekIndex * (cellSize + gap)
            val y = startY + item.dayOfWeek * (cellSize + gap)
            rect.set(x, y, x + cellSize, y + cellSize)

            when (item.level) {
                0 -> {
                    fillPaint.color = emptyColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                }
                1, 2 -> {
                    fillPaint.color = partialFillColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                    strokePaint.color = partialOutlineColor
                    strokePaint.strokeWidth = 1.2f * cappedDensity
                    canvas.drawRoundRect(rect, radius, radius, strokePaint)
                }
                3 -> {
                    fillPaint.color = completedColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                }
                else -> {
                    fillPaint.color = surplusBlueColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                }
            }

            if (item.isToday) {
                val pad = 1.2f * cappedDensity
                ringRect.set(x - pad, y - pad, x + cellSize + pad, y + cellSize + pad)
                canvas.drawRoundRect(ringRect, radius + pad, radius + pad, todayRingPaint)
            }
        }

        return bitmap
    }
}

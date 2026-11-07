package com.eipna.centsation.habit.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
    val level: Int, // 0: None, 1: 1-49%, 2: 50-99%, 3: 100%, 4: >100%
    val isToday: Boolean,
    val isFuture: Boolean
)

data class StreakStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalLogged: Int,
    val completedDays: Int,
    val totalDays: Int,
    val completionRate: Float
)

object HabitHeatmapUtil {

    private val MONTH_NAMES = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

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

        val todayCal = parseDate(todayDate)

        // 1. Current streak calculation
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
            // If today is not completed yet, streak from yesterday is still alive
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

        // 2. Longest streak calculation across past 365 days
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
            completionRate = completionRate
        )
    }

    /**
     * Generates a GitHub-style grid of DayHeatmapItems.
     * Aligned to Sundays (row 0 = Sun, 1 = Mon ... 6 = Sat).
     */
    fun generateHeatmapGrid(
        tracker: TrackerEntity,
        logs: List<TrackerLogEntity>,
        is365Days: Boolean,
        todayDate: String = TrackerDateUtil.getTodayDateString()
    ): Pair<List<DayHeatmapItem>, Int> {
        val logsByDate = logs.associate { it.date to it.completedCount }
        val target = tracker.baseDailyTarget.coerceAtLeast(1)

        val todayCal = parseDate(todayDate)

        // Determine requested days window
        val daysWindow = if (is365Days) 365 else 35

        val startCal = parseDate(todayDate).apply {
            add(Calendar.DAY_OF_MONTH, -(daysWindow - 1))
        }

        // Align startCal to the beginning Sunday of that week
        val dayOfWeekStart = startCal.get(Calendar.DAY_OF_WEEK) // Sunday = 1, Monday = 2 ...
        val daysToSunday = dayOfWeekStart - Calendar.SUNDAY
        val gridStartCal = (startCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -daysToSunday)
        }

        // Calculate total weeks to reach today's week
        val diffMs = todayCal.timeInMillis - gridStartCal.timeInMillis
        val totalDaysFromGridStart = (diffMs / (24L * 60L * 60L * 1000L)).toInt() + 1
        val numWeeks = ((totalDaysFromGridStart + 6) / 7).coerceAtLeast(1)

        val items = ArrayList<DayHeatmapItem>(numWeeks * 7)
        val walkCal = gridStartCal.clone() as Calendar

        for (w in 0 until numWeeks) {
            for (r in 0 until 7) {
                val dStr = formatDate(walkCal)
                val isToday = (dStr == todayDate)
                val isFuture = walkCal.after(todayCal)

                val count = if (isFuture) 0 else (logsByDate[dStr] ?: 0)
                val level = when {
                    isFuture || count == 0 -> 0
                    count < (target * 0.5) -> 1
                    count < target -> 2
                    count == target -> 3
                    else -> 4
                }

                items.add(
                    DayHeatmapItem(
                        dateString = dStr,
                        weekIndex = w,
                        dayOfWeek = r,
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

        return Pair(items, numWeeks)
    }

    /**
     * Renders the GitHub-style contribution squares onto a hardware-accelerated Bitmap.
     */
    fun renderHeatmapBitmap(
        items: List<DayHeatmapItem>,
        numWeeks: Int,
        is365Days: Boolean,
        isDarkMode: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float
    ): Bitmap {
        val cappedDensity = min(density, 1.75f).coerceAtLeast(1.0f)
        val safeWidth = widthPx.coerceAtLeast(100)
        val safeHeight = heightPx.coerceAtLeast(60)

        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        // Fill background with surface color for RGB_565 (matching widget card background)
        val bgColor = if (isDarkMode) 0xFF121620.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawColor(bgColor)

        // Color definitions matching GitHub contribution palette & M3 Emerald
        val emptyColor = if (isDarkMode) 0xFF1E2533.toInt() else 0xFFF1F5F9.toInt()
        val emptyBorderColor = if (isDarkMode) 0xFF2A3447.toInt() else 0xFFE2E8F0.toInt()
        val level1Color = if (isDarkMode) 0xFF0E4429.toInt() else 0xFFA7F3D0.toInt()
        val level2Color = if (isDarkMode) 0xFF006D32.toInt() else 0xFF34D399.toInt()
        val level3Color = if (isDarkMode) 0xFF059669.toInt() else 0xFF10B981.toInt()
        val level4Color = if (isDarkMode) 0xFF00E676.toInt() else 0xFF00875A.toInt()
        val todayRingColor = if (isDarkMode) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
        val labelColor = if (isDarkMode) 0xFF94A3B8.toInt() else 0xFF64748B.toInt()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * cappedDensity
        }

        val todayRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * cappedDensity
            color = todayRingColor
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = if (is365Days) 8f * cappedDensity else 9f * cappedDensity
        }

        val leftMargin = if (is365Days) 16f * cappedDensity else 14f * cappedDensity
        val topMargin = if (is365Days) 14f * cappedDensity else 4f * cappedDensity
        val rightMargin = 4f * cappedDensity
        val bottomMargin = 4f * cappedDensity

        val availWidth = safeWidth - leftMargin - rightMargin
        val availHeight = safeHeight - topMargin - bottomMargin

        val gap = if (is365Days) 2f * cappedDensity else 3.5f * cappedDensity
        val cellW = (availWidth - (numWeeks - 1) * gap) / numWeeks
        val cellH = (availHeight - 6 * gap) / 7
        val cellSize = min(cellW, cellH).coerceAtLeast(3f * cappedDensity)
        val cornerRadius = if (is365Days) max(1.5f * cappedDensity, cellSize * 0.2f) else max(3f * cappedDensity, cellSize * 0.25f)

        // Draw Weekday Labels ("M", "W", "F") on the left
        val weekdayRowLabels = listOf(1 to "M", 3 to "W", 5 to "F")

        for ((row, label) in weekdayRowLabels) {
            val y = topMargin + row * (cellSize + gap) + cellSize * 0.8f
            canvas.drawText(label, 2f * cappedDensity, y, textPaint)
        }

        // Draw Month Labels on Top for 365-day view
        if (is365Days) {
            var lastDrawnMonth = -1
            for (w in 0 until numWeeks) {
                val item = items.firstOrNull { it.weekIndex == w && it.dayOfWeek == 0 } ?: continue
                val cal = parseDate(item.dateString)
                val month = cal.get(Calendar.MONTH)
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

                // If week contains beginning of month or first week
                if (month != lastDrawnMonth && (dayOfMonth <= 7 || w == 0)) {
                    val x = leftMargin + w * (cellSize + gap)
                    if (x + 20f * cappedDensity < safeWidth) {
                        canvas.drawText(MONTH_NAMES[month], x, topMargin - 3f * cappedDensity, textPaint)
                        lastDrawnMonth = month
                    }
                }
            }
        }

        // Draw squares
        val rect = RectF()
        val ringRect = RectF()

        for (item in items) {
            if (item.isFuture) continue

            val x = leftMargin + item.weekIndex * (cellSize + gap)
            val y = topMargin + item.dayOfWeek * (cellSize + gap)

            rect.set(x, y, x + cellSize, y + cellSize)

            // Select fill color by level
            val color = when (item.level) {
                1 -> level1Color
                2 -> level2Color
                3 -> level3Color
                4 -> level4Color
                else -> emptyColor
            }

            fillPaint.color = color
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

            // Subtle border for empty squares
            if (item.level == 0) {
                strokePaint.color = emptyBorderColor
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
            }

            // Highlight ring around today's cell
            if (item.isToday) {
                val ringPad = 1f * cappedDensity
                ringRect.set(x - ringPad, y - ringPad, x + cellSize + ringPad, y + cellSize + ringPad)
                canvas.drawRoundRect(ringRect, cornerRadius + ringPad, cornerRadius + ringPad, todayRingPaint)
            }
        }

        return bitmap
    }
}

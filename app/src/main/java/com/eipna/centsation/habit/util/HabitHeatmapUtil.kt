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
                    count < (target * 0.5) -> 1
                    count < target -> 2
                    count == target -> 3
                    else -> 4
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
     * Renders a full-bleed GitHub-style contribution heatmap scaling edge-to-edge across the widget bitmap.
     * When [stackedBands] is true, splits the weeks into two wide 7-row bands stacked vertically so
     * taller / full-page widgets display large, crisp tiles with zero dead whitespace.
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
        val safeHeight = heightPx.coerceAtLeast(80)
        val safeWeeks = numWeeks.coerceAtLeast(1)

        val bitmap = createBitmap(safeWidth, safeHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        val bgColor = if (isDarkMode) 0xFF111620.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawColor(bgColor)

        val emptyColor = if (isDarkMode) 0xFF1E2634.toInt() else 0xFFF1F5F9.toInt()
        val emptyBorderColor = if (isDarkMode) 0xFF2D384B.toInt() else 0xFFCBD5E1.toInt()
        val level1Color = if (isDarkMode) 0xFF218C54.toInt() else 0xFF6EE7B7.toInt()
        val level2Color = if (isDarkMode) 0xFF2CB55C.toInt() else 0xFF34D399.toInt()
        val level3Color = if (isDarkMode) 0xFF00E676.toInt() else 0xFF10B981.toInt()
        val level4Color = if (isDarkMode) 0xFF69F0AE.toInt() else 0xFF047857.toInt()

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
            strokeWidth = 1.4f * cappedDensity
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 9f * cappedDensity
        }

        val showDayLabels = safeWidth >= (170f * cappedDensity).toInt()
        val leftGutter = if (showDayLabels) 13f * cappedDensity else 2f * cappedDensity
        val rightMargin = 4f * cappedDensity
        val rect = RectF()
        val ringRect = RectF()
        var lastDrawnMonth = -1

        fun drawBand(
            bandItems: List<DayHeatmapItem>,
            bandWeekOffset: Int,
            bandWeekCount: Int,
            bandTop: Float,
            bandHeight: Float,
            drawMonths: Boolean
        ) {
            if (bandWeekCount <= 0) return
            val monthH = if (drawMonths) 12f * cappedDensity else 2f * cappedDensity
            val bottomPad = 3f * cappedDensity
            val availW = (safeWidth - leftGutter - rightMargin).coerceAtLeast(40f)
            val availH = (bandHeight - monthH - bottomPad).coerceAtLeast(30f)

            val rawPitch = min(availW / bandWeekCount, availH / 7f)
            val minGap = if (bandWeekCount >= 35) 1.0f else 1.5f * cappedDensity
            val gap = (rawPitch * 0.16f).coerceIn(minGap, 4f * cappedDensity)
            val cellW = (availW - (bandWeekCount - 1) * gap) / bandWeekCount
            val cellH = (availH - 6f * gap) / 7f
            val cellSize = min(cellW, cellH).coerceAtLeast(1.5f)

            val gridW = bandWeekCount * cellSize + (bandWeekCount - 1) * gap
            val gridH = 7f * cellSize + 6f * gap
            val startX = leftGutter + max(0f, (availW - gridW) / 2f)
            val startY = bandTop + monthH + max(0f, (availH - gridH) / 2f)
            val radius = (cellSize * 0.22f).coerceIn(1.5f * cappedDensity, 5f * cappedDensity)

            if (showDayLabels) {
                textPaint.textSize = (cellSize * 0.65f).coerceIn(7f * cappedDensity, 10f * cappedDensity)
                val dayRows = intArrayOf(1, 3, 5)
                val dayNames = arrayOf("M", "W", "F")
                for (i in dayRows.indices) {
                    val yCenter = startY + dayRows[i] * (cellSize + gap) + cellSize * 0.72f
                    canvas.drawText(dayNames[i], 1f * cappedDensity, yCenter, textPaint)
                }
            }

            if (drawMonths) {
                textPaint.textSize = 9f * cappedDensity
                var lastLabelX = -100f
                for (w in 0 until bandWeekCount) {
                    val globalWeek = bandWeekOffset + w
                    val firstDay = bandItems.firstOrNull { it.weekIndex == globalWeek && it.dayOfWeek == 0 }
                        ?: bandItems.firstOrNull { it.weekIndex == globalWeek }
                        ?: continue
                    val cal = parseDate(firstDay.dateString)
                    val month = cal.get(Calendar.MONTH)
                    val dom = cal.get(Calendar.DAY_OF_MONTH)
                    if (month != lastDrawnMonth && (dom <= 7 || (w == 0 && bandWeekOffset == 0))) {
                        val x = startX + w * (cellSize + gap)
                        if (x - lastLabelX >= 18f * cappedDensity) {
                            val labelW = textPaint.measureText(MONTH_NAMES[month])
                            val drawX = x.coerceAtMost(safeWidth - labelW - 2f)
                            if (drawX - lastLabelX >= 14f * cappedDensity) {
                                canvas.drawText(MONTH_NAMES[month], drawX, startY - 3f * cappedDensity, textPaint)
                                lastDrawnMonth = month
                                lastLabelX = drawX
                            }
                        }
                    }
                }
            }

            for (item in bandItems) {
                if (item.isFuture) continue
                val localWeek = item.weekIndex - bandWeekOffset
                if (localWeek !in 0 until bandWeekCount) continue

                val x = startX + localWeek * (cellSize + gap)
                val y = startY + item.dayOfWeek * (cellSize + gap)
                rect.set(x, y, x + cellSize, y + cellSize)

                fillPaint.color = when (item.level) {
                    1 -> level1Color
                    2 -> level2Color
                    3 -> level3Color
                    4 -> level4Color
                    else -> emptyColor
                }
                canvas.drawRoundRect(rect, radius, radius, fillPaint)

                if (item.level == 0) {
                    strokePaint.color = emptyBorderColor
                    canvas.drawRoundRect(rect, radius, radius, strokePaint)
                }

                if (item.isToday) {
                    val pad = 1.2f * cappedDensity
                    ringRect.set(x - pad, y - pad, x + cellSize + pad, y + cellSize + pad)
                    canvas.drawRoundRect(ringRect, radius + pad, radius + pad, todayRingPaint)
                }
            }
        }

        if (stackedBands && safeWeeks >= 8) {
            val splitWeek = safeWeeks / 2
            val bandGap = 8f * cappedDensity
            val bandHeight = (safeHeight - bandGap) / 2f
            drawBand(
                bandItems = items.filter { it.weekIndex < splitWeek },
                bandWeekOffset = 0,
                bandWeekCount = splitWeek,
                bandTop = 0f,
                bandHeight = bandHeight,
                drawMonths = showMonthLabels
            )
            drawBand(
                bandItems = items.filter { it.weekIndex >= splitWeek },
                bandWeekOffset = splitWeek,
                bandWeekCount = safeWeeks - splitWeek,
                bandTop = bandHeight + bandGap,
                bandHeight = bandHeight,
                drawMonths = showMonthLabels
            )
        } else {
            drawBand(
                bandItems = items,
                bandWeekOffset = 0,
                bandWeekCount = safeWeeks,
                bandTop = 0f,
                bandHeight = safeHeight.toFloat(),
                drawMonths = showMonthLabels
            )
        }

        return bitmap
    }
}

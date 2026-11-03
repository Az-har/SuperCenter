package com.eipna.centsation.habit.calendar

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class ParsedHabitDetails(
    val title: String,
    val baseDailyTarget: Int,
    val unit: String,
    val scheduledHour: Int,
    val scheduledMinute: Int
)

object CalendarHabitParser {

    private val PREFIX_REGEX = Pattern.compile(
        "^([\\u2705\\u2714\\u2713\\u2022\\u25AA\\u25B6\\u25CF]|(?:\\[Done\\]|\\[TODO\\]|\\[TASK\\]|TODO:|Task:))\\s*",
        Pattern.CASE_INSENSITIVE
    )

    private val SUFFIX_REGEX = Pattern.compile(
        "\\s*(\\[Done\\]|\\[TODO\\]|\\[TASK\\])$",
        Pattern.CASE_INSENSITIVE
    )

    // Matches "20 pushups", "10000 steps", "30 mins", "15 pages", "8 glasses", "5 km"
    private val NUMBER_WITH_UNIT_REGEX = Pattern.compile(
        "(?:^|\\s)(\\d{1,6})\\s*(reps?|push-?ups?|sit-?ups?|pull-?ups?|chin-?ups?|squats?|pages?|chapters?|glasses?|liters?|litres?|l|ml|km|miles?|steps?|minutes?|mins?|min|hours?|hrs?|hr|times?|sessions?|pomodoros?|lessons?)(?:\\s|$|[.,!])",
        Pattern.CASE_INSENSITIVE
    )

    // Matches numbers in title, e.g. "Pushups 25", "Drink Water 8"
    private val STANDALONE_NUMBER_REGEX = Pattern.compile(
        "(?:^|\\s)(\\d{1,6})(?:\\s|$|[.,!])"
    )

    fun parse(
        rawTitle: String,
        description: String? = null,
        startMillis: Long = 0L,
        isAllDay: Boolean = false
    ): ParsedHabitDetails {
        var cleanTitle = rawTitle.trim()
        cleanTitle = PREFIX_REGEX.matcher(cleanTitle).replaceFirst("")
        cleanTitle = SUFFIX_REGEX.matcher(cleanTitle).replaceFirst("").trim()

        var detectedTarget: Int? = null
        var detectedUnit: String? = null
        var habitTitle = cleanTitle

        // 1. Try matching number + unit in title
        val titleUnitMatcher = NUMBER_WITH_UNIT_REGEX.matcher(cleanTitle)
        if (titleUnitMatcher.find()) {
            detectedTarget = titleUnitMatcher.group(1)?.toIntOrNull()
            val rawUnit = titleUnitMatcher.group(2)?.lowercase(Locale.ROOT) ?: "reps"
            detectedUnit = normalizeUnit(rawUnit)

            // Strip target and unit from title if title contains more context
            val remainder = (cleanTitle.substring(0, titleUnitMatcher.start()) + " " +
                    cleanTitle.substring(titleUnitMatcher.end()))
                .replace(Regex("\\b(of|for|to|the|a|an)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (remainder.length >= 3) {
                habitTitle = remainder
            } else if (rawUnit.contains("push") || rawUnit.contains("squat") || rawUnit.contains("pull") || rawUnit.contains("sit")) {
                habitTitle = capitalize(rawUnit.replace("-", ""))
            }
        }

        // 2. If no unit found in title, check description for number + unit
        if (detectedTarget == null && !description.isNullOrBlank()) {
            val descUnitMatcher = NUMBER_WITH_UNIT_REGEX.matcher(description)
            if (descUnitMatcher.find()) {
                detectedTarget = descUnitMatcher.group(1)?.toIntOrNull()
                val rawUnit = descUnitMatcher.group(2)?.lowercase(Locale.ROOT) ?: "reps"
                detectedUnit = normalizeUnit(rawUnit)
            }
        }

        // 3. If still no unit found, check title for standalone number
        if (detectedTarget == null) {
            val numMatcher = STANDALONE_NUMBER_REGEX.matcher(cleanTitle)
            if (numMatcher.find()) {
                val num = numMatcher.group(1)?.toIntOrNull()
                if (num != null && num > 0) {
                    detectedTarget = num
                    detectedUnit = inferUnitFromTitle(cleanTitle)
                    val remainder = (cleanTitle.substring(0, numMatcher.start()) + " " +
                            cleanTitle.substring(numMatcher.end()))
                        .replace(Regex("\\b(of|for|to|the|a|an)\\b", RegexOption.IGNORE_CASE), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (remainder.length >= 3) {
                        habitTitle = remainder
                    }
                }
            }
        }

        // 4. Infer fallback unit and sensible default target
        val finalUnit = detectedUnit ?: inferUnitFromTitle(habitTitle)
        val finalTarget = detectedTarget?.coerceAtLeast(1) ?: when (finalUnit) {
            "times", "sessions", "lessons" -> 1
            "hours" -> 1
            "mins" -> 15
            "glasses" -> 8
            "steps" -> 5000
            "km" -> 3
            "pages" -> 10
            else -> 10
        }

        // Clean up title
        habitTitle = habitTitle
            .replace(Regex("^(daily|everyday|my|do|complete)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { cleanTitle.ifEmpty { "Habit" } }

        // Time
        val (hour, minute) = if (!isAllDay && startMillis > 0L) {
            val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
            Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } else {
            Pair(21, 0) // Default 9:00 PM
        }

        return ParsedHabitDetails(
            title = capitalize(habitTitle),
            baseDailyTarget = finalTarget,
            unit = finalUnit,
            scheduledHour = hour,
            scheduledMinute = minute
        )
    }

    private fun normalizeUnit(unit: String): String {
        return when {
            unit.startsWith("rep") || unit.contains("up") || unit.contains("squat") -> "reps"
            unit.startsWith("page") -> "pages"
            unit.startsWith("glass") -> "glasses"
            unit.startsWith("step") -> "steps"
            unit.startsWith("min") -> "mins"
            unit.startsWith("hour") || unit.startsWith("hr") -> "hours"
            unit.startsWith("km") -> "km"
            unit.startsWith("mile") -> "miles"
            unit.startsWith("chapter") -> "chapters"
            unit.startsWith("session") -> "sessions"
            unit.startsWith("lesson") -> "lessons"
            unit.startsWith("pomodoro") -> "pomodoros"
            unit.startsWith("liter") || unit.startsWith("litre") || unit == "l" -> "liters"
            unit == "ml" -> "ml"
            else -> unit
        }
    }

    private fun inferUnitFromTitle(title: String): String {
        val lower = title.lowercase(Locale.ROOT)
        return when {
            lower.contains("pushup") || lower.contains("squat") || lower.contains("pullup") || lower.contains("situp") || lower.contains("workout") -> "reps"
            lower.contains("read") || lower.contains("book") || lower.contains("page") -> "pages"
            lower.contains("water") || lower.contains("drink") || lower.contains("hydrat") -> "glasses"
            lower.contains("walk") || lower.contains("step") -> "steps"
            lower.contains("run") || lower.contains("jog") -> "km"
            lower.contains("meditat") || lower.contains("stretch") -> "mins"
            lower.contains("code") || lower.contains("study") -> "hours"
            lower.contains("lesson") -> "lessons"
            lower.contains("session") -> "sessions"
            lower.contains("pomodoro") -> "pomodoros"
            else -> "times"
        }
    }

    private fun capitalize(str: String): String {
        if (str.isEmpty()) return str
        return str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}

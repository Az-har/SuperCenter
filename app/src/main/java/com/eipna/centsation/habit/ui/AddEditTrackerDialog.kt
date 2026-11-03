package com.eipna.centsation.habit.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eipna.centsation.habit.calendar.CalendarSyncManager
import com.eipna.centsation.habit.calendar.UserCalendar
import com.eipna.centsation.habit.data.entity.TrackerEntity
import java.util.Locale

@Composable
fun AddEditTrackerDialog(
    initialTracker: TrackerEntity? = null,
    availableCalendars: List<UserCalendar>,
    onRequestCalendarPermission: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        target: Int,
        unit: String,
        hour: Int,
        minute: Int,
        syncCalendar: Boolean,
        calendarId: Long?
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTracker?.title ?: "") }
    var targetText by remember { mutableStateOf(initialTracker?.baseDailyTarget?.toString() ?: "10") }
    var unit by remember { mutableStateOf(initialTracker?.unit ?: "reps") }
    var hour by remember { mutableIntStateOf(initialTracker?.scheduledHour ?: 21) }
    var minute by remember { mutableIntStateOf(initialTracker?.scheduledMinute ?: 0) }
    var syncCalendar by remember { mutableStateOf(initialTracker?.syncCalendar ?: false) }
    var selectedCalendarId by remember {
        mutableStateOf(
            initialTracker?.calendarId
                ?: availableCalendars.firstOrNull { it.isPrimary }?.id
                ?: availableCalendars.firstOrNull()?.id
        )
    }

    var calendarDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val formattedTime = remember(hour, minute) {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "AM" else "PM"
        String.format(Locale.getDefault(), "%02d:%02d %s", h, minute, amPm)
    }

    val hasCalendarPerms = CalendarSyncManager.hasCalendarPermissions(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialTracker == null) "New Habit & Quota" else "Edit Habit Tracker",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("Habit Title") },
                    placeholder = { Text("e.g. Pushups, Reading, Hydration") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = {
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                targetText = it
                            }
                        },
                        label = { Text("Daily Target") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        placeholder = { Text("reps") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Daily Reminder Time",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, selectedHour, selectedMinute ->
                                hour = selectedHour
                                minute = selectedMinute
                            },
                            hour,
                            minute,
                            false
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Scheduled at: $formattedTime")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Google Calendar Sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync with Google Calendar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Blocks off daily quota time on device calendar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncCalendar,
                        onCheckedChange = { checked ->
                            syncCalendar = checked
                            if (checked && !hasCalendarPerms) {
                                onRequestCalendarPermission()
                            }
                        }
                    )
                }

                if (syncCalendar) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!hasCalendarPerms) {
                        OutlinedButton(
                            onClick = onRequestCalendarPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Calendar Access")
                        }
                    } else if (availableCalendars.isEmpty()) {
                        Text(
                            text = "No on-device Google calendars found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        val currentCal = availableCalendars.firstOrNull { it.id == selectedCalendarId }
                            ?: availableCalendars.first()

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Selected Calendar:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { calendarDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${currentCal.displayName} (${currentCal.accountName})",
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = calendarDropdownExpanded,
                                onDismissRequest = { calendarDropdownExpanded = false }
                            ) {
                                availableCalendars.forEach { cal ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = cal.displayName,
                                                    fontWeight = if (cal.id == selectedCalendarId) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = cal.accountName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedCalendarId = cal.id
                                            calendarDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Please enter a habit title"
                        return@TextButton
                    }
                    val target = targetText.toIntOrNull()
                    if (target == null || target <= 0) {
                        errorMessage = "Target must be at least 1"
                        return@TextButton
                    }

                    onSave(
                        title.trim(),
                        target,
                        unit.trim().ifEmpty { "reps" },
                        hour,
                        minute,
                        syncCalendar,
                        if (syncCalendar) selectedCalendarId else null
                    )
                }
            ) {
                Text(if (initialTracker == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package com.eipna.centsation.habit.ui

import android.app.TimePickerDialog
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.eipna.centsation.habit.data.entity.TrackerEntity
import java.util.Locale

@Composable
fun AddEditTrackerDialog(
    initialTracker: TrackerEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        target: Int,
        unit: String,
        hour: Int,
        minute: Int
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTracker?.title ?: "") }
    var targetText by remember { mutableStateOf(initialTracker?.baseDailyTarget?.toString() ?: "10") }
    var unit by remember { mutableStateOf(initialTracker?.unit ?: "reps") }
    var hour by remember { mutableIntStateOf(initialTracker?.scheduledHour ?: 21) }
    var minute by remember { mutableIntStateOf(initialTracker?.scheduledMinute ?: 0) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val formattedTime = remember(hour, minute) {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "AM" else "PM"
        String.format(Locale.getDefault(), "%02d:%02d %s", h, minute, amPm)
    }

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
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("Habit Title") },
                    placeholder = { Text("e.g. Pushups, Read Book, Drink Water") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Target & Unit Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = {
                            targetText = it.filter { ch -> ch.isDigit() }
                            errorMessage = null
                        },
                        label = { Text("Daily Target") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = {
                            unit = it
                            errorMessage = null
                        },
                        label = { Text("Unit") },
                        placeholder = { Text("reps, pages, mins") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick unit suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("reps", "pages", "glasses", "mins", "steps").forEach { suggestion ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (unit.equals(suggestion, ignoreCase = true)) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .weight(1f),
                            onClick = { unit = suggestion }
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (unit.equals(suggestion, ignoreCase = true)) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Daily Reminder Time
                Text(
                    text = "Daily Reminder & Reset Time",
                    style = MaterialTheme.typography.labelLarge,
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
                        minute
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

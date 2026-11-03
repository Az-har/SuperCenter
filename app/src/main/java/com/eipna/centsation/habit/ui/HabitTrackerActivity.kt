package com.eipna.centsation.habit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.eipna.centsation.habit.notification.TrackerNotificationHandler
import com.eipna.centsation.habit.ui.theme.SuperCenterTheme

class HabitTrackerActivity : ComponentActivity() {

    private val viewModel: HabitViewModel by viewModels()

    private val requestCalendarPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.loadCalendars()
            Toast.makeText(this, "Calendar access granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Calendar permissions are required to sync habit events.", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Enable notifications in settings to receive daily quota reminders.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channel
        TrackerNotificationHandler.createNotificationChannel(this)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            SuperCenterTheme {
                HabitDashboardScreen(
                    viewModel = viewModel,
                    onRequestCalendarPermission = {
                        requestCalendarPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    },
                    onBackClicked = { finish() }
                )
            }
        }
    }
}

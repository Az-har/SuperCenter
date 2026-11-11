package com.eipna.centsation.habit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.eipna.centsation.CentsationApp
import com.eipna.centsation.habit.ui.theme.SuperCenterTheme
import com.eipna.centsation.util.PreferenceUtil

class HabitTrackerActivity : ComponentActivity() {

    private val viewModel: HabitViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Enable notifications in settings to receive daily quota reminders.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferenceUtil = PreferenceUtil(this)
        CentsationApp.applyTheme(preferenceUtil.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (preferenceUtil.isScreenPrivacyEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

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
                    onBackClicked = { finish() }
                )
            }
        }
    }
}

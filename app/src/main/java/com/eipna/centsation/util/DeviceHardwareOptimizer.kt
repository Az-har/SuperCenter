package com.eipna.centsation.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import java.util.Locale
import kotlin.math.max

/**
 * Hardware and battery telemetry optimizer.
 * Specially tuned for OriginOS and Vivo/iQOO devices (such as iQOO Neo 9 Pro),
 * providing adaptive background throttling, thermal protection, and direct
 * navigation to OriginOS background power & autostart managers.
 */
object DeviceHardwareOptimizer {

    private const val TAG = "HardwareOptimizer"

    @Volatile
    private var hasResolvedOriginOsVersion = false
    @Volatile
    private var cachedOriginOsVersion: String? = null
    @Volatile
    private var initialSnapshot: BatterySnapshot? = null

    data class BatterySnapshot(
        val level: Int,
        val scale: Int,
        val percentage: Int,
        val temperatureCelsius: Float,
        val isCharging: Boolean,
        val isFastCharging: Boolean,
        val isPowerSaveMode: Boolean,
        val health: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class PerformanceMode {
        HIGH_PERFORMANCE,
        BALANCED,
        POWER_SAVING,
        THERMAL_THROTTLE
    }

    val isVivoOrIqoo: Boolean
        get() {
            val m = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
            val b = (Build.BRAND ?: "").lowercase(Locale.ROOT)
            return m.contains("vivo") || m.contains("iqoo") || b.contains("vivo") || b.contains("iqoo")
        }

    val isIqooNeo9Pro: Boolean
        get() {
            val model = (Build.MODEL ?: "").lowercase(Locale.ROOT)
            val product = (Build.PRODUCT ?: "").lowercase(Locale.ROOT)
            val device = (Build.DEVICE ?: "").lowercase(Locale.ROOT)
            return model.contains("i2302") || model.contains("v2339") ||
                    product.contains("neo9") || product.contains("neo 9") ||
                    device.contains("neo9")
        }

    fun getDeviceDisplayName(): String {
        return if (isIqooNeo9Pro) {
            "iQOO Neo 9 Pro (Snapdragon 8 Gen 2)"
        } else if (isVivoOrIqoo) {
            "${(Build.BRAND ?: "Vivo").uppercase(Locale.ROOT)} ${Build.MODEL ?: ""}".trim()
        } else {
            "${Build.MANUFACTURER ?: "Android"} ${Build.MODEL ?: "Device"}".trim()
        }
    }

    fun getOriginOsVersion(): String? {
        if (hasResolvedOriginOsVersion) return cachedOriginOsVersion
        val resolved = getSystemProperty("ro.vivo.os.version")
            ?: getSystemProperty("ro.vivo.os.name")
            ?: getSystemProperty("ro.iqoo.os.version")
            ?: if (isVivoOrIqoo) "OriginOS / Funtouch" else null
        cachedOriginOsVersion = resolved
        hasResolvedOriginOsVersion = true
        return resolved
    }

    @Suppress("PrivateApi")
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val value = (method.invoke(null, key, "") as? String)?.trim()
            if (!value.isNullOrEmpty()) value else null
        } catch (_: Exception) {
            null
        }
    }

    @JvmOverloads
    fun getBatterySnapshot(context: Context, recordTelemetry: Boolean = true): BatterySnapshot {
        val appContext = context.applicationContext
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = appContext.registerReceiver(null, filter)

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            50
        }

        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = rawTemp / 10.0f

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val isFastCharging = isCharging && (plugged == BatteryManager.BATTERY_PLUGGED_AC)

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true

        val rawHealth = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val healthStr = when (rawHealth) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Normal"
        }

        val snapshot = BatterySnapshot(
            level = level,
            scale = scale,
            percentage = percentage,
            temperatureCelsius = tempCelsius,
            isCharging = isCharging,
            isFastCharging = isFastCharging,
            isPowerSaveMode = isPowerSave,
            health = healthStr
        )

        if (recordTelemetry && initialSnapshot == null) {
            initialSnapshot = snapshot
        }
        return snapshot
    }

    internal fun evaluatePerformanceMode(snapshot: BatterySnapshot): PerformanceMode {
        return when {
            snapshot.temperatureCelsius >= 41.0f -> PerformanceMode.THERMAL_THROTTLE
            snapshot.isPowerSaveMode || snapshot.percentage < 20 -> PerformanceMode.POWER_SAVING
            snapshot.isFastCharging -> PerformanceMode.HIGH_PERFORMANCE
            else -> PerformanceMode.BALANCED
        }
    }

    fun getPerformanceMode(context: Context): PerformanceMode {
        return evaluatePerformanceMode(getBatterySnapshot(context, recordTelemetry = false))
    }

    /**
     * Determines the optimal debounce window for widget Binder IPC.
     * Conserves battery and prevents launcher lag on OriginOS.
     */
    fun getAdaptiveWidgetDebounceMs(context: Context): Long {
        return when (getPerformanceMode(context)) {
            PerformanceMode.THERMAL_THROTTLE -> 1500L
            PerformanceMode.POWER_SAVING -> 1000L
            PerformanceMode.HIGH_PERFORMANCE -> 100L
            PerformanceMode.BALANCED -> 250L
        }
    }

    /**
     * Open OriginOS-specific background power management or auto-start settings.
     * Prevents OriginOS from killing widget sync and habit alarms.
     */
    fun openOriginOsPowerSettings(context: Context): Boolean {
        val intentsToTry = listOf(
            // Vivo Excessive Background Power Manager
            Intent().apply {
                component = ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // iQOO / Vivo Background Start Manager
            Intent().apply {
                component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // iQOO Add Whitelist Activity
            Intent().apply {
                component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Vivo Permission Manager Autostart
            Intent().apply {
                component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // General Battery Optimization Settings (policy-compliant)
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in intentsToTry) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch intent: ${intent.component}", e)
            }
        }

        // Final fallback: App Details Settings
        return try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetailsIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getBatteryLogSummary(context: Context): String {
        val initial = initialSnapshot ?: return "Collecting initial battery telemetry..."
        val current = getBatterySnapshot(context, recordTelemetry = false)
        val durationMin = max(1L, (current.timestamp - initial.timestamp) / 60000L)
        val drain = initial.percentage - current.percentage
        return when {
            drain > 0 -> "Drain: -$drain% over ${durationMin}m"
            drain < 0 -> "Charging: +${-drain}% over ${durationMin}m"
            else -> "Stable: 0% drain over ${durationMin}m"
        }
    }
}

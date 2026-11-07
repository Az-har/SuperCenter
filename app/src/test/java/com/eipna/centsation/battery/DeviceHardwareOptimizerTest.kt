package com.eipna.centsation.battery

import com.eipna.centsation.util.DeviceHardwareOptimizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHardwareOptimizerTest {

    @Test
    fun testBatterySnapshotDataIntegrity() {
        val snapshot = DeviceHardwareOptimizer.BatterySnapshot(
            level = 85,
            scale = 100,
            percentage = 85,
            temperatureCelsius = 32.5f,
            isCharging = false,
            isFastCharging = false,
            isPowerSaveMode = false,
            health = "Good"
        )

        assertEquals(85, snapshot.percentage)
        assertEquals(32.5f, snapshot.temperatureCelsius, 0.01f)
        assertEquals("Good", snapshot.health)
        assertTrue(snapshot.timestamp > 0)
    }

    @Test
    fun testPerformanceModesEnumCompleteness() {
        val modes = DeviceHardwareOptimizer.PerformanceMode.values()
        assertTrue(modes.contains(DeviceHardwareOptimizer.PerformanceMode.HIGH_PERFORMANCE))
        assertTrue(modes.contains(DeviceHardwareOptimizer.PerformanceMode.BALANCED))
        assertTrue(modes.contains(DeviceHardwareOptimizer.PerformanceMode.POWER_SAVING))
        assertTrue(modes.contains(DeviceHardwareOptimizer.PerformanceMode.THERMAL_THROTTLE))
    }

    @Test
    fun testDeviceDisplayName_ReturnsNonEmpty() {
        val name = DeviceHardwareOptimizer.getDeviceDisplayName()
        assertNotNull(name)
        assertTrue(name.isNotEmpty())
    }
}

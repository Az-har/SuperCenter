package com.eipna.centsation.habit

import com.eipna.centsation.habit.widget.HabitWidgetSyncManager
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitWidgetCycleTest {

    @Test
    fun computeNextTrackerIndex_cyclesNormally() {
        assertEquals(1, HabitWidgetSyncManager.computeNextTrackerIndex(0, 3))
        assertEquals(2, HabitWidgetSyncManager.computeNextTrackerIndex(1, 3))
        assertEquals(0, HabitWidgetSyncManager.computeNextTrackerIndex(2, 3))
    }

    @Test
    fun computeNextTrackerIndex_staleHighIndexDoesNotGetStuck() {
        // After deleting the 3rd habit while saved index was 2, UI coerces display to 1 ("2 of 2").
        // Tapping next must wrap to 0 ("1 of 2"), not (2 + 1) % 2 == 1.
        assertEquals(0, HabitWidgetSyncManager.computeNextTrackerIndex(2, 2))
        assertEquals(0, HabitWidgetSyncManager.computeNextTrackerIndex(9, 2))
    }

    @Test
    fun computeNextTrackerIndex_handlesSingleOrEmptyAndNegativeSafely() {
        assertEquals(0, HabitWidgetSyncManager.computeNextTrackerIndex(0, 0))
        assertEquals(0, HabitWidgetSyncManager.computeNextTrackerIndex(3, 1))
        assertEquals(1, HabitWidgetSyncManager.computeNextTrackerIndex(-1, 2))
    }
}

package app.allinonereminder.schedule

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScheduleModuleMarkerTest {
    @Test
    fun scheduleModuleMarker_defaultValues() {
        val marker = ScheduleModuleMarker()
        assertThat(marker.initialized).isTrue()
    }
}


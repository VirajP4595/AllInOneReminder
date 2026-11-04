package app.allinonereminder.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataModuleMarkerTest {
    @Test
    fun dataModuleMarker_defaultValues() {
        val marker = DataModuleMarker()
        assertThat(marker.initialized).isTrue()
    }
}


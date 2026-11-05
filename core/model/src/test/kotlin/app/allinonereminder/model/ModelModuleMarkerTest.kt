package app.allinonereminder.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelModuleMarkerTest {
    @Test
    fun modelModuleMarker_defaultValues() {
        val marker = ModelModuleMarker()
        assertThat(marker.initialized).isTrue()
    }
}


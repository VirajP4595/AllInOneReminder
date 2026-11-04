package app.allinonereminder.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.UUID

class UuidV5Test {
    @Test
    fun generate_isDeterministic() {
        val ns = UUID.randomUUID()
        val name = "schedule-123_2026-11-04T08:30:00"
        val id1 = UuidV5.generate(ns, name)
        val id2 = UuidV5.generate(ns, name)
        assertThat(id1).isEqualTo(id2)

        val uuid = UUID.fromString(id1)
        assertThat(uuid.version()).isEqualTo(5)
        assertThat(uuid.variant()).isEqualTo(2) // RFC 4122
    }

    @Test
    fun generate_differentNamesProduceDifferentIds() {
        val ns = UuidV5.OCCURRENCE_NAMESPACE
        val id1 = UuidV5.generate(ns, "name1")
        val id2 = UuidV5.generate(ns, "name2")
        assertThat(id1).isNotEqualTo(id2)
    }
}


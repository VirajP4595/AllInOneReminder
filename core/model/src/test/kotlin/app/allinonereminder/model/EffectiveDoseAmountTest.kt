package app.allinonereminder.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EffectiveDoseAmountTest {

    private fun item(dosageAmount: String = "500") = Item(
        id = "item-1",
        profileId = "profile-1",
        type = ItemType.MEDICINE,
        name = "Aspirin",
        dosageAmount = dosageAmount,
        dosageUnit = "mg",
        homeZoneId = "UTC",
        startDate = "2026-01-01",
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun schedule(doseAmountOverride: String?) = Schedule(
        id = "sched-1",
        itemId = "item-1",
        anchorType = ScheduleAnchorType.FIXED,
        recurrence = RecurrenceType.DAILY,
        anchorDate = "2026-01-01",
        doseAmountOverride = doseAmountOverride,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun effectiveDoseAmount_usesScheduleOverrideWhenPresent() {
        assertThat(effectiveDoseAmount(schedule("250"), item("500"))).isEqualTo("250")
    }

    @Test
    fun effectiveDoseAmount_fallsBackToItemDoseWhenOverrideMissing() {
        assertThat(effectiveDoseAmount(schedule(null), item("500"))).isEqualTo("500")
    }

    @Test
    fun effectiveDoseAmount_fallsBackToItemDoseWhenOverrideBlank() {
        assertThat(effectiveDoseAmount(schedule("   "), item("500"))).isEqualTo("500")
    }

    @Test
    fun effectiveDoseAmount_fallsBackToItemDoseWhenScheduleIsNull() {
        assertThat(effectiveDoseAmount(null, item("500"))).isEqualTo("500")
    }
}

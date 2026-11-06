package app.allinonereminder.ui.items

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.data.repository.StockEventRepository
import app.allinonereminder.model.*
import app.allinonereminder.ui.util.PhotoStorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class ScheduleDraft(
    val id: String = UUID.randomUUID().toString(),
    var anchorType: ScheduleAnchorType = ScheduleAnchorType.FIXED,
    var minuteOfDay: Int = 8 * 60, // 08:00 AM
    var meal: MealType? = MealType.BREAKFAST,
    var relation: MealRelation? = MealRelation.WITH,
    var offsetMinutes: Int? = 0,
    var recurrence: RecurrenceType = RecurrenceType.DAILY,
    var intervalN: Int? = 1,
    var weekdayMask: Int? = null,
    var dayOfMonth: Int = 1,
    var monthlyByWeekday: Boolean = false,
    var nthWeek: Int = 1,
    var intervalHours: Int = 4,
    var windowStartMinute: Int = 8 * 60,
    var windowEndMinute: Int = 20 * 60,
    var onceDate: String? = null,
    var validFrom: String? = null,
    var validUntil: String? = null,
    var doseAmountOverride: String? = null
)

data class ItemEditorUiState(
    val isEditMode: Boolean = false,
    val itemId: String = UUID.randomUUID().toString(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String = "",
    val type: ItemType = ItemType.MEDICINE,
    val name: String = "",
    val dosageAmount: String = "1",
    val dosageUnit: String = "tablet",
    val form: String = "pill",
    val instructions: String = "",
    val criticality: Criticality = Criticality.NORMAL,
    val exerciseMinutes: Int = 30,
    val startDate: String = LocalDate.now(ZoneId.systemDefault()).toString(),
    val endDate: String? = null,
    val timePolicy: TimePolicy = TimePolicy.LOCAL_WALL_CLOCK,
    val homeZoneId: String = ZoneId.systemDefault().id,
    val graceMinutes: Int = 60,
    val reRingMinutes: Int = 5,
    val minGapMinutes: Int = 240,
    val maxPerDay: Int? = null,
    val currentStock: String = "",
    val unitsPerDose: String = "1.0",
    val refillThreshold: String = "",
    val schedules: List<ScheduleDraft> = listOf(ScheduleDraft()),
    val photos: List<ItemPhoto> = emptyList(),
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false
)

@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val scheduleRepository: ScheduleRepository,
    private val profileRepository: ProfileRepository,
    private val stockEventRepository: StockEventRepository? = null,
    private val settingsRepository: SettingsRepository,
    private val alarmCoordinator: AlarmCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editItemId: String? = savedStateHandle["itemId"]

    private val _uiState = MutableStateFlow(ItemEditorUiState())
    val uiState: StateFlow<ItemEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profiles = profileRepository.getActiveProfiles().firstOrNull() ?: emptyList()
            val initialProfileId = profiles.firstOrNull()?.id ?: ""

            if (!editItemId.isNullOrBlank()) {
                val item = itemRepository.getItemSync(editItemId)
                val scheds = scheduleRepository.getActiveSchedules(editItemId).firstOrNull() ?: emptyList()
                val photos = itemRepository.getPhotosSync(editItemId)

                if (item != null) {
                    val stock = if (stockEventRepository?.hasStockEvents(item.id) == true) stockEventRepository.getCurrentStockSync(item.id) else null
                    val currentStockStr = stock?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
                    val unitsStr = item.unitsPerDose?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "1.0"
                    val thresholdStr = item.refillThreshold?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""

                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            itemId = item.id,
                            profiles = profiles,
                            selectedProfileId = item.profileId,
                            type = item.type,
                            name = item.name,
                            dosageAmount = item.dosageAmount,
                            dosageUnit = item.dosageUnit,
                            form = item.form ?: "",
                            instructions = item.instructions ?: "",
                            criticality = item.criticality,
                            exerciseMinutes = item.exerciseTargetMinutes ?: 30,
                            startDate = item.startDate,
                            endDate = item.endDate,
                            timePolicy = item.timePolicy,
                            homeZoneId = item.homeZoneId,
                            graceMinutes = item.graceMinutes,
                            reRingMinutes = item.reRingMinutes,
                            minGapMinutes = item.minGapMinutes ?: 240,
                            maxPerDay = item.maxPerDay,
                            currentStock = currentStockStr,
                            unitsPerDose = unitsStr,
                            refillThreshold = thresholdStr,
                            schedules = scheds.map { s ->
                                ScheduleDraft(
                                    id = s.id,
                                    anchorType = s.anchorType,
                                    minuteOfDay = s.minuteOfDay ?: (8 * 60),
                                    meal = s.meal,
                                    relation = s.relation,
                                    offsetMinutes = s.offsetMinutes,
                                    recurrence = s.recurrence,
                                    intervalN = s.intervalN,
                                    weekdayMask = s.weekdayMask,
                                    dayOfMonth = s.dayOfMonth ?: 1,
                                    monthlyByWeekday = s.nthWeek != null,
                                    nthWeek = s.nthWeek ?: 1,
                                    intervalHours = s.intervalHours ?: 4,
                                    windowStartMinute = s.windowStartMinute ?: (s.minuteOfDay ?: (8 * 60)),
                                    windowEndMinute = s.windowEndMinute ?: (20 * 60),
                                    onceDate = s.onceDate,
                                    validFrom = s.validFrom,
                                    validUntil = s.validUntil,
                                    doseAmountOverride = s.doseAmountOverride
                                )
                            }.ifEmpty { listOf(ScheduleDraft()) },
                            photos = photos
                        )
                    }
                    return@launch
                }
            }

            _uiState.update {
                it.copy(
                    profiles = profiles,
                    selectedProfileId = initialProfileId
                )
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateType(type: ItemType) = _uiState.update { it.copy(type = type) }
    fun updateProfile(profileId: String) = _uiState.update { it.copy(selectedProfileId = profileId) }
    fun updateDosageAmount(amount: String) = _uiState.update { it.copy(dosageAmount = amount) }
    fun updateDosageUnit(unit: String) = _uiState.update { it.copy(dosageUnit = unit) }
    fun updateForm(form: String) = _uiState.update { it.copy(form = form) }
    fun updateInstructions(instructions: String) = _uiState.update { it.copy(instructions = instructions) }
    fun updateCriticality(crit: Criticality) = _uiState.update { it.copy(criticality = crit) }
    fun updateExerciseMinutes(min: Int) = _uiState.update { it.copy(exerciseMinutes = min) }
    fun updateTimePolicy(tp: TimePolicy) = _uiState.update { it.copy(timePolicy = tp) }
    fun updateGraceMinutes(m: Int) = _uiState.update { it.copy(graceMinutes = m) }
    fun updateReRingMinutes(m: Int) = _uiState.update { it.copy(reRingMinutes = m) }
    fun updateMinGapMinutes(m: Int) = _uiState.update { it.copy(minGapMinutes = m) }
    fun updateMaxPerDay(value: String) = _uiState.update {
        it.copy(maxPerDay = value.toIntOrNull()?.takeIf { n -> n > 0 })
    }

    fun addSchedule() {
        _uiState.update { it.copy(schedules = it.schedules + ScheduleDraft()) }
    }

    fun removeSchedule(scheduleId: String) {
        _uiState.update {
            val list = it.schedules.filterNot { s -> s.id == scheduleId }
            it.copy(schedules = list.ifEmpty { listOf(ScheduleDraft()) })
        }
    }

    fun updateSchedule(scheduleId: String, update: (ScheduleDraft) -> ScheduleDraft) {
        _uiState.update { state ->
            val updated = state.schedules.map { s -> if (s.id == scheduleId) update(s) else s }
            state.copy(schedules = updated)
        }
    }

    fun onPhotoAdded(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val photoId = PhotoStorageHelper.saveAndCompressPhoto(context, uri)
                val targetFile = PhotoStorageHelper.getPhotoFile(context, photoId)
                val newPhoto = ItemPhoto(
                    id = photoId,
                    itemId = _uiState.value.itemId,
                    filePath = targetFile.absolutePath,
                    sortOrder = _uiState.value.photos.size,
                    createdAt = System.currentTimeMillis()
                )
                itemRepository.addPhoto(newPhoto)
                _uiState.update { it.copy(photos = it.photos + newPhoto) }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun removePhoto(context: Context, photoId: String) {
        viewModelScope.launch {
            PhotoStorageHelper.deletePhoto(context, photoId)
            itemRepository.deletePhoto(photoId)
            _uiState.update { it.copy(photos = it.photos.filterNot { p -> p.id == photoId }) }
        }
    }

    fun saveItem() {
        val state = _uiState.value
        if (state.name.isBlank() || state.selectedProfileId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val now = System.currentTimeMillis()
            val deviceId = settingsRepository.getOrCreateDeviceId()

            val item = Item(
                id = state.itemId,
                profileId = state.selectedProfileId,
                type = state.type,
                name = state.name.trim(),
                dosageAmount = state.dosageAmount.trim(),
                dosageUnit = state.dosageUnit.trim(),
                form = state.form.trim().ifEmpty { null },
                instructions = state.instructions.trim().ifEmpty { null },
                criticality = state.criticality,
                exerciseTargetMinutes = if (state.type == ItemType.EXERCISE) state.exerciseMinutes else null,
                startDate = state.startDate,
                endDate = state.endDate,
                timePolicy = state.timePolicy,
                homeZoneId = state.homeZoneId,
                graceMinutes = state.graceMinutes,
                reRingMinutes = state.reRingMinutes,
                minGapMinutes = state.minGapMinutes,
                maxPerDay = state.maxPerDay,
                unitsPerDose = state.unitsPerDose.toDoubleOrNull() ?: 1.0,
                refillThreshold = state.refillThreshold.toDoubleOrNull(),
                createdAt = now,
                updatedAt = now
            )

            if (state.isEditMode) {
                itemRepository.updateItem(item, deviceId)
            } else {
                itemRepository.createItem(item, deviceId)
            }

            // Save schedules
            val existingSchedules = scheduleRepository.getActiveSchedules(state.itemId).firstOrNull() ?: emptyList()
            val currentScheduleIds = state.schedules.map { it.id }.toSet()
            // Delete removed schedules
            existingSchedules.filterNot { it.id in currentScheduleIds }.forEach { s ->
                scheduleRepository.deleteSchedule(s.id, deviceId)
            }

            state.schedules.forEach { draft ->
                val schedule = Schedule(
                    id = draft.id,
                    itemId = state.itemId,
                    anchorType = draft.anchorType,
                    minuteOfDay = if (draft.anchorType == ScheduleAnchorType.FIXED) draft.minuteOfDay else null,
                    meal = if (draft.anchorType == ScheduleAnchorType.MEAL) draft.meal else null,
                    relation = if (draft.anchorType == ScheduleAnchorType.MEAL) draft.relation else null,
                    offsetMinutes = if (draft.anchorType == ScheduleAnchorType.MEAL) draft.offsetMinutes else null,
                    recurrence = draft.recurrence,
                    weekdayMask = when (draft.recurrence) {
                        RecurrenceType.EVERY_N_WEEKS -> draft.weekdayMask ?: 127
                        RecurrenceType.MONTHLY -> if (draft.monthlyByWeekday) {
                            draft.weekdayMask?.takeIf { it != 0 } ?: 2
                        } else {
                            null
                        }
                        else -> null
                    },
                    intervalN = if (draft.recurrence == RecurrenceType.EVERY_N_DAYS ||
                        draft.recurrence == RecurrenceType.EVERY_N_WEEKS
                    ) {
                        draft.intervalN?.coerceAtLeast(1) ?: 1
                    } else {
                        null
                    },
                    anchorDate = state.startDate,
                    onceDate = if (draft.recurrence == RecurrenceType.ONCE) {
                        draft.onceDate ?: state.startDate
                    } else {
                        null
                    },
                    dayOfMonth = if (draft.recurrence == RecurrenceType.MONTHLY && !draft.monthlyByWeekday) {
                        draft.dayOfMonth.coerceIn(1, 31)
                    } else {
                        null
                    },
                    nthWeek = if (draft.recurrence == RecurrenceType.MONTHLY && draft.monthlyByWeekday) {
                        draft.nthWeek.coerceIn(1, 5)
                    } else {
                        null
                    },
                    intervalHours = if (draft.recurrence == RecurrenceType.INTERVAL) {
                        draft.intervalHours.coerceAtLeast(1)
                    } else {
                        null
                    },
                    windowStartMinute = if (draft.recurrence == RecurrenceType.INTERVAL) draft.windowStartMinute else null,
                    windowEndMinute = if (draft.recurrence == RecurrenceType.INTERVAL) draft.windowEndMinute else null,
                    validFrom = draft.validFrom?.takeIf { it.isNotBlank() },
                    validUntil = draft.validUntil?.takeIf { it.isNotBlank() },
                    doseAmountOverride = draft.doseAmountOverride?.takeIf { it.isNotBlank() },
                    createdAt = now,
                    updatedAt = now
                )
                val exists = existingSchedules.any { it.id == draft.id }
                if (exists) {
                    scheduleRepository.updateSchedule(schedule, deviceId)
                } else {
                    scheduleRepository.createSchedule(schedule, deviceId)
                }
            }

            // Adjust stock if current stock was entered
            if (state.currentStock.isNotBlank()) {
                val enteredStock = state.currentStock.toDoubleOrNull()
                if (enteredStock != null) {
                    stockEventRepository?.adjustStock(item.id, enteredStock, now)
                }
            }

            // Reschedule alarms immediately
            alarmCoordinator.reschedule("item_editor_saved")

            _uiState.update { it.copy(isSaving = false, saveCompleted = true) }
        }
    }

    fun updateCurrentStock(stock: String) {
        _uiState.update { it.copy(currentStock = stock) }
    }

    fun updateUnitsPerDose(units: String) {
        _uiState.update { it.copy(unitsPerDose = units) }
    }

    fun updateRefillThreshold(threshold: String) {
        _uiState.update { it.copy(refillThreshold = threshold) }
    }
}

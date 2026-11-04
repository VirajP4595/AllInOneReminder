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
    var intervalN: Int? = 1
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
                            schedules = scheds.map { s ->
                                ScheduleDraft(
                                    id = s.id,
                                    anchorType = s.anchorType,
                                    minuteOfDay = s.minuteOfDay ?: (8 * 60),
                                    meal = s.meal,
                                    relation = s.relation,
                                    offsetMinutes = s.offsetMinutes,
                                    recurrence = s.recurrence,
                                    intervalN = s.intervalN
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
            val deviceId = "local-device"

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
                    intervalN = if (draft.recurrence == RecurrenceType.EVERY_N_DAYS || draft.recurrence == RecurrenceType.EVERY_N_WEEKS) draft.intervalN else null,
                    anchorDate = state.startDate,
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

            // Reschedule alarms immediately
            alarmCoordinator.reschedule("item_editor_saved")

            _uiState.update { it.copy(isSaving = false, saveCompleted = true) }
        }
    }
}

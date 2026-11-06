package app.allinonereminder.ui.appointments

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.repository.AppointmentRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.Appointment
import app.allinonereminder.model.AppointmentAttachment
import app.allinonereminder.model.AppointmentPrep
import app.allinonereminder.model.AppointmentStatus
import app.allinonereminder.model.Profile
import app.allinonereminder.ui.util.AttachmentStorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class AppointmentEditorUiState(
    val isEditMode: Boolean = false,
    val appointmentId: String = UUID.randomUUID().toString(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String = "",
    val doctor: String = "",
    val place: String = "",
    val date: LocalDate = LocalDate.now(ZoneId.systemDefault()).plusDays(1),
    val timeHour: Int = 10,
    val timeMinute: Int = 0,
    val purpose: String = "",
    val status: AppointmentStatus = AppointmentStatus.UPCOMING,
    val notes: String = "",
    val prescriptionNotes: String = "",
    val remindMinutesList: Set<Int> = setOf(120, 30),
    val preps: List<AppointmentPrep> = emptyList(),
    val existingAttachments: List<AppointmentAttachment> = emptyList(),
    val pendingAttachmentUris: List<Uri> = emptyList(),
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false
)

@HiltViewModel
class AppointmentEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appointmentRepository: AppointmentRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmCoordinator: AlarmCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editAppointmentId: String? = savedStateHandle["appointmentId"]

    private val _uiState = MutableStateFlow(AppointmentEditorUiState())
    val uiState: StateFlow<AppointmentEditorUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val profiles = profileRepository.getActiveProfiles().firstOrNull() ?: emptyList()
            val initialProfileId = profiles.firstOrNull()?.id ?: ""

            if (editAppointmentId != null && editAppointmentId.isNotBlank()) {
                val appt = appointmentRepository.getAppointmentSync(editAppointmentId)
                if (appt != null) {
                    val zdt = Instant.ofEpochMilli(appt.startAt).atZone(ZoneId.systemDefault())
                    val preps = appointmentRepository.getPrepsForAppointmentSync(editAppointmentId)
                    val attachments = appointmentRepository.getAttachmentsForAppointmentSync(editAppointmentId)
                    val parsedRemindMinutes = appt.remindMinutesBefore
                        .split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .toSet()

                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            appointmentId = appt.id,
                            profiles = profiles,
                            selectedProfileId = appt.profileId,
                            doctor = appt.doctor,
                            place = appt.place.orEmpty(),
                            date = zdt.toLocalDate(),
                            timeHour = zdt.hour,
                            timeMinute = zdt.minute,
                            purpose = appt.purpose.orEmpty(),
                            status = appt.status,
                            notes = appt.notes.orEmpty(),
                            prescriptionNotes = appt.prescriptionNotes.orEmpty(),
                            remindMinutesList = if (parsedRemindMinutes.isNotEmpty()) parsedRemindMinutes else setOf(120, 30),
                            preps = preps,
                            existingAttachments = attachments
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

    fun onDoctorChange(doctor: String) = _uiState.update { it.copy(doctor = doctor) }
    fun onPlaceChange(place: String) = _uiState.update { it.copy(place = place) }
    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }
    fun onTimeChange(hour: Int, minute: Int) = _uiState.update { it.copy(timeHour = hour, timeMinute = minute) }
    fun onPurposeChange(purpose: String) = _uiState.update { it.copy(purpose = purpose) }
    fun onProfileSelected(profileId: String) = _uiState.update { it.copy(selectedProfileId = profileId) }
    fun onNotesChange(notes: String) = _uiState.update { it.copy(notes = notes) }
    fun onPrescriptionNotesChange(notes: String) = _uiState.update { it.copy(prescriptionNotes = notes) }
    fun onStatusChange(status: AppointmentStatus) = _uiState.update { it.copy(status = status) }

    fun toggleRemindMinute(minutes: Int) {
        _uiState.update {
            val current = it.remindMinutesList.toMutableSet()
            if (current.contains(minutes)) {
                current.remove(minutes)
            } else {
                current.add(minutes)
            }
            it.copy(remindMinutesList = current)
        }
    }

    fun addPrep(text: String, minutesBefore: Int) {
        if (text.isBlank()) return
        val newPrep = AppointmentPrep(
            id = UUID.randomUUID().toString(),
            appointmentId = _uiState.value.appointmentId,
            text = text.trim(),
            minutesBefore = minutesBefore
        )
        _uiState.update { it.copy(preps = it.preps + newPrep) }
    }

    fun removePrep(prepId: String) {
        _uiState.update { it.copy(preps = it.preps.filter { p -> p.id != prepId }) }
    }

    fun addPendingAttachmentUri(uri: Uri) {
        _uiState.update { it.copy(pendingAttachmentUris = it.pendingAttachmentUris + uri) }
    }

    fun removePendingAttachmentUri(uri: Uri) {
        _uiState.update { it.copy(pendingAttachmentUris = it.pendingAttachmentUris.filter { u -> u != uri }) }
    }

    fun deleteExistingAttachment(attachment: AppointmentAttachment) {
        viewModelScope.launch {
            appointmentRepository.deleteAttachment(attachment.id)
            AttachmentStorageHelper.deleteAttachmentFile(context, attachment.filePath)
            _uiState.update { it.copy(existingAttachments = it.existingAttachments.filter { a -> a.id != attachment.id }) }
        }
    }

    fun saveAppointment() {
        val state = _uiState.value
        if (state.doctor.isBlank()) return
        if (state.selectedProfileId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val deviceId = settingsRepository.getOrCreateDeviceId()
            val startZdt = LocalDateTime.of(state.date, LocalTime.of(state.timeHour, state.timeMinute))
                .atZone(ZoneId.systemDefault())
            val startAt = startZdt.toInstant().toEpochMilli()

            val remindStr = if (state.remindMinutesList.isEmpty()) {
                "120,30"
            } else {
                state.remindMinutesList.sortedDescending().joinToString(",")
            }

            val now = System.currentTimeMillis()
            val appointment = Appointment(
                id = state.appointmentId,
                profileId = state.selectedProfileId,
                doctor = state.doctor.trim(),
                place = state.place.trim().ifBlank { null },
                startAt = startAt,
                purpose = state.purpose.trim().ifBlank { null },
                notes = state.notes.trim().ifBlank { null },
                prescriptionNotes = state.prescriptionNotes.trim().ifBlank { null },
                status = state.status,
                remindMinutesBefore = remindStr,
                createdAt = if (state.isEditMode) now else now,
                updatedAt = now
            )

            // Save appointment & preps
            appointmentRepository.saveAppointment(appointment, state.preps, deviceId)

            // Process pending attachments
            for (uri in state.pendingAttachmentUris) {
                try {
                    val att = AttachmentStorageHelper.saveAttachment(context, appointment.id, uri)
                    appointmentRepository.addAttachment(att)
                } catch (_: Exception) {
                }
            }

            // Reschedule alarms
            alarmCoordinator.reschedule("appointment_saved")

            _uiState.update { it.copy(isSaving = false, saveCompleted = true) }
        }
    }
}


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
import javax.inject.Inject

data class AppointmentDetailUiState(
    val appointment: Appointment? = null,
    val profile: Profile? = null,
    val preps: List<AppointmentPrep> = emptyList(),
    val attachments: List<AppointmentAttachment> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false
)

@HiltViewModel
class AppointmentDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appointmentRepository: AppointmentRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmCoordinator: AlarmCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val appointmentId: String = checkNotNull(savedStateHandle["appointmentId"])

    private val _uiState = MutableStateFlow(AppointmentDetailUiState())
    val uiState: StateFlow<AppointmentDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            val appt = appointmentRepository.getAppointmentSync(appointmentId) ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val profile = profileRepository.getProfileSync(appt.profileId)
            val preps = appointmentRepository.getPrepsForAppointmentSync(appointmentId)
            val attachments = appointmentRepository.getAttachmentsForAppointmentSync(appointmentId)

            _uiState.update {
                it.copy(
                    appointment = appt,
                    profile = profile,
                    preps = preps,
                    attachments = attachments,
                    isLoading = false
                )
            }
        }
    }

    fun markDone(notes: String?, prescriptionNotes: String?) {
        viewModelScope.launch {
            val deviceId = settingsRepository.getOrCreateDeviceId()
            val current = _uiState.value.appointment ?: return@launch
            val updated = current.copy(
                status = AppointmentStatus.DONE,
                notes = notes ?: current.notes,
                prescriptionNotes = prescriptionNotes ?: current.prescriptionNotes
            )
            val preps = _uiState.value.preps
            appointmentRepository.saveAppointment(updated, preps, deviceId)
            alarmCoordinator.reschedule("appointment_marked_done")
            loadDetails()
        }
    }

    fun cancelAppointment() {
        viewModelScope.launch {
            val deviceId = settingsRepository.getOrCreateDeviceId()
            val current = _uiState.value.appointment ?: return@launch
            val updated = current.copy(status = AppointmentStatus.CANCELLED)
            val preps = _uiState.value.preps
            appointmentRepository.saveAppointment(updated, preps, deviceId)
            alarmCoordinator.reschedule("appointment_cancelled")
            loadDetails()
        }
    }

    fun saveNotes(notes: String, prescriptionNotes: String) {
        viewModelScope.launch {
            val deviceId = settingsRepository.getOrCreateDeviceId()
            val current = _uiState.value.appointment ?: return@launch
            val updated = current.copy(
                notes = notes.ifBlank { null },
                prescriptionNotes = prescriptionNotes.ifBlank { null }
            )
            val preps = _uiState.value.preps
            appointmentRepository.saveAppointment(updated, preps, deviceId)
            loadDetails()
        }
    }

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            try {
                val attachment = AttachmentStorageHelper.saveAttachment(context, appointmentId, uri)
                appointmentRepository.addAttachment(attachment)
                loadDetails()
            } catch (_: Exception) {
            }
        }
    }

    fun deleteAttachment(attachment: AppointmentAttachment) {
        viewModelScope.launch {
            appointmentRepository.deleteAttachment(attachment.id)
            AttachmentStorageHelper.deleteAttachmentFile(context, attachment.filePath)
            loadDetails()
        }
    }

    fun deleteAppointment() {
        viewModelScope.launch {
            val deviceId = settingsRepository.getOrCreateDeviceId()
            // Delete all local files
            val attachments = _uiState.value.attachments
            for (att in attachments) {
                AttachmentStorageHelper.deleteAttachmentFile(context, att.filePath)
            }
            appointmentRepository.deleteAppointment(appointmentId, deviceId)
            alarmCoordinator.reschedule("appointment_deleted")
            _uiState.update { it.copy(isDeleted = true) }
        }
    }
}


package app.allinonereminder.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.data.repository.AppointmentRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.model.Appointment
import app.allinonereminder.model.AppointmentStatus
import app.allinonereminder.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppointmentUiModel(
    val appointment: Appointment,
    val profile: Profile?,
    val prepCount: Int = 0,
    val attachmentCount: Int = 0
)

data class AppointmentsUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val selectedTab: Int = 0, // 0 = Upcoming, 1 = Past
    val upcoming: List<AppointmentUiModel> = emptyList(),
    val past: List<AppointmentUiModel> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AppointmentsViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    private val _selectedTab = MutableStateFlow(0)

    val uiState: StateFlow<AppointmentsUiState> = combine(
        appointmentRepository.getAllAppointments(),
        profileRepository.getActiveProfiles(),
        _selectedProfileId,
        _selectedTab
    ) { allAppointments, profiles, selectedProfileId, selectedTab ->
        val profileMap = profiles.associateBy { it.id }
        val now = System.currentTimeMillis()

        val filtered = allAppointments.filter { appt ->
            selectedProfileId == null || appt.profileId == selectedProfileId
        }

        val models = filtered.map { appt ->
            val preps = appointmentRepository.getPrepsForAppointmentSync(appt.id)
            val attachments = appointmentRepository.getAttachmentsForAppointmentSync(appt.id)
            AppointmentUiModel(
                appointment = appt,
                profile = profileMap[appt.profileId],
                prepCount = preps.size,
                attachmentCount = attachments.size
            )
        }

        val upcoming = models
            .filter { it.appointment.status == AppointmentStatus.UPCOMING && it.appointment.startAt >= now - 3600_000L }
            .sortedBy { it.appointment.startAt }

        val past = models
            .filter { it.appointment.status != AppointmentStatus.UPCOMING || it.appointment.startAt < now - 3600_000L }
            .sortedByDescending { it.appointment.startAt }

        AppointmentsUiState(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            selectedTab = selectedTab,
            upcoming = upcoming,
            past = past,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppointmentsUiState())

    fun selectProfile(profileId: String?) {
        _selectedProfileId.value = profileId
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }
}


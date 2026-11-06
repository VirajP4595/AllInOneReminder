package app.allinonereminder.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.data.repository.DayAdherence
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ProgressRepository
import app.allinonereminder.model.Item
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class HistoryUiState(
    val yearMonth: YearMonth = YearMonth.now(ZoneId.systemDefault()),
    val selectedDate: LocalDate = LocalDate.now(ZoneId.systemDefault()),
    val monthAdherence: Map<LocalDate, DayAdherence> = emptyMap(),
    val selectedDateOccurrences: List<Pair<Occurrence, Item>> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profiles = profileRepository.getActiveProfiles().firstOrNull() ?: emptyList()
            val state = _uiState.value
            val zone = ZoneId.systemDefault()

            val adherence = progressRepository.getMonthAdherence(
                yearMonth = state.yearMonth,
                profileId = state.selectedProfileId,
                zoneId = zone
            )

            val dayOccurrences = progressRepository.getOccurrencesForDate(
                date = state.selectedDate,
                profileId = state.selectedProfileId,
                zoneId = zone
            )

            _uiState.update {
                it.copy(
                    profiles = profiles,
                    monthAdherence = adherence,
                    selectedDateOccurrences = dayOccurrences,
                    isLoading = false
                )
            }
        }
    }

    fun previousMonth() {
        _uiState.update {
            val newYm = it.yearMonth.minusMonths(1)
            it.copy(yearMonth = newYm, selectedDate = newYm.atDay(1))
        }
        loadData()
    }

    fun nextMonth() {
        _uiState.update {
            val newYm = it.yearMonth.plusMonths(1)
            it.copy(yearMonth = newYm, selectedDate = newYm.atDay(1))
        }
        loadData()
    }

    fun selectDate(date: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedDate = date) }
            val dayOccurrences = progressRepository.getOccurrencesForDate(
                date = date,
                profileId = _uiState.value.selectedProfileId,
                zoneId = ZoneId.systemDefault()
            )
            _uiState.update { it.copy(selectedDateOccurrences = dayOccurrences) }
        }
    }

    fun selectProfile(profileId: String?) {
        _uiState.update { it.copy(selectedProfileId = profileId) }
        loadData()
    }
}


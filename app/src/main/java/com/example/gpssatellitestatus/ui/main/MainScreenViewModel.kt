package com.example.gpssatellitestatus.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpssatellitestatus.GpsApplication
import com.example.gpssatellitestatus.data.GnssSatelliteRepository
import com.example.gpssatellitestatus.data.GnssStatusSnapshot
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainScreenViewModel(
  application: Application
) : AndroidViewModel(application) {

  private val repository: GnssSatelliteRepository = (application as GpsApplication).repository

  init {
    refresh()
  }

  val uiState: StateFlow<MainScreenUiState> =
    repository.status
      .map { snapshot -> MainScreenUiState.fromSnapshot(snapshot) }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MainScreenUiState.fromSnapshot(repository.status.value),
      )

  fun onPermissionResult(granted: Boolean) {
    if (granted) {
      repository.startListening()
    } else {
      repository.stopListening()
    }
  }

  fun setSignalThreshold(threshold: Float) {
    repository.minCn0Threshold = threshold
    // Update the state immediately to reflect the change if snapshot is already available
    refresh()
  }

  fun setLocationPriority(priority: Int) {
    repository.locationPriority = priority
    refresh()
  }

  fun refresh() {
    repository.refreshDeviceState()
  }

  fun clearHistory() {
    repository.clearHistory()
  }

  override fun onCleared() {
    repository.stopListening()
    super.onCleared()
  }
}

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState

  data class NeedPermission(val snapshot: GnssStatusSnapshot) : MainScreenUiState

  data class Ready(val snapshot: GnssStatusSnapshot) : MainScreenUiState

  data class Error(val message: String) : MainScreenUiState

  companion object {
    fun fromSnapshot(snapshot: GnssStatusSnapshot): MainScreenUiState {
      if (!snapshot.gnssSupported) {
        return Error("This device does not support GNSS status updates.")
      }
      if (!snapshot.isListening) {
        return NeedPermission(snapshot)
      }
      return Ready(snapshot)
    }
  }
}

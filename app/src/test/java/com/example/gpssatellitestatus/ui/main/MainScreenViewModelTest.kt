package com.example.gpssatellitestatus.ui.main

import com.example.gpssatellitestatus.data.Constellation
import com.example.gpssatellitestatus.data.GnssSatelliteRepository
import com.example.gpssatellitestatus.data.GnssStatusSnapshot
import com.example.gpssatellitestatus.data.SatelliteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_whenNotListening_requestsPermission() = runTest {
    val repository = FakeGnssRepository(GnssStatusSnapshot(isListening = false, gnssSupported = true))
    val uiState = MainScreenUiState.fromSnapshot(repository.status.value)
    assertTrue(uiState is MainScreenUiState.NeedPermission)
  }

  @Test
  fun uiState_whenListening_showsSatellites() = runTest {
    val snapshot =
      GnssStatusSnapshot(
        satellites =
          listOf(
            SatelliteInfo(1, Constellation.Gps, 30f, 45f, 90f, true, true, true, 0f),
          ),
        isListening = true,
        gpsEnabled = true,
      )
    val uiState = MainScreenUiState.fromSnapshot(snapshot)
    assertTrue(uiState is MainScreenUiState.Ready)
    assertEquals(1, (uiState as MainScreenUiState.Ready).snapshot.totalCount)
  }

  @Test
  fun uiState_whenGnssUnsupported_showsError() = runTest {
    val uiState = MainScreenUiState.fromSnapshot(GnssStatusSnapshot(gnssSupported = false))
    assertTrue(uiState is MainScreenUiState.Error)
  }
}

private class FakeGnssRepository(initial: GnssStatusSnapshot) : GnssSatelliteRepository {
  private val _status = MutableStateFlow(initial)
  override val status: StateFlow<GnssStatusSnapshot> = _status

  override fun startListening() {
    _status.value = _status.value.copy(isListening = true)
  }

  override fun stopListening() {
    _status.value = _status.value.copy(isListening = false)
  }

  override fun refreshDeviceState() = Unit
}

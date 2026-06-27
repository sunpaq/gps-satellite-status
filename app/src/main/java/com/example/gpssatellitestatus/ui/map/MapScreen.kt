package com.example.gpssatellitestatus.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpssatellitestatus.data.GnssStatusSnapshot
import com.example.gpssatellitestatus.ui.main.MainScreenUiState
import com.example.gpssatellitestatus.ui.main.MainScreenViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun MapScreen(
  hasLocationPermission: Boolean,
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(hasLocationPermission) {
    viewModel.onPermissionResult(hasLocationPermission)
  }

  when (val current = state) {
    MainScreenUiState.Loading -> {
      Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    is MainScreenUiState.NeedPermission -> {
      Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Location permission needed for map.")
      }
    }
    is MainScreenUiState.Ready -> {
      MapContent(
        snapshot = current.snapshot,
        hasLocationPermission = hasLocationPermission,
        onClearHistory = { viewModel.clearHistory() },
        modifier = modifier
      )
    }
    is MainScreenUiState.Error -> {
      Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(current.message)
      }
    }
  }
}

@Composable
private fun MapContent(
  snapshot: GnssStatusSnapshot,
  hasLocationPermission: Boolean,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val path = snapshot.locationHistory.map { LatLng(it.latitude, it.longitude) }
  val currentPos = snapshot.currentLocation?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(0.0, 0.0)

  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(currentPos, 15f)
  }

  val saveLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/gpx+xml")
  ) { uri ->
    if (uri != null) {
      context.contentResolver.openOutputStream(uri)?.use { 
        it.write(generateGpx(snapshot.locationHistory).toByteArray())
      }
    }
  }

  LaunchedEffect(currentPos) {
      if (currentPos.latitude != 0.0) {
          cameraPositionState.position = CameraPosition.fromLatLngZoom(currentPos, cameraPositionState.position.zoom)
      }
  }

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
      uiSettings = MapUiSettings(myLocationButtonEnabled = true),
    ) {
      if (path.isNotEmpty()) {
        Polyline(
          points = path,
          color = Color.Blue,
          width = 8f
        )
      }
    }

    Surface(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
      shape = MaterialTheme.shapes.medium
    ) {
      Text(
        text = "Filter: ${snapshot.minCn0Threshold.toInt()} dB-Hz",
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Column(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(16.dp),
      horizontalAlignment = Alignment.Start
    ) {
      if (snapshot.locationHistory.isNotEmpty()) {
        FloatingActionButton(
          onClick = { onClearHistory() },
          containerColor = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.padding(bottom = 8.dp)
        ) {
          Icon(Icons.Default.Clear, contentDescription = "Clear History")
        }

        FloatingActionButton(
          onClick = {
            val fileName = "track_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.gpx"
            saveLauncher.launch(fileName)
          },
          modifier = Modifier.padding(bottom = 8.dp)
        ) {
          Icon(Icons.Default.Save, contentDescription = "Save to Downloads")
        }

        FloatingActionButton(
          onClick = { exportToGpx(context, snapshot.locationHistory) }
        ) {
          Icon(Icons.Default.Share, contentDescription = "Export GPX")
        }
      }
    }
  }
}

private fun generateGpx(history: List<Location>): String {
  val gpxHeader = """
    <?xml version="1.1" encoding="UTF-8"?>
    <gpx version="1.1" creator="GPS Satellite Status" 
         xmlns="http://www.topografix.com/GPX/1/1">
      <trk>
        <name>Recorded Track ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}</name>
        <trkseg>
  """.trimIndent()

  val points = history.joinToString("\n") { loc ->
    """
          <trkpt lat="${loc.latitude}" lon="${loc.longitude}">
            <ele>${if (loc.hasAltitude()) loc.altitude else 0.0}</ele>
            <time>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(loc.time))}</time>
          </trkpt>
    """.trimIndent()
  }

  val gpxFooter = """
        </trkseg>
      </trk>
    </gpx>
  """.trimIndent()

  return gpxHeader + "\n" + points + "\n" + gpxFooter
}

private fun exportToGpx(context: Context, history: List<Location>) {
  if (history.isEmpty()) return

  val gpxContent = generateGpx(history)
  try {
    val fileName = "track_${System.currentTimeMillis()}.gpx"
    val file = File(context.cacheDir, fileName)
    file.writeText(gpxContent)

    val contentUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/gpx+xml"
      putExtra(Intent.EXTRA_STREAM, contentUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export GPX"))
  } catch (e: Exception) {
    e.printStackTrace()
  }
}

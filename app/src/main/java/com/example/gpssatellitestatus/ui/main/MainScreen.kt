package com.example.gpssatellitestatus.ui.main

import androidx.activity.viewModels
import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpssatellitestatus.R
import com.example.gpssatellitestatus.data.Constellation
import com.example.gpssatellitestatus.data.GnssStatusSnapshot
import com.example.gpssatellitestatus.data.SatelliteInfo
import com.example.gpssatellitestatus.theme.GPSSatelliteStatusTheme
import com.google.android.gms.location.Priority
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun MainScreen(
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
      PermissionPrompt(onRequestPermission = onRequestPermission, modifier = modifier)
    }
    is MainScreenUiState.Ready -> {
      SatelliteStatusContent(
        snapshot = current.snapshot,
        onThresholdChange = { viewModel.setSignalThreshold(it) },
        onPriorityChange = { viewModel.setLocationPriority(it) },
        modifier = modifier
      )
    }
    is MainScreenUiState.Error -> {
      ErrorContent(message = current.message, modifier = modifier)
    }
  }
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.permission_title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
      text = stringResource(R.string.permission_body),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRequestPermission) {
      Text(stringResource(R.string.grant_permission))
    }
  }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
  Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(text = message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
internal fun SatelliteStatusContent(
  snapshot: GnssStatusSnapshot,
  onThresholdChange: (Float) -> Unit,
  onPriorityChange: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SummaryCard(snapshot, onThresholdChange, onPriorityChange)
    }
    item {
      SkyPlotCard(satellites = snapshot.satellites)
    }
    if (snapshot.satellites.isEmpty()) {
      item {
        Text(
          text = stringResource(R.string.waiting_for_satellites),
          modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      item {
        Text(
          text = stringResource(R.string.satellite_list_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      items(
        snapshot.satellites,
        key = { "${it.constellation.name}-${it.svid}-${it.carrierFrequencyHz}" }
      ) { satellite ->
        SatelliteRow(satellite)
      }
    }
    item { Spacer(Modifier.height(8.dp)) }
  }
}

@Composable
private fun SummaryCard(
  snapshot: GnssStatusSnapshot,
  onThresholdChange: (Float) -> Unit,
  onPriorityChange: (Int) -> Unit
) {
  var showAccuracyInfo by remember { mutableStateOf(false) }

  if (showAccuracyInfo) {
    AccuracyInfoDialog(onDismiss = { showAccuracyInfo = false })
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(R.string.summary_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text =
            if (snapshot.gpsEnabled) {
              stringResource(R.string.gps_enabled)
            } else {
              stringResource(R.string.gps_disabled)
            },
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color =
            if (snapshot.gpsEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
      }

      snapshot.currentLocation?.let { location ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Column(Modifier.weight(1f)) {
            Text(text = "Latitude", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "%.6f°".format(location.latitude), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          }
          Column(Modifier.weight(1f)) {
            Text(text = "Longitude", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "%.6f°".format(location.longitude), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          }
          Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Accuracy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              IconButton(
                onClick = { showAccuracyInfo = true },
                modifier = Modifier.size(18.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = "Accuracy Info",
                  modifier = Modifier.size(14.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Text(
              text = if (location.hasAccuracy()) "±%.1f m".format(location.accuracy) else "—",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SummaryStat(label = stringResource(R.string.in_view), value = snapshot.totalCount.toString())
        SummaryStat(label = stringResource(R.string.used_in_fix), value = snapshot.usedInFixCount.toString())
        SummaryStat(
          label = stringResource(R.string.avg_signal),
          value = if (snapshot.satellites.isEmpty()) "—" else "%.1f dB-Hz".format(snapshot.averageCn0),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Accuracy Priority",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val options = listOf(
          "HIGH" to Priority.PRIORITY_HIGH_ACCURACY,
          "BALANCED" to Priority.PRIORITY_BALANCED_POWER_ACCURACY,
          "LOW" to Priority.PRIORITY_LOW_POWER,
          "PASSIVE" to Priority.PRIORITY_PASSIVE
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          options.forEachIndexed { index, (label, priority) ->
            SegmentedButton(
              selected = snapshot.locationPriority == priority,
              onClick = { onPriorityChange(priority) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1
              )
            }
          }
        }
      }

      Column {
        Text(
          text = "Signal Strength Filter: ${snapshot.minCn0Threshold.toInt()} dB-Hz",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
          value = snapshot.minCn0Threshold,
          onValueChange = onThresholdChange,
          valueRange = 0f..40f,
          steps = 39
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          SuggestionChip(
            onClick = { onThresholdChange(20f) },
            label = { Text("Urban", style = MaterialTheme.typography.labelSmall) }
          )
          SuggestionChip(
            onClick = { onThresholdChange(25f) },
            label = { Text("Suburban", style = MaterialTheme.typography.labelSmall) }
          )
          SuggestionChip(
            onClick = { onThresholdChange(30f) },
            label = { Text("Countryside", style = MaterialTheme.typography.labelSmall) }
          )
        }
      }
    }
  }
}

@Composable
private fun SummaryStat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun AccuracyInfoDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Understanding Accuracy") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "The accuracy value is a statistical estimate (68% confidence radius).",
          style = MaterialTheme.typography.bodyMedium
        )
        Text(
          text = "• Trustable: Accuracy < 5m in open sky conditions.",
          style = MaterialTheme.typography.bodySmall
        )
        Text(
          text = "• Drift: In urban areas, real-world drift can be 2-3x higher than reported due to building reflections (Multipath).",
          style = MaterialTheme.typography.bodySmall
        )
        Text(
          text = "• Driver Floor: Many devices cap accuracy at 10m (or 9.9m) by design in the firmware.",
          style = MaterialTheme.typography.bodySmall
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Got it")
      }
    }
  )
}

@Composable
private fun SkyPlotCard(satellites: List<SatelliteInfo>) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      Text(
        text = stringResource(R.string.sky_plot_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.height(8.dp))
      SkyPlot(
        satellites = satellites,
        modifier = Modifier.fillMaxWidth().height(260.dp),
      )
      Spacer(Modifier.height(8.dp))
      ConstellationLegend()
    }
  }
}

@Composable
private fun ConstellationLegend() {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
  ) {
    LegendItem(color = constellationColor(Constellation.Gps), label = "GPS")
    LegendItem(color = constellationColor(Constellation.Galileo), label = "Galileo")
    LegendItem(color = constellationColor(Constellation.Glonass), label = "GLONASS")
    LegendItem(color = constellationColor(Constellation.Beidou), label = "BeiDou")
  }
}

@Composable
private fun LegendItem(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    Text(text = label, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun SkyPlot(satellites: List<SatelliteInfo>, modifier: Modifier = Modifier) {
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
  val horizonColor = MaterialTheme.colorScheme.outline
  val northColor = MaterialTheme.colorScheme.primary

  Canvas(modifier = modifier) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f * 0.88f

    listOf(1f, 0.66f, 0.33f).forEach { fraction ->
      drawCircle(color = gridColor, radius = radius * fraction, center = center, style = Stroke(width = 1.5f))
    }
    drawCircle(color = horizonColor, radius = radius, center = center, style = Stroke(width = 2f))

    // North marker
    drawLine(
      color = northColor,
      start = center,
      end = Offset(center.x, center.y - radius),
      strokeWidth = 2f,
    )

    satellites.forEach { satellite ->
      if (satellite.elevationDegrees <= 0f) return@forEach
      val elevationFraction = 1f - (satellite.elevationDegrees / 90f)
      val distance = radius * elevationFraction
      val azimuthRad = Math.toRadians(satellite.azimuthDegrees.toDouble())
      val x = center.x + (distance * sin(azimuthRad)).toFloat()
      val y = center.y - (distance * cos(azimuthRad)).toFloat()
      val color = constellationColor(satellite.constellation)
      val dotRadius = if (satellite.usedInFix) 10f else 7f
      drawCircle(color = color, radius = dotRadius, center = Offset(x, y))
      if (satellite.usedInFix) {
        drawCircle(
          color = Color.White,
          radius = dotRadius,
          center = Offset(x, y),
          style = Stroke(width = 2f),
        )
      }
    }
  }
}

@Composable
private fun SatelliteRow(satellite: SatelliteInfo) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (satellite.usedInFix) {
            MaterialTheme.colorScheme.secondaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          },
      ),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
          Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(constellationColor(satellite.constellation)),
        )
        Column {
          Text(
            text = buildString {
              append(satellite.constellation.label)
              append(" ")
              append(satellite.svid)
              if (satellite.carrierFrequencyHz > 0) {
                append(" (")
                append("%.1f MHz".format(satellite.carrierFrequencyHz / 1_000_000f))
                append(")")
              }
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text =
              stringResource(
                R.string.satellite_detail,
                satellite.elevationDegrees,
                satellite.azimuthDegrees,
              ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "%.1f dB-Hz".format(satellite.cn0DbHz),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text =
            when {
              satellite.usedInFix -> stringResource(R.string.status_used)
              satellite.hasEphemeris -> stringResource(R.string.status_ephemeris)
              satellite.hasAlmanac -> stringResource(R.string.status_almanac)
              else -> stringResource(R.string.status_visible)
            },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun constellationColor(constellation: Constellation): Color =
  when (constellation) {
    Constellation.Gps -> Color(0xFF4285F4)
    Constellation.Galileo -> Color(0xFF34A853)
    Constellation.Glonass -> Color(0xFFEA4335)
    Constellation.Beidou -> Color(0xFFFBBC04)
    Constellation.Qzss -> Color(0xFFAB47BC)
    Constellation.Sbas -> Color(0xFF00ACC1)
    Constellation.Irnss -> Color(0xFFFF7043)
    Constellation.Unknown -> Color(0xFF9E9E9E)
  }

@Preview(showBackground = true)
@Composable
fun SatelliteStatusPreview() {
  val sample =
    GnssStatusSnapshot(
      satellites =
        listOf(
          SatelliteInfo(12, Constellation.Gps, 35.2f, 65f, 120f, true, true, true, 0f),
          SatelliteInfo(24, Constellation.Galileo, 28.5f, 42f, 210f, true, true, false, 0f),
          SatelliteInfo(7, Constellation.Glonass, 22.1f, 18f, 330f, false, false, true, 0f),
        ),
      isListening = true,
      gpsEnabled = true,
    )
  GPSSatelliteStatusTheme {
    SatelliteStatusContent(
      snapshot = sample,
      onThresholdChange = {},
      onPriorityChange = {},
      modifier = Modifier.padding(16.dp)
    )
  }
}

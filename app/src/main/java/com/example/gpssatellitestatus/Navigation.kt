package com.example.gpssatellitestatus

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.gpssatellitestatus.ui.main.MainScreen
import com.example.gpssatellitestatus.ui.map.MapScreen

@Composable
fun MainNavigation(
  hasLocationPermission: Boolean,
  onRequestPermission: () -> Unit,
) {
  val backStack = rememberNavBackStack(Main)

  Scaffold(
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          selected = backStack.lastOrNull() is Main,
          onClick = {
            if (backStack.lastOrNull() !is Main) {
              backStack.add(Main)
            }
          },
          icon = { Icon(Icons.Default.List, contentDescription = "Satellites") },
          label = { Text("Satellites") }
        )
        NavigationBarItem(
          selected = backStack.lastOrNull() is Map,
          onClick = {
            if (backStack.lastOrNull() !is Map) {
              backStack.add(Map)
            }
          },
          icon = { Icon(Icons.Default.LocationOn, contentDescription = "Map") },
          label = { Text("Map") }
        )
      }
    }
  ) { innerPadding ->
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryProvider =
        entryProvider {
          entry<Main> {
            MainScreen(
              hasLocationPermission = hasLocationPermission,
              onRequestPermission = onRequestPermission,
              modifier = Modifier.padding(innerPadding).padding(16.dp),
            )
          }
          entry<Map> {
            MapScreen(
              hasLocationPermission = hasLocationPermission,
              onRequestPermission = onRequestPermission,
              modifier = Modifier.padding(innerPadding),
            )
          }
        },
    )
  }
}

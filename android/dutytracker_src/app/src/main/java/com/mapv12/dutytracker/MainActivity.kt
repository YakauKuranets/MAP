package com.mapv12.dutytracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MainTab { DASHBOARD, MAP, CHAT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchdogWorker.ensureScheduled(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TacticalTerminalApp(
                    onStartTracking = {
                        ForegroundLocationService.setTrackingOn(this, true)
                        startForegroundService(Intent(this, ForegroundLocationService::class.java))
                    },
                    onStopTracking = {
                        ForegroundLocationService.setTrackingOn(this, false)
                        stopService(Intent(this, ForegroundLocationService::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun TacticalTerminalApp(onStartTracking: () -> Unit, onStopTracking: () -> Unit) {
    var activeTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = activeTab == MainTab.DASHBOARD, onClick = { activeTab = MainTab.DASHBOARD }, label = { Text("Dashboard") }, icon = { Text("🏠") })
                NavigationBarItem(selected = activeTab == MainTab.MAP, onClick = { activeTab = MainTab.MAP }, label = { Text("Map") }, icon = { Text("🗺️") })
                NavigationBarItem(selected = activeTab == MainTab.CHAT, onClick = { activeTab = MainTab.CHAT }, label = { Text("Chat") }, icon = { Text("💬") })
            }
        }
    ) { paddings ->
        when (activeTab) {
            MainTab.DASHBOARD -> DashboardScreen(Modifier.padding(paddings), onStartTracking, onStopTracking)
            MainTab.MAP -> MapScreen(Modifier.padding(paddings))
            MainTab.CHAT -> ChatScreen(Modifier.padding(paddings))
        }
    }
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier, onStartTracking: () -> Unit, onStopTracking: () -> Unit) {
    val ctx = LocalContext.current
    var coords by remember { mutableStateOf("—") }
    LaunchedEffect(Unit) {
        coords = withContext(Dispatchers.IO) {
            val last = StatusStore.getLastLatLon(ctx)
            if (last == null) "—" else "%.6f, %.6f".format(last.first, last.second)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Мобильный тактический терминал", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Текущие координаты:", style = MaterialTheme.typography.titleMedium)
        Text(coords, modifier = Modifier.testTag("dashboard_coordinates"), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onStartTracking, modifier = Modifier.weight(1f)) { Text("Старт") }
            Button(onClick = onStopTracking, modifier = Modifier.weight(1f)) { Text("Стоп") }
        }
    }
}

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("MapScreen", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("3D карта и тактические слои подключаются к backend через Postgres/Redis pipeline.")
    }
}

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("ChatScreen", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Оперативный чат с командным центром работает по WebSocket без FCM.")
    }
}

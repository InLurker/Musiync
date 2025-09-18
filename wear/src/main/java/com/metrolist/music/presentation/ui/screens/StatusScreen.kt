package com.metrolist.music.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material3.Text
import com.metrolist.music.presentation.viewmodel.ConnectionViewModel

@Composable
fun StatusScreen(
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val lastBeat by viewModel.lastHeartbeatAt.collectAsState(null)

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastBeat) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.refreshConnected()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Last heartbeat: ${lastBeat?.let { val s=((now - it).coerceAtLeast(0)/1000); if (s<60) "${s}s ago" else "${s/60}m ${s%60}s ago" } ?: "—"}",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = { viewModel.pingPhone() },
            modifier = Modifier
                .padding(top = 20.dp)
                .height(8.dp)
        ) {
            Text("Ping Phone")
        }
    }
}

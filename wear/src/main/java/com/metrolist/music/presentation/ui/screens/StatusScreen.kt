package com.metrolist.music.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.metrolist.music.presentation.viewmodel.ConnectionViewModel

@Composable
fun StatusScreen(
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val nodes by viewModel.connectedNodes.collectAsState()
    val lastBeat by viewModel.lastHeartbeatAt.collectAsState(null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connected devices: $nodes",
            textAlign = TextAlign.Center
        )
        Text(
            text = "Last heartbeat: ${lastBeat?.let { "${(System.currentTimeMillis() - it)} ms ago" } ?: "—"}",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = { viewModel.pingPhone() },
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Ping Phone")
        }
    }
}


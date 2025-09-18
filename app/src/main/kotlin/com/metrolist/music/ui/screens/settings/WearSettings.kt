package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.wear.MessageLayerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WearSettingsViewModel @Inject constructor(
    private val messageLayerHelper: MessageLayerHelper
) : ViewModel() {
    val lastHeartbeatAt = messageLayerHelper.lastHeartbeatAt
    var connected = androidx.compose.runtime.mutableStateOf(0)

    fun refreshConnected() {
        messageLayerHelper.fetchConnectedNodes { connected.value = it }
    }

    fun ping() {
        messageLayerHelper.sendHeartbeatPing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: WearSettingsViewModel = hiltViewModel()
) {
    val lastBeat by viewModel.lastHeartbeatAt.collectAsState(null)
    val connectedCount = viewModel.connected.value

    LaunchedEffect(Unit) { viewModel.refreshConnected() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 56.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.wear_os_integration),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.link),
                    title = { Text(stringResource(R.string.wear_connected_devices)) },
                    description = { Text(connectedCount.toString()) },
                    onClick = { viewModel.refreshConnected() }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.sync),
                    title = { Text(stringResource(R.string.wear_test_connection)) },
                    description = {
                        Text(
                            lastBeat?.let { stringResource(R.string.wear_last_heartbeat, it.toString()) }
                                ?: ""
                        )
                    },
                    onClick = { viewModel.ping() }
                )
            )
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.wear_os_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = {}
            ) {
                androidx.compose.material3.Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}


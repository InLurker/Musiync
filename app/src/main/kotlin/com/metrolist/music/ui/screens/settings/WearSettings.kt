package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.wear.MessageLayerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WearSettingsViewModel @Inject constructor(
    private val messageLayerHelper: MessageLayerHelper
) : ViewModel() {
    val lastHeartbeatAt = messageLayerHelper.lastHeartbeatAt
    var connected = mutableStateOf(0)

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

    // Tick every second to update the "time ago" text
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastBeat) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val timeAgo = lastBeat?.let {
        val delta = (now - it).coerceAtLeast(0)
        val seconds = delta / 1000
        when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s ago"
            else -> "${seconds / 3600}h ${seconds % 3600 / 60}m ago"
        }
    }

    Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

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
                        Text(timeAgo?.let { stringResource(R.string.wear_last_heartbeat, it) } ?: "")
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
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}


package com.metrolist.music.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices
import com.metrolist.music.models.TrackInfo
import com.metrolist.music.presentation.ui.screens.DisplayScreen
import com.metrolist.music.presentation.wear.MessageClientService
import com.metrolist.music.repository.CurrentTrackHolder
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {

    private lateinit var messageClientService: MessageClientService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            messageClientService = MessageClientService(this)
            DisplayScreen(trackInfo = CurrentTrackHolder.currentTrack) { command ->
                messageClientService.sendPlaybackCommand(command)
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        messageClientService.destroy()
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    DisplayScreen(
        TrackInfo(
            "Track Name",
            "Artist Name",
            "Album Name",
            "https://i.ytimg.com/vi/8ZP5eqm4JqM/hq720.jpg",
            flowOf( null )
        )
    ) {
        // Do nothing
    }
}

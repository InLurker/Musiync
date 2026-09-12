package com.metrolist.music.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.MaterialTheme
import com.metrolist.music.R
import com.metrolist.music.common.enumerated.WearCommandEnum

@Composable
fun PlaybackControl(
    accentColor: Color?,
    isPlaying: Boolean,
    onCommand: (WearCommandEnum) -> Unit,
    modifier: Modifier = Modifier,
) {

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        CompactButton(
            onClick = { onCommand(WearCommandEnum.PREVIOUS) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.skip_previous),
                contentDescription = "Previous"
            )
        }
        CompactButton(
            onClick = { onCommand(WearCommandEnum.PLAY_PAUSE) },
            colors = ButtonDefaults.buttonColors(backgroundColor = accentColor ?: MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = "Play/Pause"
            )
        }
        CompactButton(
            onClick = { onCommand(WearCommandEnum.NEXT) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.skip_next),
                contentDescription = "Next"
            )
        }
    }
}

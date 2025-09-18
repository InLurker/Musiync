package com.metrolist.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun ThumbnailImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    builder: ImageRequest.Builder.() -> Unit = {},
    onState: (AsyncImagePainter.State) -> Unit = {},
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .apply {
                url?.let {
                    memoryCacheKey(it)
                    diskCacheKey(it)
                }
            }
            .crossfade(true)
            .apply(builder)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        onState = onState
    )
}

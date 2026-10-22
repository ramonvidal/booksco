package com.punyplatipus.podouvir.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.punyplatipus.podouvir.ui.theme.PodOuvirTheme

/**
 * Square artwork, rounded (MaterialTheme.shapes.medium), Coil image with crossfade.
 * While loading / on error / url == null: surfaceVariant box with a podcast icon centred.
 */
@Composable
fun PodcastArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val shaped = modifier.clip(MaterialTheme.shapes.medium)
    if (artworkUrl == null) {
        ArtworkPlaceholder(shaped)
        return
    }
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(artworkUrl).crossfade(true).build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = shaped,
        loading = { ArtworkPlaceholder(Modifier.fillMaxSize()) },
        error = { ArtworkPlaceholder(Modifier.fillMaxSize()) },
    )
}

@Composable
private fun ArtworkPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("podcastArtworkPlaceholder"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Podcasts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Artwork placeholder")
@Composable
private fun PodcastArtworkPlaceholderPreview() {
    PodOuvirTheme { PodcastArtwork(artworkUrl = null, contentDescription = null, modifier = Modifier.size(96.dp)) }
}

@Preview(name = "Artwork placeholder dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PodcastArtworkPlaceholderDarkPreview() {
    PodOuvirTheme { PodcastArtwork(artworkUrl = null, contentDescription = null, modifier = Modifier.size(96.dp)) }
}

package com.punyplatipus.podouvir.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.punyplatipus.podouvir.R
import kotlinx.serialization.Serializable

@Serializable data object LibraryDestination

@Serializable data object DiscoverDestination

@Serializable data object QueueDestination

@Serializable data class PodcastDestination(val podcastId: Long)

enum class TopLevelTab(
    val destination: Any,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    LIBRARY(LibraryDestination, R.string.navigation_library, Icons.Rounded.LibraryMusic),
    DISCOVER(DiscoverDestination, R.string.navigation_discover, Icons.Rounded.Search),
    QUEUE(QueueDestination, R.string.navigation_queue, Icons.AutoMirrored.Rounded.QueueMusic),
}

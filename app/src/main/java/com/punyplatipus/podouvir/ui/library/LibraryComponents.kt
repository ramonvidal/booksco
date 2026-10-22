package com.punyplatipus.podouvir.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.ViewMode
import com.punyplatipus.podouvir.ui.components.PodcastArtwork
import com.punyplatipus.podouvir.ui.components.genreLabel

@Composable
internal fun ViewToggleButton(viewMode: ViewMode, onToggle: () -> Unit) {
    val description = stringResource(
        if (viewMode == ViewMode.GRID) R.string.library_view_switch_to_list else R.string.library_view_switch_to_grid,
    )
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
            contentDescription = description,
        )
    }
}

@Composable
internal fun SortMenuButton(
    sortOrder: SortOrder,
    onSortOrder: (SortOrder) -> Unit,
    onStartReorder: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.library_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sortOptions.forEach { (order, label) ->
                val isSelected = order == sortOrder
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { RadioButton(selected = isSelected, onClick = null) },
                    onClick = {
                        expanded = false
                        onSortOrder(order)
                    },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        selected = isSelected
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_reorder)) },
                onClick = {
                    expanded = false
                    onStartReorder()
                },
            )
        }
    }
}

private val sortOptions = listOf(
    SortOrder.TITLE to R.string.library_sort_title,
    SortOrder.RECENTLY_UPDATED to R.string.library_sort_recently_updated,
    SortOrder.DATE_ADDED to R.string.library_sort_date_added,
    SortOrder.CUSTOM to R.string.library_sort_custom,
)

@Composable
internal fun GenreChipsRow(
    genres: List<String>,
    selectedGenre: String?,
    onSelectGenre: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "chip-all") {
            FilterChip(
                selected = selectedGenre == null,
                onClick = { onSelectGenre(null) },
                label = { Text(stringResource(R.string.library_all)) },
            )
        }
        items(genres, key = { "chip-$it" }) { genre ->
            FilterChip(
                selected = genre == selectedGenre,
                onClick = { onSelectGenre(genre) },
                label = { Text(genreLabel(genre)) },
            )
        }
    }
}

@Composable
internal fun GroupHeader(
    genre: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_group_header, genreLabel(genre), count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = stringResource(
                    if (collapsed) R.string.library_group_expand else R.string.library_group_collapse,
                ),
            )
        }
    }
}

/** Wraps [content] with the long-press menu (Change genre / Unsubscribe). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastMenuHost(
    podcast: Podcast,
    onOpen: () -> Unit,
    onChangeGenre: (Podcast) -> Unit,
    onUnsubscribe: (Podcast) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        content(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }))
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_change_genre)) },
                onClick = {
                    menuOpen = false
                    onChangeGenre(podcast)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_unsubscribe)) },
                onClick = {
                    menuOpen = false
                    onUnsubscribe(podcast)
                },
            )
        }
    }
}

@Composable
internal fun PodcastGridRow(
    podcasts: List<Podcast>,
    columns: Int,
    onOpen: (Long) -> Unit,
    onChangeGenre: (Podcast) -> Unit,
    onUnsubscribe: (Podcast) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        podcasts.forEach { podcast ->
            PodcastMenuHost(
                podcast = podcast,
                onOpen = { onOpen(podcast.id) },
                onChangeGenre = onChangeGenre,
                onUnsubscribe = onUnsubscribe,
                modifier = Modifier.weight(1f),
            ) { clickable ->
                Column(clickable.padding(4.dp)) {
                    PodcastArtwork(
                        artworkUrl = podcast.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        repeat(columns - podcasts.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
internal fun PodcastListRow(
    podcast: Podcast,
    onOpen: (Long) -> Unit,
    onChangeGenre: (Podcast) -> Unit,
    onUnsubscribe: (Podcast) -> Unit,
    modifier: Modifier = Modifier,
) {
    PodcastMenuHost(
        podcast = podcast,
        onOpen = { onOpen(podcast.id) },
        onChangeGenre = onChangeGenre,
        onUnsubscribe = onUnsubscribe,
        modifier = modifier,
    ) { clickable ->
        ListItem(
            modifier = clickable,
            leadingContent = { PodcastArtwork(podcast.artworkUrl, null, Modifier.size(56.dp)) },
            headlineContent = { Text(podcast.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = podcast.author.takeIf { it.isNotBlank() }?.let { author ->
                { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
        )
    }
}

@Composable
internal fun ReorderRow(
    podcast: Podcast,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = { PodcastArtwork(podcast.artworkUrl, null, Modifier.size(56.dp)) },
        headlineContent = { Text(podcast.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = podcast.author.takeIf { it.isNotBlank() }?.let { author ->
            { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.library_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.library_move_down))
                }
            }
        },
    )
}

@Composable
internal fun UnsubscribeDialog(podcast: Podcast, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_unsubscribe_title)) },
        text = { Text(stringResource(R.string.library_unsubscribe_message, podcast.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_unsubscribe)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) }
        },
    )
}

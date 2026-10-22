package com.punyplatipus.podouvir.ui.library

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE
import com.punyplatipus.podouvir.domain.model.ViewMode
import com.punyplatipus.podouvir.ui.components.ChangeGenreDialog
import com.punyplatipus.podouvir.ui.theme.PodOuvirTheme

@Composable
fun LibraryRoute(
    onOpenPodcast: (Long) -> Unit,
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onOpenPodcast = onOpenPodcast,
        onDiscover = onDiscover,
        onSelectGenre = viewModel::selectGenre,
        onSortOrder = viewModel::setSortOrder,
        onToggleViewMode = viewModel::toggleViewMode,
        onStartReorder = viewModel::startReorder,
        onFinishReorder = viewModel::finishReorder,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onChangeGenre = viewModel::changeGenre,
        onUnsubscribe = viewModel::unsubscribe,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenPodcast: (Long) -> Unit,
    onDiscover: () -> Unit,
    onSelectGenre: (String?) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    onToggleViewMode: () -> Unit,
    onStartReorder: () -> Unit,
    onFinishReorder: () -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onChangeGenre: (podcastId: Long, genre: String?) -> Unit,
    onUnsubscribe: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var genreTarget by remember { mutableStateOf<Podcast?>(null) }
    var unsubscribeTarget by remember { mutableStateOf<Podcast?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    if (state.isReordering) {
                        TextButton(onClick = onFinishReorder) { Text(stringResource(R.string.library_done)) }
                    } else {
                        ViewToggleButton(state.viewMode, onToggleViewMode)
                        SortMenuButton(state.sortOrder, onSortOrder, onStartReorder)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val isEmpty = !state.isLoading && state.totalCount == 0
            if (!state.isLoading && !isEmpty && !state.isReordering) {
                GenreChipsRow(state.genres, state.selectedGenre, onSelectGenre)
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                isEmpty -> EmptyLibrary(onDiscover)
                else -> LibraryContent(
                    state = state,
                    onOpenPodcast = onOpenPodcast,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onChangeGenreRequest = { genreTarget = it },
                    onUnsubscribeRequest = { unsubscribeTarget = it },
                )
            }
        }
    }

    genreTarget?.let { podcast ->
        ChangeGenreDialog(
            podcastTitle = podcast.title,
            feedGenre = podcast.genre,
            currentOverride = podcast.genreOverride,
            suggestions = state.genres,
            onConfirm = { genre ->
                genreTarget = null
                onChangeGenre(podcast.id, genre)
            },
            onDismiss = { genreTarget = null },
        )
    }
    unsubscribeTarget?.let { podcast ->
        UnsubscribeDialog(
            podcast = podcast,
            onConfirm = {
                unsubscribeTarget = null
                onUnsubscribe(podcast.id)
            },
            onDismiss = { unsubscribeTarget = null },
        )
    }
}

@Composable
private fun EmptyLibrary(onDiscover: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Rounded.Podcasts,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.library_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDiscover) { Text(stringResource(R.string.library_discover)) }
    }
}

private val GridTileWidth: Dp = 128.dp

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onOpenPodcast: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onChangeGenreRequest: (Podcast) -> Unit,
    onUnsubscribeRequest: (Podcast) -> Unit,
) {
    // ArrayList (Serializable) so rememberSaveable can store it in a Bundle.
    var collapsed by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = maxOf(2, (maxWidth / GridTileWidth).toInt())
        LazyColumn(Modifier.fillMaxSize()) {
            state.groups.forEach { group ->
                val isCollapsed = group.genre in collapsed
                groupHeader(group, isCollapsed) {
                    collapsed = ArrayList(if (isCollapsed) collapsed - group.genre else collapsed + group.genre)
                }
                if (isCollapsed) return@forEach
                when {
                    state.isReordering -> reorderItems(group.podcasts, onMoveUp, onMoveDown)
                    state.viewMode == ViewMode.GRID -> items(
                        items = group.podcasts.chunked(columns),
                        key = { "row-${it.first().id}" },
                    ) { row ->
                        PodcastGridRow(row, columns, onOpenPodcast, onChangeGenreRequest, onUnsubscribeRequest)
                    }
                    else -> items(group.podcasts, key = { it.id }) { podcast ->
                        PodcastListRow(podcast, onOpenPodcast, onChangeGenreRequest, onUnsubscribeRequest)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.groupHeader(group: GenreGroup, collapsed: Boolean, onToggle: () -> Unit) {
    stickyHeader(key = "header-${group.genre}") {
        GroupHeader(group.genre, group.podcasts.size, collapsed, onToggle)
    }
}

private fun LazyListScope.reorderItems(
    podcasts: List<Podcast>,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
) {
    itemsIndexed(podcasts, key = { _, podcast -> podcast.id }) { index, podcast ->
        ReorderRow(
            podcast = podcast,
            canMoveUp = index > 0,
            canMoveDown = index < podcasts.lastIndex,
            onMoveUp = { onMoveUp(podcast.id) },
            onMoveDown = { onMoveDown(podcast.id) },
        )
    }
}

// ---- Previews ----

private fun samplePodcast(id: Long, title: String, genre: String) =
    Podcast(id = id, feedUrl = "https://example.com/$id", title = title, author = "Author $id", genre = genre)

private val sampleState = LibraryUiState(
    isLoading = false,
    totalCount = 5,
    genres = listOf("News", "Technology", UNKNOWN_GENRE),
    groups = listOf(
        GenreGroup("News", listOf(samplePodcast(1, "Daily News", "News"), samplePodcast(2, "World", "News"))),
        GenreGroup("Technology", listOf(samplePodcast(3, "Tech Talk", "Technology"))),
        GenreGroup(UNKNOWN_GENRE, listOf(samplePodcast(4, "Misc", ""), samplePodcast(5, "Stuff", ""))),
    ),
)

@Composable
private fun PreviewScreen(state: LibraryUiState) {
    PodOuvirTheme {
        LibraryScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})
    }
}

@Preview(name = "Empty")
@Composable
private fun LibraryEmptyPreview() = PreviewScreen(LibraryUiState(isLoading = false))

@Preview(name = "Grid")
@Composable
private fun LibraryGridPreview() = PreviewScreen(sampleState)

@Preview(name = "List dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryListDarkPreview() = PreviewScreen(sampleState.copy(viewMode = ViewMode.LIST))

@Preview(name = "Reorder")
@Composable
private fun LibraryReorderPreview() = PreviewScreen(sampleState.copy(isReordering = true, sortOrder = SortOrder.CUSTOM))

@Preview(name = "Reorder dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryReorderDarkPreview() =
    PreviewScreen(sampleState.copy(isReordering = true, sortOrder = SortOrder.CUSTOM))

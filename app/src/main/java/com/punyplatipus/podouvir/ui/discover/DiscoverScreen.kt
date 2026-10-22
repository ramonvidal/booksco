package com.punyplatipus.podouvir.ui.discover

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import com.punyplatipus.podouvir.ui.components.PodcastArtwork
import com.punyplatipus.podouvir.ui.components.genreLabel
import com.punyplatipus.podouvir.ui.theme.PodOuvirTheme

@Composable
fun DiscoverRoute(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DiscoverScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSubscribe = viewModel::subscribe,
        onAddByUrl = viewModel::addByUrl,
        onMessageShown = viewModel::messageShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onQueryChange: (String) -> Unit,
    onSubscribe: (PodcastSearchResult) -> Unit,
    onAddByUrl: (String) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }

    val message = state.message
    val messageText = message?.let { stringResource(it.textRes()) }
    val currentOnMessageShown by rememberUpdatedState(onMessageShown)
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            currentOnMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discover_title)) },
                actions = {
                    IconButton(onClick = { showUrlDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = stringResource(R.string.discover_add_by_url),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DiscoverContent(state = state, onSubscribe = onSubscribe)
        }
    }

    if (showUrlDialog) {
        AddByUrlDialog(
            isAdding = state.isAddingByUrl,
            onConfirm = { url ->
                showUrlDialog = false
                onAddByUrl(url)
            },
            onDismiss = { showUrlDialog = false },
        )
    }
}

private fun DiscoverMessage.textRes(): Int = when (this) {
    DiscoverMessage.SEARCH_FAILED -> R.string.discover_message_search_failed
    DiscoverMessage.SUBSCRIBE_FAILED -> R.string.discover_message_subscribe_failed
    DiscoverMessage.SUBSCRIBED -> R.string.discover_message_subscribed
    DiscoverMessage.INVALID_URL -> R.string.discover_message_invalid_url
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.discover_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.discover_clear),
                    )
                }
            }
        },
    )
}

@Composable
private fun DiscoverContent(
    state: DiscoverUiState,
    onSubscribe: (PodcastSearchResult) -> Unit,
) {
    when {
        state.items.isNotEmpty() -> {
            Column(Modifier.fillMaxSize()) {
                if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.result.feedUrl }) { item ->
                        DiscoverRow(item = item, onSubscribe = onSubscribe)
                    }
                }
            }
        }
        state.isSearching -> CenteredBox { CircularProgressIndicator() }
        state.hasSearched -> CenteredBox {
            Text(
                text = stringResource(R.string.discover_no_results),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        state.query.isBlank() -> CenteredBox {
            Text(
                text = stringResource(R.string.discover_initial_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        else -> Unit
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DiscoverRow(
    item: DiscoverItem,
    onSubscribe: (PodcastSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = item.result
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PodcastArtwork(
            artworkUrl = result.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.discover_author_genre, result.author, genreLabel(result.genre)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (item.status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> FilledTonalButton(onClick = { onSubscribe(result) }) {
                Text(stringResource(R.string.discover_subscribe))
            }
            SubscriptionStatus.SUBSCRIBING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            SubscriptionStatus.SUBSCRIBED -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.discover_subscribed),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun AddByUrlDialog(
    isAdding: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discover_add_by_url)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text(stringResource(R.string.discover_add_by_url_field)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }, enabled = !isAdding) {
                Text(stringResource(R.string.discover_add_by_url_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.discover_add_by_url_cancel))
            }
        },
    )
}

private val previewItems = listOf(
    DiscoverItem(
        PodcastSearchResult("https://a.example/feed", "Tech Weekly", "Ana", null, "Technology"),
        SubscriptionStatus.NOT_SUBSCRIBED,
    ),
    DiscoverItem(
        PodcastSearchResult("https://b.example/feed", "Histórias", "Bruno", null, "History"),
        SubscriptionStatus.SUBSCRIBING,
    ),
    DiscoverItem(
        PodcastSearchResult("https://c.example/feed", "Mystery Hour", "Carla", null, "Other"),
        SubscriptionStatus.SUBSCRIBED,
    ),
)

@Preview
@Composable
private fun DiscoverInitialPreview() {
    PodOuvirTheme { DiscoverScreen(DiscoverUiState(), {}, {}, {}, {}) }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverResultsDarkPreview() {
    PodOuvirTheme {
        DiscoverScreen(DiscoverUiState(query = "pod", hasSearched = true, items = previewItems), {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun DiscoverEmptyPreview() {
    PodOuvirTheme { DiscoverScreen(DiscoverUiState(query = "zzz", hasSearched = true), {}, {}, {}, {}) }
}

@Preview
@Composable
private fun DiscoverSearchingPreview() {
    PodOuvirTheme { DiscoverScreen(DiscoverUiState(query = "pod", isSearching = true), {}, {}, {}, {}) }
}

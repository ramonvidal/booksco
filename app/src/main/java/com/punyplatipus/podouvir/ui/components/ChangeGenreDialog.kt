package com.punyplatipus.podouvir.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE
import com.punyplatipus.podouvir.ui.theme.PodOuvirTheme

/**
 * AlertDialog to change a podcast's genre. Save returns the trimmed text (null if blank) and is
 * disabled while it equals the current override. "Use feed genre" is shown only with an override.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChangeGenreDialog(
    podcastTitle: String,
    feedGenre: String,
    currentOverride: String?,
    suggestions: List<String>,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(currentOverride.orEmpty()) }
    val trimmed = text.trim()
    val unchanged = trimmed == currentOverride.orEmpty().trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.components_change_genre_title))
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.components_change_genre_field)) },
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.filter { it != UNKNOWN_GENRE }.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { text = suggestion },
                            label = { Text(suggestion) },
                        )
                    }
                }
                if (currentOverride != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(
                            stringResource(
                                R.string.components_change_genre_use_feed,
                                genreLabel(feedGenre),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed.ifEmpty { null }) },
                enabled = !unchanged,
            ) { Text(stringResource(R.string.components_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.components_cancel)) }
        },
    )
}

@Preview
@Composable
private fun ChangeGenreDialogPreview() {
    PodOuvirTheme {
        ChangeGenreDialog("My Show", "Technology", "Comedy", listOf("News", "Comedy", UNKNOWN_GENRE), {}, {})
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChangeGenreDialogDarkPreview() {
    PodOuvirTheme {
        ChangeGenreDialog("My Show", "Technology", null, listOf("News", "Comedy"), {}, {})
    }
}

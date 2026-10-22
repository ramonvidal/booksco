package com.punyplatipus.podouvir.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE

/** Localised label: UNKNOWN_GENRE becomes "Other" / "Outros", any other genre is returned as-is. */
@Composable
fun genreLabel(genre: String): String =
    if (genre == UNKNOWN_GENRE) stringResource(R.string.components_genre_unknown) else genre

package com.punyplatipus.podouvir.domain.model

enum class ViewMode { GRID, LIST }

data class LibrarySettings(
    val sortOrder: SortOrder = SortOrder.TITLE,
    val viewMode: ViewMode = ViewMode.GRID,
    val selectedGenre: String? = null, // null = "All"
)

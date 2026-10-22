package com.punyplatipus.podouvir.domain.model

/** Shown when a podcast has no genre; the UI maps it to a localised label. */
const val UNKNOWN_GENRE = "Other"

data class Podcast(
    val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String? = null,
    val genre: String = "",
    val genreOverride: String? = null,
    val addedAt: Long = 0,
    val lastUpdated: Long = 0,
    val customOrder: Int = 0,
) {
    /** genreOverride (trimmed, if not blank), else genre (trimmed, if not blank), else UNKNOWN_GENRE. */
    val effectiveGenre: String
        get() = genreOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: genre.trim().takeIf { it.isNotEmpty() }
            ?: UNKNOWN_GENRE
}

enum class SortOrder { TITLE, RECENTLY_UPDATED, DATE_ADDED, CUSTOM }

fun List<Podcast>.sortPodcasts(order: SortOrder): List<Podcast> {
    val primary: Comparator<Podcast> = when (order) {
        SortOrder.TITLE -> compareBy<Podcast> { it.title.lowercase() }
        SortOrder.RECENTLY_UPDATED -> compareByDescending<Podcast> { it.lastUpdated }
        SortOrder.DATE_ADDED -> compareByDescending<Podcast> { it.addedAt }
        SortOrder.CUSTOM -> compareBy<Podcast> { it.customOrder }
    }
    return sortedWith(primary.thenBy { it.id })
}

/**
 * Groups podcasts by [Podcast.effectiveGenre] (case-insensitive), keeping [order] inside each group.
 * Keys are sorted A-Z ignoring case, with [UNKNOWN_GENRE] always last.
 */
fun List<Podcast>.groupByGenre(order: SortOrder): Map<String, List<Podcast>> {
    val groups = LinkedHashMap<String, Pair<String, MutableList<Podcast>>>()
    for (podcast in sortPodcasts(order)) {
        val genre = podcast.effectiveGenre
        val isUnknown = genre.equals(UNKNOWN_GENRE, ignoreCase = true)
        val lookup = if (isUnknown) UNKNOWN_GENRE.lowercase() else genre.lowercase()
        val name = if (isUnknown) UNKNOWN_GENRE else genre
        groups.getOrPut(lookup) { name to mutableListOf() }.second.add(podcast)
    }
    val result = LinkedHashMap<String, List<Podcast>>()
    groups.entries
        .sortedWith(
            compareBy<Map.Entry<String, Pair<String, MutableList<Podcast>>>> {
                it.value.first == UNKNOWN_GENRE
            }.thenBy { it.key },
        )
        .forEach { result[it.value.first] = it.value.second }
    return result
}

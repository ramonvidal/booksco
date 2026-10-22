package com.punyplatipus.booksco.domain.model

data class Podcast(
    val id: Long,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val genre: String,
    val addedAt: Long,
    val lastUpdated: Long,
    val customOrder: Int,
)

enum class SortOrder { TITLE, RECENTLY_UPDATED, DATE_ADDED, CUSTOM }

fun List<Podcast>.sortPodcasts(order: SortOrder): List<Podcast> = when (order) {
    SortOrder.TITLE -> sortedBy { it.title.lowercase() }
    SortOrder.RECENTLY_UPDATED -> sortedByDescending { it.lastUpdated }
    SortOrder.DATE_ADDED -> sortedByDescending { it.addedAt }
    SortOrder.CUSTOM -> sortedBy { it.customOrder }
}

/** Groups podcasts by genre (genres A–Z), keeping [order] inside each group. */
fun List<Podcast>.groupByGenre(order: SortOrder): Map<String, List<Podcast>> =
    sortPodcasts(order).groupBy { it.genre }.toSortedMap(compareBy<String> { it.lowercase() }.thenBy { it })

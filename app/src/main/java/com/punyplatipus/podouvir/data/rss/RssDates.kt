package com.punyplatipus.podouvir.data.rss

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val SECONDS_PART = Regex("""\d+(\.\d+)?""")
private val INT_PART = Regex("""\d+""")

/** "3723", "62:03", "1:02:03" (also with fractional seconds "62:03.5") -> millis; null/blank/garbage -> null. */
fun parseItunesDuration(value: String?): Long? {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) return null
    val parts = text.split(':')
    if (parts.size > 3) return null
    if (!parts.last().matches(SECONDS_PART)) return null
    if (!parts.dropLast(1).all { it.matches(INT_PART) }) return null
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val higher = parts.dropLast(1).map { it.toLongOrNull() ?: return null }
    val prefixSeconds = when (higher.size) {
        0 -> 0L
        1 -> higher[0] * 60
        else -> higher[0] * 3600 + higher[1] * 60
    }
    return prefixSeconds * 1000 + Math.round(seconds * 1000)
}

private val DATE_PATTERNS = listOf(
    "EEE, d MMM yyyy HH:mm:ss z",
    "d MMM yyyy HH:mm:ss z",
    "EEE, d MMM yyyy HH:mm z",
    "d MMM yyyy HH:mm z",
)

/** RFC 822/1123 dates as found in feeds -> epoch millis; unparseable -> null. */
fun parseRfc822Date(value: String?): Long? {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) return null
    for (pattern in DATE_PATTERNS) {
        val format = SimpleDateFormat(pattern, Locale.US)
        format.timeZone = TimeZone.getTimeZone("GMT")
        val position = ParsePosition(0)
        val date = format.parse(text, position)
        if (date != null && position.index == text.length) return date.time
    }
    return null
}

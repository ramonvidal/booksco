package com.punyplatipus.podouvir.data.rss

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

private const val ITUNES_NS = "http://www.itunes.com/dtds/podcast-1.0.dtd"

class RssParser @Inject constructor() {

    /** Does not close [input]. Throws FeedParseException for malformed XML or a document without <channel>. */
    fun parse(input: InputStream): ParsedFeed {
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(input, null)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "channel") {
                    return readChannel(parser)
                }
            }
            throw FeedParseException("Document has no <channel> element")
        } catch (e: XmlPullParserException) {
            throw FeedParseException("Malformed feed XML", e)
        } catch (e: IOException) {
            throw FeedParseException("Could not read feed", e)
        } catch (e: RuntimeException) {
            throw FeedParseException("Could not parse feed", e)
        }
    }

    private fun readChannel(parser: XmlPullParser): ParsedFeed {
        var title: String? = null
        var author: String? = null
        var description: String? = null
        var summary: String? = null
        var itunesImage: String? = null
        var imageUrl: String? = null
        var genre: String? = null
        val episodes = mutableListOf<ParsedEpisode>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw FeedParseException("Unexpected end of document")
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val ns = parser.namespace.orEmpty()
            when {
                ns == "" && parser.name == "title" -> readText(parser).let { if (title == null) title = it }
                ns == "" && parser.name == "description" ->
                    readText(parser).let { if (description == null) description = it }
                ns == ITUNES_NS && parser.name == "author" ->
                    readText(parser).let { if (author == null) author = it }
                ns == ITUNES_NS && parser.name == "summary" ->
                    readText(parser).let { if (summary == null) summary = it }
                ns == ITUNES_NS && parser.name == "image" -> {
                    val href = parser.getAttributeValue(null, "href")?.trim()
                    if (itunesImage == null && !href.isNullOrEmpty()) itunesImage = href
                    skip(parser)
                }
                ns == ITUNES_NS && parser.name == "category" -> {
                    if (genre == null) genre = parser.getAttributeValue(null, "text")?.trim().orEmpty()
                    skip(parser)
                }
                ns == "" && parser.name == "image" -> readImageUrl(parser).let { if (imageUrl == null) imageUrl = it }
                ns == "" && parser.name == "item" -> readItem(parser)?.let { episodes += it }
                else -> skip(parser)
            }
        }

        return ParsedFeed(
            title = title.orEmpty(),
            author = author.orEmpty(),
            description = description?.takeIf { it.isNotEmpty() } ?: summary.orEmpty(),
            artworkUrl = itunesImage ?: imageUrl?.takeIf { it.isNotEmpty() },
            genre = genre.orEmpty(),
            episodes = episodes,
        )
    }

    private fun readImageUrl(parser: XmlPullParser): String? {
        var url: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw FeedParseException("Unexpected end of document")
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.namespace.orEmpty() == "" && parser.name == "url") {
                readText(parser).let { if (url == null) url = it }
            } else {
                skip(parser)
            }
        }
        return url
    }

    private fun readItem(parser: XmlPullParser): ParsedEpisode? {
        var title: String? = null
        var description: String? = null
        var summary: String? = null
        var guid: String? = null
        var audioUrl: String? = null
        var duration: String? = null
        var pubDate: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw FeedParseException("Unexpected end of document")
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val ns = parser.namespace.orEmpty()
            when {
                ns == "" && parser.name == "title" -> readText(parser).let { if (title == null) title = it }
                ns == "" && parser.name == "description" ->
                    readText(parser).let { if (description == null) description = it }
                ns == "" && parser.name == "guid" -> readText(parser).let { if (guid == null) guid = it }
                ns == "" && parser.name == "pubDate" -> readText(parser).let { if (pubDate == null) pubDate = it }
                ns == "" && parser.name == "enclosure" -> {
                    val url = parser.getAttributeValue(null, "url")?.trim()
                    if (audioUrl == null && !url.isNullOrEmpty()) audioUrl = url
                    skip(parser)
                }
                ns == ITUNES_NS && parser.name == "summary" ->
                    readText(parser).let { if (summary == null) summary = it }
                ns == ITUNES_NS && parser.name == "duration" ->
                    readText(parser).let { if (duration == null) duration = it }
                else -> skip(parser)
            }
        }

        val url = audioUrl ?: return null
        return ParsedEpisode(
            guid = guid?.takeIf { it.isNotEmpty() } ?: url,
            title = title.orEmpty(),
            description = description?.takeIf { it.isNotEmpty() } ?: summary.orEmpty(),
            audioUrl = url,
            durationMs = parseItunesDuration(duration),
            publishedAt = parseRfc822Date(pubDate),
        )
    }

    /** Reads the text/CDATA of the current element (ignoring child elements) and leaves the parser at its END_TAG. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (depth == 1) sb.append(parser.text)
                XmlPullParser.END_DOCUMENT -> throw FeedParseException("Unexpected end of document")
            }
        }
        return sb.toString().trim()
    }

    /** Skips the current element entirely, leaving the parser at its END_TAG. */
    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> throw FeedParseException("Unexpected end of document")
            }
        }
    }
}

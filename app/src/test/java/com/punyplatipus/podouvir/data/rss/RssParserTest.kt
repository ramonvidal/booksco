package com.punyplatipus.podouvir.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RssParserTest {

    private val parser = RssParser()
    private val itunes = "http://www.itunes.com/dtds/podcast-1.0.dtd"

    private fun fixture(name: String): InputStream =
        javaClass.classLoader!!.getResourceAsStream("rss/$name")!!

    private fun parseXml(xml: String): ParsedFeed =
        parser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun feed(channelBody: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="$itunes"><channel>$channelBody</channel></rss>"""

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `parses channel metadata from a simple feed`() {
        val f = parser.parse(fixture("simple_feed.xml"))
        assertEquals("Simple Show", f.title)
        assertEquals("Jane Host", f.author)
        assertEquals("A simple show about things.", f.description)
        assertEquals("https://example.com/art.jpg", f.artworkUrl)
        assertEquals("Technology", f.genre)
    }

    @Test
    fun `parses all episodes in document order`() {
        val eps = parser.parse(fixture("simple_feed.xml")).episodes
        assertEquals(listOf("guid-1", "guid-2", "guid-3"), eps.map { it.guid })
        assertEquals(listOf("Episode One", "Episode Two", "Episode Three"), eps.map { it.title })
        assertEquals(
            listOf("https://example.com/1.mp3", "https://example.com/2.mp3", "https://example.com/3.mp3"),
            eps.map { it.audioUrl },
        )
        assertEquals(listOf(3_723_000L, 1_800_000L, 2_730_000L), eps.map { it.durationMs })
        assertEquals(
            listOf(
                millis("2026-10-21T08:00:00Z"),
                millis("2026-10-14T08:00:00Z"),
                millis("2026-10-07T08:00:00Z"),
            ),
            eps.map { it.publishedAt },
        )
    }

    @Test
    fun `episode fields map from item elements`() {
        val ep = parser.parse(fixture("simple_feed.xml")).episodes.first()
        assertEquals("guid-1", ep.guid)
        assertEquals("https://example.com/1.mp3", ep.audioUrl)
        assertEquals("First episode", ep.description)
        assertEquals(3_723_000L, ep.durationMs)
        assertEquals(millis("2026-10-21T08:00:00Z"), ep.publishedAt)
    }

    @Test
    fun `missing guid falls back to enclosure url`() {
        val f = parseXml(
            feed("""<title>T</title><item><title>E</title><enclosure url="https://e.com/a.mp3"/></item>"""),
        )
        assertEquals(1, f.episodes.size)
        assertEquals("https://e.com/a.mp3", f.episodes[0].guid)
        assertEquals(f.episodes[0].audioUrl, f.episodes[0].guid)
    }

    @Test
    fun `item without enclosure is skipped`() {
        val f = parseXml(
            feed(
                """<title>T</title>
                <item><guid>a</guid><enclosure url="https://e.com/a.mp3"/></item>
                <item><guid>b</guid></item>
                <item><guid>c</guid><enclosure url="https://e.com/c.mp3"/></item>""",
            ),
        )
        assertEquals(listOf("a", "c"), f.episodes.map { it.guid })
    }

    @Test
    fun `description falls back to itunes summary`() {
        val f = parseXml(
            feed(
                """<title>T</title><itunes:summary>Channel summary</itunes:summary>
                <item><itunes:summary>Item summary</itunes:summary><enclosure url="https://e.com/a.mp3"/></item>""",
            ),
        )
        assertEquals("Channel summary", f.description)
        assertEquals("Item summary", f.episodes[0].description)
    }

    @Test
    fun `description prefers description over itunes summary`() {
        val f = parseXml(
            feed(
                """<title>T</title>
                <itunes:summary>Channel summary</itunes:summary><description>Channel desc</description>
                <item><itunes:summary>Item summary</itunes:summary><description>Item desc</description>
                <enclosure url="https://e.com/a.mp3"/></item>""",
            ),
        )
        assertEquals("Channel desc", f.description)
        assertEquals("Item desc", f.episodes[0].description)
    }

    @Test
    fun `author title description and genre default to empty string`() {
        val f = parseXml(feed("""<title>Only Title</title><item><enclosure url="https://e.com/a.mp3"/></item>"""))
        assertEquals("Only Title", f.title)
        assertEquals("", f.author)
        assertEquals("", f.description)
        assertEquals("", f.genre)
        assertEquals("", f.episodes[0].title)
        assertEquals("", f.episodes[0].description)
    }

    @Test
    fun `artwork falls back to image url then null`() {
        val a = parseXml(feed("""<title>T</title><image><url>https://e.com/img.png</url></image>"""))
        assertEquals("https://e.com/img.png", a.artworkUrl)

        val b = parseXml(feed("""<title>T</title>"""))
        assertNull(b.artworkUrl)

        val both = parseXml(
            feed(
                """<title>T</title><image><url>https://e.com/img.png</url></image>
                <itunes:image href="https://e.com/itunes.png"/>""",
            ),
        )
        assertEquals("https://e.com/itunes.png", both.artworkUrl)
    }

    @Test
    fun `genre is first top-level category and ignores nested ones`() {
        val f = parseXml(
            feed(
                """<title>T</title>
                <itunes:category text="Tech"><itunes:category text="Gadgets"/></itunes:category>
                <itunes:category text="News"/>""",
            ),
        )
        assertEquals("Tech", f.genre)
    }

    @Test
    fun `channel title and description are not overwritten by item or image values`() {
        val f = parseXml(
            feed(
                """<image><title>Image Title</title><url>https://e.com/i.png</url></image>
                <item><title>Item Title</title><description>Item desc</description>
                <enclosure url="https://e.com/a.mp3"/></item>
                <title>Channel Title</title><description>Channel desc</description>
                <item><title>Item 2</title><description>Item desc 2</description>
                <enclosure url="https://e.com/b.mp3"/></item>""",
            ),
        )
        assertEquals("Channel Title", f.title)
        assertEquals("Channel desc", f.description)
        assertEquals(listOf("Item Title", "Item 2"), f.episodes.map { it.title })
    }

    @Test
    fun `text is trimmed and CDATA is read as text`() {
        val f = parseXml(
            feed(
                "<title>\n   Padded Title \n</title>" +
                    "<item><title>  E1  </title><description><![CDATA[<p>Hi <b>there</b></p>]]></description>" +
                    "<enclosure url=\"https://e.com/a.mp3\"/></item>",
            ),
        )
        assertEquals("Padded Title", f.title)
        assertEquals("E1", f.episodes[0].title)
        assertEquals("<p>Hi <b>there</b></p>", f.episodes[0].description)
    }

    @Test
    fun `messy fixture is parsed as specified`() {
        val f = parser.parse(fixture("messy_feed.xml"))
        assertEquals("Messy Show", f.title)
        assertEquals("Summary only description", f.description)
        assertEquals("https://example.com/fallback.png", f.artworkUrl)
        assertEquals("Society", f.genre)
        assertEquals(2, f.episodes.size)
        val first = f.episodes[0]
        assertEquals("Item With CDATA", first.title)
        assertEquals("<p>Hi <b>there</b></p>", first.description)
        assertEquals("https://example.com/a.mp3", first.guid)
        assertNull(first.durationMs)
        assertNotNull(first.publishedAt)
        val second = f.episodes[1]
        assertEquals("g-3", second.guid)
        assertEquals("item summary", second.description)
        assertEquals(90_000L, second.durationMs)
    }

    @Test
    fun `missing or invalid duration and pubDate give null`() {
        val f = parseXml(
            feed(
                """<title>T</title>
                <item><guid>a</guid><enclosure url="https://e.com/a.mp3"/></item>
                <item><guid>b</guid><enclosure url="https://e.com/b.mp3"/>
                <itunes:duration>abc</itunes:duration><pubDate>not a date</pubDate></item>""",
            ),
        )
        assertEquals(2, f.episodes.size)
        f.episodes.forEach {
            assertNull(it.durationMs)
            assertNull(it.publishedAt)
        }
    }

    @Test
    fun `feed with no items returns empty episode list`() {
        val f = parseXml(feed("""<title>T</title><itunes:author>A</itunes:author><description>D</description>"""))
        assertTrue(f.episodes.isEmpty())
        assertEquals("T", f.title)
        assertEquals("A", f.author)
        assertEquals("D", f.description)
    }

    @Test
    fun `malformed xml throws FeedParseException`() {
        try {
            parser.parse(fixture("malformed.xml"))
            fail("expected FeedParseException")
        } catch (e: FeedParseException) {
            assertNotNull(e.cause)
        }
    }

    @Test(expected = FeedParseException::class)
    fun `empty input throws FeedParseException`() {
        parser.parse(ByteArrayInputStream(ByteArray(0)))
    }

    @Test(expected = FeedParseException::class)
    fun `xml without channel throws FeedParseException`() {
        parser.parse(fixture("not_rss.xml"))
    }

    @Test
    fun `input stream is not closed by parse`() {
        var closed = false
        val bytes = fixture("simple_feed.xml").readBytes()
        val stream = object : java.io.FilterInputStream(ByteArrayInputStream(bytes)) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        parser.parse(stream)
        assertFalse(closed)
    }

    @Test
    fun `iTunes elements are matched by namespace not prefix`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:it="$itunes" xmlns:itunes="http://example.com/not-itunes">
<channel><title>T</title><it:author>Right Author</it:author>
<itunes:summary>Wrong summary</itunes:summary></channel></rss>"""
        val f = parseXml(xml)
        assertEquals("Right Author", f.author)
        assertEquals("", f.description)

        val other = parseXml(
            """<rss version="2.0" xmlns:itunes="http://example.com/not-itunes">
<channel><title>T</title><itunes:author>Impostor</itunes:author></channel></rss>""",
        )
        assertEquals("", other.author)
    }
}

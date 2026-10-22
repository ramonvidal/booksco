package com.punyplatipus.podouvir.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class RssDatesTest {

    private val oct21 = Instant.parse("2026-10-21T08:00:00Z").toEpochMilli()

    // parseItunesDuration

    @Test
    fun `plain seconds are converted to millis`() {
        assertEquals(3_723_000L, parseItunesDuration("3723"))
    }

    @Test
    fun `mm_ss format is converted`() {
        assertEquals(3_723_000L, parseItunesDuration("62:03"))
    }

    @Test
    fun `hh_mm_ss format is converted`() {
        assertEquals(3_723_000L, parseItunesDuration("1:02:03"))
    }

    @Test
    fun `fractional seconds are kept`() {
        assertEquals(3_723_500L, parseItunesDuration("62:03.5"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(90_000L, parseItunesDuration(" 90 "))
    }

    @Test
    fun `zero duration is zero not null`() {
        assertEquals(0L, parseItunesDuration("0"))
        assertEquals(0L, parseItunesDuration("00:00:00"))
    }

    @Test
    fun `duration null blank and garbage return null`() {
        listOf(null, "", "  ", "abc", "1:xx", "1:2:3:4", "-5").forEach {
            assertNull("input: $it", parseItunesDuration(it))
        }
    }

    // parseRfc822Date

    @Test
    fun `full RFC 1123 GMT date parses`() {
        assertEquals(oct21, parseRfc822Date("Tue, 21 Oct 2026 08:00:00 GMT"))
    }

    @Test
    fun `numeric offset is applied`() {
        assertEquals(oct21, parseRfc822Date("Tue, 21 Oct 2026 10:00:00 +0200"))
    }

    @Test
    fun `negative offset is applied`() {
        assertEquals(oct21, parseRfc822Date("Tue, 21 Oct 2026 03:00:00 -0500"))
    }

    @Test
    fun `single digit day parses`() {
        assertEquals(
            Instant.parse("2026-10-01T08:00:00Z").toEpochMilli(),
            parseRfc822Date("Wed, 1 Oct 2026 08:00:00 GMT"),
        )
    }

    @Test
    fun `missing weekday parses`() {
        assertEquals(oct21, parseRfc822Date("21 Oct 2026 08:00:00 GMT"))
    }

    @Test
    fun `missing seconds parses`() {
        assertEquals(oct21, parseRfc822Date("Tue, 21 Oct 2026 08:00 GMT"))
    }

    @Test
    fun `result is independent of default locale and timezone`() {
        val oldLocale = Locale.getDefault()
        val oldZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale("pt", "BR"))
            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
            assertEquals(oct21, parseRfc822Date("Tue, 21 Oct 2026 08:00:00 GMT"))
        } finally {
            Locale.setDefault(oldLocale)
            TimeZone.setDefault(oldZone)
        }
    }

    @Test
    fun `date null blank and garbage return null`() {
        listOf(null, "", "yesterday", "2026-10-21").forEach {
            assertNull("input: $it", parseRfc822Date(it))
        }
    }
}

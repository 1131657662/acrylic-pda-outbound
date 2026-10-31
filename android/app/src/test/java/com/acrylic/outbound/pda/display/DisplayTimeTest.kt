package com.acrylic.outbound.pda.display

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class DisplayTimeTest {
    @Test fun utcAndExplicitOffsetsDescribeTheSameInstant() {
        listOf("2026-09-20T04:37:05Z", "2026-09-20T04:37:05.987654321Z",
            "2026-09-20T12:37:05+08:00", "2026-09-19T21:37:05-07:00").forEach {
            assertEquals(it, "2026-09-20 12:37:05", DisplayTime.formatDateTime(it))
        }
    }

    @Test fun calendarBoundariesAndMidnightUsePaddedTwentyFourHourTime() {
        mapOf("2026-09-19T16:00:00Z" to "2026-09-20 00:00:00",
            "2026-12-31T16:00:00Z" to "2027-01-01 00:00:00",
            "2024-02-29T16:02:03.999Z" to "2024-03-01 00:02:03",
            "2026-01-01T17:02:03Z" to "2026-01-02 01:02:03").forEach { (input, expected) ->
            assertEquals(input, expected, DisplayTime.formatDateTime(input))
        }
    }

    @Test fun absentMalformedAndAmbiguousValuesDoNotInventTimes() {
        listOf(null, "", " ", "invalid", "2026-09-20", "2026-09-20T12:37:05",
            "2026-02-29T00:00:00Z", "2026-04-31T00:00:00Z", "2026-13-20T00:00:00Z",
            "2026-09-20T24:00:00Z", "2026-09-20T04:37:60Z", "2026-09-20T04:37:05+18:01").forEach {
            assertEquals(it, "暂无记录", DisplayTime.formatDateTime(it))
        }
    }

    @Test fun epochMillisecondsAreDisplayedWithoutRoundingOrChangingTheInstant() {
        val original = Instant.parse("2026-09-20T04:37:05.999Z")
        assertEquals("2026-09-20 12:37:05", DisplayTime.formatEpochMillis(original.toEpochMilli()))
        assertEquals("1970-01-01 08:00:00", DisplayTime.formatEpochMillis(0))
        assertEquals("暂无记录", DisplayTime.formatEpochMillis(null))
        assertEquals("2026-09-20T04:37:05.999Z", original.toString())
    }

    @Test fun deviceTimezoneAndLanguageNeverChangeBusinessDisplay() {
        val zone = TimeZone.getDefault(); val locale = Locale.getDefault()
        try {
            for (id in listOf("UTC", "America/Los_Angeles", "Asia/Shanghai")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id)); Locale.setDefault(Locale.forLanguageTag("ar-EG"))
                assertEquals("2026-09-20 12:37:05", DisplayTime.formatDateTime("2026-09-20T04:37:05Z"))
            }
        } finally { TimeZone.setDefault(zone); Locale.setDefault(locale) }
    }
}

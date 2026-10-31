package com.acrylic.outbound.pda.display

import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display only: never use these strings for authorization, persistence or signatures. */
object DisplayTime {
    private const val MISSING = "暂无记录"
    private val timestamp = Regex("""\d{4}-\d{2}-\d{2}T[0-2]\d:[0-5]\d:[0-5]\d(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})""")
    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
        .withZone(ZoneId.of("Asia/Shanghai"))

    fun formatDateTime(value: String?): String {
        if (value == null || !timestamp.matches(value)) return MISSING
        return try { formatter.format(OffsetDateTime.parse(value).toInstant()) }
        catch (_: DateTimeException) { MISSING }
    }

    fun formatEpochMillis(value: Long?): String {
        if (value == null) return MISSING
        return try { formatter.format(Instant.ofEpochMilli(value)) }
        catch (_: DateTimeException) { MISSING }
    }
}

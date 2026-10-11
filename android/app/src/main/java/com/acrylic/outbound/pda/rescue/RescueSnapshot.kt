package com.acrylic.outbound.pda.rescue

import org.json.JSONObject

/** Decimal integers are strings so independent consumers never round long sequence IDs. */
data class RescueSnapshot(val order: JSONObject, val captures: List<JSONObject>, val scans: List<JSONObject>,
    val events: List<JSONObject>, val lines: List<JSONObject>, val originals: Map<String, ByteArray> = emptyMap(),
    val dispositions: List<JSONObject> = emptyList())

data class RescueSigner(val publicKey: ByteArray, val sign: (String) -> String)

object RescueText {
    fun escape(value: String): String = buildString {
        value.forEach { char -> append(when (char) {
            '\\' -> "\\\\"; '\t' -> "\\t"; '\n' -> "\\n"; '\r' -> "\\r"
            else -> if (char.code < 32 || char.code == 127) "\\u%04x".format(char.code) else char.toString()
        }) }
    }

    fun cell(value: String): String {
        val escaped = escape(value)
        return if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$escaped" else escaped
    }

    fun tsv(headings: List<String>, rows: List<List<String>>): ByteArray =
        (listOf(headings).plus(rows).joinToString("\n", postfix = "\n") { row -> row.joinToString("\t", transform = ::cell) })
            .toByteArray(Charsets.UTF_8)

    fun jsonLines(rows: List<JSONObject>): ByteArray =
        rows.joinToString("\n", postfix = if (rows.isEmpty()) "" else "\n").toByteArray(Charsets.UTF_8)
}

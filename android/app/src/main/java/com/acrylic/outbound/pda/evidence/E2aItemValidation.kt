package com.acrylic.outbound.pda.evidence

import org.json.JSONObject
import java.util.UUID

internal object E2aItemValidation {
    private val fields = setOf("itemKind", "originalRecordId", "objectKind", "originalObjectId",
        "itemBytesSha256", "byteCount", "originalDeviceSeq", "recordSha256", "recordStatus",
        "baleId", "originalCycle", "captureId")
    private val kinds = setOf("CAPTURE", "SCAN", "AUDIT", "STOP", "ARCHIVE")
    private val objects = setOf("LOCAL_FROZEN_PAYLOAD", "CAPTURE_RECORDS", "AUDIT_RECORDS", "AUDIT_ARCHIVE")
    private val hash = Regex("[0-9a-f]{64}")

    fun validate(item: JSONObject): Long {
        check(item.keys().asSequence().toSet() == fields)
        val kind = text(item, "itemKind", 24)
        check(kind in kinds && text(item, "objectKind", 32) in objects)
        uuid(text(item, "originalRecordId", 36))
        uuid(text(item, "originalObjectId", 36))
        check(text(item, "itemBytesSha256", 64).matches(hash))
        check(text(item, "recordSha256", 64).matches(hash))
        val status = text(item, "recordStatus", 40)
        check(status.none(Char::isISOControl))
        val bytes = number(item, "byteCount")
        number(item, "originalDeviceSeq")
        check(bytes <= 33554432)
        val scan = kind == "SCAN"
        for (field in listOf("baleId", "captureId")) {
            check(item.isNull(field) != scan)
            if (scan) uuid(text(item, field, 36))
        }
        check(item.isNull("originalCycle") != scan)
        if (scan) number(item, "originalCycle")
        return bytes
    }

    fun uuid(value: String) {
        check(UUID.fromString(value).toString() == value)
    }

    private fun text(item: JSONObject, field: String, limit: Int): String {
        val value = item.get(field)
        check(value is String && value.isNotBlank() && value.length <= limit)
        return value
    }

    private fun number(item: JSONObject, field: String): Long {
        val value = item.get(field)
        check(value is Long || value is Int)
        val number = (value as Number).toLong()
        check(number in 1..9007199254740991L)
        return number
    }
}

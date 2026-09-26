package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object E2aWire {
    data class Identity(val manifestId: String, val snapshotId: String, val sourceId: String,
        val submissionId: String, val workRound: Long, val manifestVersion: Long = 1)
    data class Page(val canonicalCore: String, val hash: String)
    data class Manifest(val canonicalCore: String, val bytesHash: String, val setHash: String)

    fun page(identity: Identity, number: Int, count: Int, previous: String?, items: JSONArray): Page {
        listOf(identity.manifestId, identity.snapshotId, identity.sourceId,
            identity.submissionId).forEach(E2aItemValidation::uuid)
        check(identity.workRound in 1..9007199254740991L &&
            identity.manifestVersion in 1..9007199254740991L)
        check(number in 1..count && count in 1..4096 && (number == 1) == (previous == null))
        check(previous == null || previous.matches(Regex("[0-9a-f]{64}")))
        val references = JSONArray(items.toString())
        var total = 0L
        val keys = HashSet<String>()
        val objects = HashSet<String>()
        for (index in 0 until references.length()) {
            val item = references.getJSONObject(index)
            val bytes = E2aItemValidation.validate(item)
            check(keys.add(item.getString("itemKind") + "/" + item.getString("originalRecordId")))
            check(objects.add(item.getString("originalObjectId")))
            total = Math.addExact(total, bytes)
        }
        check(total <= 134217728)
        val core = JSONObject().put("manifestId", identity.manifestId).put("manifestVersion", identity.manifestVersion)
            .put("snapshotId", identity.snapshotId).put("pageNo", number).put("pageCount", count)
            .put("isFinal", number == count).put("itemCount", references.length())
            .put("byteCount", total).put("prevPageHash", previous ?: JSONObject.NULL).put("items", references)
        val canonical = SubmissionJson.canonical(core)
        check(canonical.toByteArray(Charsets.UTF_8).size <= 262144)
        return Page(canonical, digest("PAGE", canonical))
    }

    fun manifest(identity: Identity, pages: List<Page>): Manifest = manifest(identity, pages.size, pages.asSequence())

    fun manifest(identity: Identity, pageCount: Int, pages: Sequence<Page>): Manifest {
        check(identity.workRound > 0 && identity.manifestVersion > 0 && pageCount in 1..4096)
        var entries = 0L
        var bytes = 0L
        var previous: String? = null
        val roots = JSONArray()
        val records = HashSet<String>()
        val objects = HashSet<String>()
        pages.forEachIndexed { index, page ->
            val core = JSONObject(page.canonicalCore)
            check(core.getString("manifestId") == identity.manifestId
                && core.getString("snapshotId") == identity.snapshotId
                && core.getLong("manifestVersion") == identity.manifestVersion)
            val rebuilt = page(identity, index + 1, pageCount, previous, core.getJSONArray("items"))
            check(rebuilt == page)
            val items = core.getJSONArray("items")
            for (ordinal in 0 until items.length()) {
                val item = items.getJSONObject(ordinal)
                check(records.add(item.getString("itemKind") + "/" + item.getString("originalRecordId")))
                check(objects.add(item.getString("originalObjectId")))
            }
            entries = Math.addExact(entries, core.getLong("itemCount"))
            bytes = Math.addExact(bytes, core.getLong("byteCount"))
            roots.put(JSONObject().put("pageNo", index + 1).put("pageHash", page.hash))
            previous = page.hash
        }
        check(bytes <= 134217728 && roots.length() == pageCount)
        val core = JSONObject().put("schemaVersion", 1).put("manifestId", identity.manifestId)
            .put("manifestVersion", identity.manifestVersion).put("snapshotId", identity.snapshotId)
            .put("source", "TEMP").put("sourceId", identity.sourceId)
            .put("submissionId", identity.submissionId).put("workRound", identity.workRound)
            .put("pageCount", pageCount).put("entryCount", entries).put("byteCount", bytes)
            .put("pages", roots).put("endPageNo", pageCount)
        val canonical = SubmissionJson.canonical(core)
        check(canonical.toByteArray(Charsets.UTF_8).size <= 262144)
        return Manifest(canonical, digest("MANIFEST", canonical), digest("SET", canonical))
    }

    fun item(kind: String, recordId: String, objectKind: String, objectId: String, raw: ByteArray,
        sequence: Long, recordHash: String, status: String, baleId: String? = null,
        cycle: Long? = null, captureId: String? = null): JSONObject {
        check(kind in setOf("CAPTURE", "SCAN", "AUDIT", "STOP", "ARCHIVE"))
        check(objectKind in setOf("LOCAL_FROZEN_PAYLOAD", "CAPTURE_RECORDS", "AUDIT_RECORDS", "AUDIT_ARCHIVE"))
        check(raw.size in 1..33554432 && sequence > 0 && recordHash.matches(Regex("[0-9a-f]{64}")))
        check(status.isNotBlank() && status.length <= 40 && status.none(Char::isISOControl))
        check(if (kind == "SCAN") baleId != null && captureId != null && cycle != null && cycle > 0
            else baleId == null && captureId == null && cycle == null)
        val reference = JSONObject().put("itemKind", kind).put("originalRecordId", recordId)
            .put("objectKind", objectKind).put("originalObjectId", objectId)
            .put("itemBytesSha256", sha(raw)).put("byteCount", raw.size)
            .put("originalDeviceSeq", sequence).put("recordSha256", recordHash)
            .put("recordStatus", status).put("baleId", baleId ?: JSONObject.NULL)
            .put("originalCycle", cycle ?: JSONObject.NULL).put("captureId", captureId ?: JSONObject.NULL)
        E2aItemValidation.validate(reference)
        return reference
    }

    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun digest(domain: String, canonical: String): String = sha(
        ("ACRYLIC-R06/E2A/$domain/1\n" + canonical).toByteArray(Charsets.UTF_8))
}

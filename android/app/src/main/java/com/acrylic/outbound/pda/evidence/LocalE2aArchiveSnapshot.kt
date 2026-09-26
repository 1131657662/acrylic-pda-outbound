package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.retention.AuditArchive
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.util.UUID

object LocalE2aArchiveSnapshot {
    fun originals(stop: LocalE2aStop, archives: List<AuditArchive>): List<LocalE2aObject> {
        check(archives.size.toLong() == stop.archiveSnapshotCount)
        return archives.map { archive ->
            check(archive.originDeviceId == stop.freezeDeviceId &&
                archive.recoveryEpoch.matches(Regex("[1-9][0-9]*")) &&
                archive.recoveryEpoch.toLong() <= stop.sourceRecoveryEpoch &&
                archive.throughSeq in 1..stop.auditThroughSeq)
            val proof = JSONObject(checkNotNull(archive.proofJson))
            val key = JSONObject(checkNotNull(archive.signingKeyJson))
            AuditArchiveVerifier.verify(archive, proof, key, stop.warehouseId)
            val raw = SubmissionJson.canonical(proof).toByteArray(Charsets.UTF_8)
            val canonicalKey = SubmissionJson.canonical(key)
            check(raw.contentEquals(archive.proofJson.toByteArray(Charsets.UTF_8)) &&
                canonicalKey == archive.signingKeyJson)
            LocalE2aObject(stop.clientOrderId, "ARCHIVE", recordId(stop.freezeDeviceId,
                archive.recoveryEpoch.toLong(), archive.afterSeq), UUID.randomUUID().toString(),
                archive.throughSeq, "ARCHIVED", E2aWire.sha(raw), raw, canonicalKey)
        }
    }

    fun verify(db: PdaDatabase, stop: LocalE2aStop): Pair<Long, Long> {
        var after = ""
        var count = 0L
        var bytes = 0L
        while (true) {
            val page = db.localE2aObjects().archives(stop.clientOrderId, after)
            if (page.isEmpty()) break
            for (row in page) {
                check(row.recordId > after)
                bytes = Math.addExact(bytes, verifyRow(row, stop))
                count++
                after = row.recordId
            }
        }
        check(count == stop.archiveSnapshotCount)
        return count to bytes
    }

    private fun verifyRow(row: LocalE2aObject, stop: LocalE2aStop): Long {
        check(row.recordStatus == "ARCHIVED" && row.deviceSeq in 1..stop.auditThroughSeq &&
            row.rawBytes.size in 1..33554432 && E2aWire.sha(row.rawBytes) == row.bytesSha256)
        E2aItemValidation.uuid(row.objectId)
        val proof = JSONObject(row.rawBytes.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(proof).toByteArray(Charsets.UTF_8).contentEquals(row.rawBytes))
        val epoch = proof.getString("recoveryEpoch").toLong()
        val sequence = proof.getString("throughSeq").toLong()
        val from = proof.getString("afterSeq").toLong()
        check(epoch in 1..stop.sourceRecoveryEpoch && sequence == row.deviceSeq &&
            row.recordId == recordId(stop.freezeDeviceId, epoch, from))
        val key = checkNotNull(row.signingKeyJson)
        check(SubmissionJson.canonical(JSONObject(key)) == key)
        val archive = AuditArchive(epoch.toString(), stop.freezeDeviceId, from, sequence,
            proof.getString("payloadSha256"), "", proofJson = row.rawBytes.toString(Charsets.UTF_8),
            signingKeyJson = key)
        AuditArchiveVerifier.verify(archive, proof, JSONObject(key), stop.warehouseId)
        return row.rawBytes.size.toLong() + key.toByteArray(Charsets.UTF_8).size
    }

    fun recordId(device: String, epoch: Long, after: Long): String = UUID.nameUUIDFromBytes(
        "acrylic-pda-audit-archive/$device/$epoch/$after".toByteArray(Charsets.UTF_8)).toString()
}

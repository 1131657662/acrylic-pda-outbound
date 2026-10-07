package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.evidence.LocalE2aStopRepository
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.work.WorkDevice
import org.json.JSONObject

/** Imports the explicitly synthetic stopped source; only the following continuation is Android-created. */
internal class TemporaryContinuationHttpSource(private val fixture: TemporaryFixture) {
    private val db get() = fixture.db
    private val body = fixture.fixture.getJSONObject("original")
    val id: String = body.getJSONObject("source").getString("clientOrderId")
    private val store get() = TemporaryWorkStore(db, fixture, true) { TimeSample(1000, fixture.time.toEpochMilli(), 1) }

    fun import() {
        fixture.activate()
        db.runInTransaction {
            val draft = draft(); db.temporaryDrafts().insert(draft); lines()
            val captures = body.getJSONArray("captures")
            for (i in 0 until captures.length()) capture(captures.getJSONObject(i))
            val scans = body.getJSONArray("scans")
            for (i in 0 until scans.length()) scan(scans.getJSONObject(i))
            val events = body.getJSONArray("events")
            for (i in 0 until events.length()) event(events.getJSONObject(i))
            db.work().insertDevice(WorkDevice(draft.creatorDeviceId, draft.nextScanSeq, events.length().toLong() + 1))
            val payload = SubmissionJson.canonical(body)
            val frozen = TemporarySubmission(body.getString("submissionId"), id, payload, JwtVerifier.sha(payload.toByteArray()), "ABORTED")
            db.temporarySubmissions().insert(frozen)
            val sourceStore = store; sourceStore.load(id)
            LocalE2aStopRepository(sourceStore).createAtFreeze(draft, frozen)
        }
    }

    private fun draft(): TemporaryDraft {
        val plan = body.getJSONObject("temporaryOrder"); val identity = fixture.manifest.getJSONObject("identity")
        return TemporaryDraft(id, identity.getString("deviceId"), identity.getString("userId"), identity.getString("warehouseId"),
            body.getString("authorizationId"), body.getString("temporaryNo"), body.getString("closedAt"),
            plan.getString("vehicleNo"), plan.getString("containerNo"), plan.getString("reason"), plan.getString("revision").toLong(),
            "FROZEN", body.getString("captureHighWatermark").toLong() + 1, body.getString("planSha256"))
    }

    private fun lines() {
        val lines = body.getJSONObject("temporaryOrder").getJSONArray("lines")
        db.temporaryDrafts().insertLines((0 until lines.length()).map { lines.getJSONObject(it) }.map {
            TemporaryLine(id, it.getString("deliveryCode"), it.getString("productId"), it.getString("model"),
                it.getString("name"), it.getString("plannedCount").toLong())
        })
    }

    private fun capture(c: JSONObject) {
        db.temporaryScans().insertCapture(TemporaryCapture(c.getString("id"), id, c.getString("originDeviceId"),
            c.getString("operatorId"), c.getString("orderScanSeq").toLong(), c.getString("deviceCaptureSeq").toLong(),
            c.getString("rawInput"), c.getString("barcode"), c.getString("inputSource"), c.getString("receivedWallMs").toLong(),
            c.getString("receivedElapsedMs").toLong(), c.getInt("receivedBoot"), c.getString("authorizationId"),
            c.getString("authorizationEpoch"), c.getString("snapshotId"), c.getString("sourceRecoveryEpoch"),
            c.getString("orderRevision").toLong(), c.getString("expectedDispatchCycle").toLong(), c.getString("state"),
            c.getString("error"), c.getString("confirmedAt"), c.getString("failureAckAt")))
    }

    private fun scan(s: JSONObject) {
        db.temporaryScans().insertScan(TemporaryScan(s.getString("captureId"), id, s.getString("orderScanSeq").toLong(),
            s.getString("barcode"), s.getString("baleId"), s.getString("deliveryCode"), s.getString("model"), s.getString("name"),
            s.getString("rawWeight"), s.getString("weightGrams").toLong(), s.getString("expectedDispatchCycle").toLong(),
            s.getString("savedAt"), s.getString("localSeq").toLong(), s.getString("status")))
        db.temporaryScans().insertGuard(TemporaryGuard(s.getString("barcode"), s.getString("captureId")))
    }

    private fun event(e: JSONObject) {
        db.temporaryScans().insertEvent(TemporaryEvent(e.getString("id"), id,
            if (e.isNull("captureId")) null else e.getString("captureId"), e.getString("originDeviceId"), e.getString("operatorId"),
            e.getString("localSeq").toLong(), e.getString("type"), e.getString("detail"), e.getString("wallMs").toLong(),
            e.getString("elapsedMs").toLong(), e.getInt("boot")))
    }
}

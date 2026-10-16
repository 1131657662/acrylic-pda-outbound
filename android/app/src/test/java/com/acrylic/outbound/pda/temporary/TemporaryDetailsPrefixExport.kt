package com.acrylic.outbound.pda.temporary

import org.json.JSONObject
import java.io.File

/** New synthetic read-only device input; this never claims an original PDA or restores cancelled handover scope. */
internal object TemporaryDetailsPrefixExport {
    fun save(fixture: TemporaryContinuationFixture, targetId: String, barcode: String) {
        val root = System.getenv("PDA_TEMPORARY_DETAILS_PREFIX_OUTPUT") ?: return
        val directory = File(root).apply { check(!exists()); check(mkdirs()) }
        val database = File(directory, "synthetic-temporary-prefix.db")
        fixture.db.openHelper.writableDatabase.query("VACUUM INTO ?", arrayOf(database.path)).use { it.moveToFirst() }
        val session = checkNotNull(fixture.work.fixture.auth)
        File(directory, "synthetic-session.json").writeText(session.toString(2))
        File(directory, "identity.json").writeText(JSONObject().put("syntheticOnly", true)
            .put("clientOrderId", targetId).put("sourceClientOrderId", fixture.work.id)
            .put("userId", session.getString("userId")).put("deviceId", session.getString("deviceId"))
            .put("barcode", barcode).put("sourceSubmissionId", fixture.frozen.id)
            .put("sourcePayloadSha256", fixture.frozen.sha256).put("state", "CONTINUATION_BLOCKED").toString(2))
    }
}

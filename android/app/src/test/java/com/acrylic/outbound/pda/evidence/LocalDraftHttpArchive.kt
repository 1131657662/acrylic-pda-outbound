package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.retention.AuditArchiveRepository
import com.acrylic.outbound.pda.sync.AuditRepository
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject
import org.junit.Assert.*

/** Actual audit upload and original archive receipt before the Room stop snapshot. */
internal object LocalDraftHttpArchive {
    fun create(work: TemporaryWorkFixture, connection: E2aRealHttpConnection) {
        fun auth() = JSONObject(checkNotNull(connection.current()).toString())
        val audit = AuditRepository(work.db, ::auth) { raw: String, epoch: String ->
            connection.call(TemporaryConnection.Call("pda/audit/events", auth(), JSONObject(raw), epoch = epoch))
        }
        assertTrue(audit.syncAudit().supported)
        val archive = AuditArchiveRepository(work.db, ::auth) { restoring: Boolean, body: JSONObject, epoch: String ->
            check(!restoring)
            connection.call(TemporaryConnection.Call("pda/audit/archives", auth(), body, epoch = epoch))
        }
        assertTrue(archive.syncArchive().supported)
        val rows = work.db.auditArchives().rows("1", auth().getString("deviceId"))
        assertEquals(1, rows.size); assertNotNull(rows.single().proofJson)
    }
}

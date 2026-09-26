package com.acrylic.outbound.pda.temporary

import org.json.JSONObject
import org.junit.Assert.*

object TemporaryArchiveStorageChecks {
    fun rollback(work: TemporaryWorkFixture, request: TemporaryArchiveRequest, hash: String) {
        work.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_archive BEFORE UPDATE ON temporary_archive_response
            WHEN NEW.state='VERIFIED' BEGIN SELECT RAISE(ABORT,'injected archive final write failure'); END""")
        assertTrue(runCatching { TemporaryArchiveRepository(work.db).verify(request.submissionId, "1", hash, work.fixture.key, work.fixture.key) }.isFailure)
        assertNull(work.db.temporaryArchives().evidence(request.submissionId, "1"))
        assertNotNull(work.db.temporaryArchives().response(request.submissionId, "1", hash))
        work.db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_archive"); work.reopen()
    }

    fun negatives(work: TemporaryWorkFixture, request: TemporaryArchiveRequest, originalHash: String, negatives: JSONObject) {
        val repository = TemporaryArchiveRepository(work.db); val key = work.fixture.key
        negatives.keys().forEach { name ->
            val bytes = negatives.getString(name).toByteArray()
            val hash = repository.retain(request, bytes)
            assertTrue(name, runCatching { repository.verify(request.submissionId, "1", hash, key, key) }.isFailure)
            assertArrayEquals(bytes, work.db.temporaryArchives().response(request.submissionId, "1", hash)!!.responseBytes)
        }
        assertEquals(originalHash, work.db.temporaryArchives().evidence(request.submissionId, "1")!!.responseSha256)
        assertFalse(repository.read(request.submissionId, "1", key, key)!!.getBoolean("localDeletionAllowed"))
    }
}

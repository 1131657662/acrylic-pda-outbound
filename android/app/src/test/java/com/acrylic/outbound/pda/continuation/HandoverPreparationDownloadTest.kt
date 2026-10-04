package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.network.SessionChanged
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.submission.SubmissionRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverPreparationDownloadTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun downloadsSignedTargetMaterialAndImportsOriginalFactsFrozen() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            val order = f.import()

            assertEquals("HANDOVER_FROZEN", order.state)
            assertEquals("IMPORTED_FROZEN", f.local.db.handoverStages().get(f.local.caseId)?.state)
            val captures = JSONArray(f.local.db.captures().page(f.local.target, 0, 100).map(SubmissionRecords::capture))
            assertEquals(SubmissionJson.canonical(f.local.body.getJSONArray("captures")), SubmissionJson.canonical(captures))
            assertNotNull(f.local.db.preparations().order(f.local.target)?.ackAt)
            assertEquals(6, f.server.requestCount)
            val requests = List(6) { f.server.takeRequest() }
            assertEquals(listOf("GET", "POST", "GET", "GET", "GET", "POST"), requests.map { it.method })
            requests.forEach { assertEquals("Bearer test-access", it.getHeader("Authorization")) }
        }
    }

    @Test fun failedDownloadLeavesOnlyTheStageAndCanRetryAfterDatabaseReopen() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.failPart = "inventory"
            assertThrows(ApiFailure::class.java) { f.import() }
            f.assertNoImport()

            f.failPart = null
            f.local.reopen()
            val first = f.import()
            f.local.reopen()

            assertEquals(first, f.import())
            assertEquals(2, f.local.db.captures().page(f.local.target, 0, 100).size)
            assertEquals(1, f.ackBodies.size)
        }
    }

    @Test fun corruptedDownloadedPartCannotPublishPreparation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.corruptPart = "availability"

            assertThrows(IllegalStateException::class.java) { f.import() }

            f.assertNoImport()
        }
    }

    @Test fun tamperedManifestIsRejectedBeforeDownloadingParts() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.receipt.put("manifest", f.receipt.getString("manifest") + " ")

            assertThrows(IllegalStateException::class.java) { f.import() }

            f.assertNoImport()
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test fun loginChangeDuringDownloadPreventsAllLocalWrites() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.afterResponse = { if (it.contains("/content/plan?")) f.auth = JSONObject(f.auth.toString())
                .put("localSessionId", UUID.randomUUID().toString()) }

            assertThrows(SessionChanged::class.java) { f.import() }

            f.assertNoImport()
            assertEquals(3, f.server.requestCount)
        }
    }

    @Test fun identityChangeAfterDownloadsIsRejectedAtTheCommitBoundary() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            assertThrows(SessionChanged::class.java) {
                f.import { if (it == "资料已校验，正在保存原交接记录") f.auth = null }
            }

            f.assertNoImport()
            assertEquals(5, f.server.requestCount)
        }
    }

    @Test fun expiryDuringDownloadIsRecheckedBeforePersisting() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.afterResponse = { if (it.contains("/content/availability?"))
                f.clock = Instant.parse(f.local.input.assignment.getString("expiresAt")) }

            assertThrows(IllegalStateException::class.java) { f.import() }

            f.assertNoImport()
        }
    }

    @Test fun writeFailureAfterDownloadsRollsBackTheEntireImport() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_download_import BEFORE INSERT ON work_event
                BEGIN SELECT RAISE(ABORT, 'test import persistence failure'); END""")

            assertThrows(Exception::class.java) { f.import() }

            f.assertNoImport()
            f.local.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_download_import")
            assertEquals("HANDOVER_FROZEN", f.import().state)
        }
    }
}

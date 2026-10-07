package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.TimeSample
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryContinuationHttpTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun sameInputContinuationRecoversAckAndIntakeThenConsumesIndependentConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("TEMP_CONTINUATION_HTTP_PRIVATE")
        assumeTrue("Requires a new protected isolated MySQL/HTTP harness", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex(".*/acrylic-pda-outbound-isolated/retention-20261006-[1-9][0-9]{0,3}/private/android-input\\.json")))
        val input = JSONObject(file.readText()); val evidence = file.parentFile!!.parentFile!!
        check(input.getString("baseUrl").matches(Regex("http://127\\.0\\.0\\.1:[0-9]{1,5}/api/v1/")))
        TemporaryFixture(folder.root, contract = input.getJSONObject("vector")).use { f ->
            try { execute(f, input, evidence) }
            finally {
                f.db.close()
                f.context.getDatabasePath("outbound-pda.db").copyTo(File(evidence, "android-room.db"))
            }
        }
    }

    private suspend fun execute(f: TemporaryFixture, input: JSONObject, evidence: File) {
        val source = TemporaryContinuationHttpSource(f); source.import()
        val connection = TemporaryContinuationHttpConnection(f, input, evidence)
        val original = f.db.temporarySubmissions().forOrder(source.id)!!
        val next = TemporaryContinuationActivation(store(f, connection)).activate(source.id, input.getString("activationUtf8").toByteArray())
        checkpoint(f, evidence, "blocked")
        resume(f, connection, next)
        checkpoint(f, evidence, "ready")
        val frozen = scanAndFreeze(f, connection, next)
        upload(f, connection, frozen, evidence)
        assertEquals(original, f.db.temporarySubmissions().get(original.id))
        assertEquals(3, f.db.temporaryScans().allScans(source.id).size)
        assertNotNull(f.db.localE2aStops().get(source.id))
    }

    private fun checkpoint(f: TemporaryFixture, evidence: File, state: String) {
        f.db.close()
        f.context.getDatabasePath("outbound-pda.db").copyTo(File(evidence, "android-$state.db"))
        f.reopen()
    }

    private fun store(f: TemporaryFixture, connection: TemporaryConnection) = TemporaryWorkStore(f.db, connection, true) {
        TimeSample(1000, connection.now().toEpochMilli(), 1)
    }

    private fun resume(f: TemporaryFixture, connection: TemporaryContinuationHttpConnection, draft: TemporaryDraft) {
        val ticket = f.db.temporaryContinuations().forTarget(draft.clientOrderId)!!.ticketId
        val before = f.db.temporary().prepared(ticket)!!
        var coordinator = TemporaryContinuationCoordinator(store(f, connection)); coordinator.queue(ticket)
        assertThrows(IOException::class.java) { coordinator.resume(ticket) }
        assertNull(f.db.temporary().prepared(ticket)!!.ackJson)
        f.reopen(); coordinator = TemporaryContinuationCoordinator(store(f, connection))
        assertEquals("READY", coordinator.resume(ticket).state)
        assertEquals(connection.ackRequests[0], connection.ackRequests[1])
        assertEquals(before.ackKey, f.db.temporary().prepared(ticket)!!.ackKey)
        f.reopen()
        assertEquals("READY", TemporaryContinuationCoordinator(store(f, connection)).resume(ticket).state)
    }

    private fun scanAndFreeze(f: TemporaryFixture, connection: TemporaryContinuationHttpConnection, draft: TemporaryDraft): TemporarySubmission {
        val store = store(f, connection); store.load(draft.clientOrderId)
        assertEquals(3L, store.total(draft.clientOrderId).count)
        val processor = TemporaryProcessor(store) {}
        val capture = processor.reception.receive(TemporaryInput(draft.clientOrderId, "00100000005", "BROADCAST", store.sample()))
        processor.drain(draft.clientOrderId)
        assertEquals("COMMITTED", f.db.temporaryScans().capture(capture.id)!!.state)
        assertEquals(5L, capture.orderScanSeq)
        if (connection.chunked) f.db.runInTransaction {
            // Synthetic scale input through the product audit writer; all final bytes are frozen by the product.
            repeat(1500) { store.event(draft, null, "TEMP_CHUNK_TRANSPORT_TEST", "合成分块证据".repeat(60)) }
        }
        val frozen = TemporaryFreeze(store).freeze(draft.clientOrderId, "")
        assertEquals("4", JSONObject(frozen.payload).getString("expectedCount"))
        assertEquals("4000", JSONObject(frozen.payload).getString("expectedWeightGrams"))
        if (connection.chunked) assertTrue(frozen.payload.toByteArray(Charsets.UTF_8).size > 1024 * 1024)
        return frozen
    }

    private suspend fun upload(f: TemporaryFixture, connection: TemporaryContinuationHttpConnection, frozen: TemporarySubmission, evidence: File) {
        f.db.temporarySubmissions().queue(frozen.id)
        assertThrows(IOException::class.java) { TemporarySubmissionRepository(f.db, connection).upload(frozen.id) }
        f.reopen()
        if (connection.chunked) repeat(2) {
            assertThrows(IOException::class.java) { TemporarySubmissionRepository(f.db, connection).upload(frozen.id) }
            f.reopen()
        }
        assertEquals("REVIEW_REQUIRED", TemporarySubmissionRepository(f.db, connection).upload(frozen.id).state)
        if (connection.chunked) assertArrayEquals(frozen.payload.toByteArray(Charsets.UTF_8),
            connection.chunks.entries.sortedBy { it.key.substringAfterLast('/').toInt() }.flatMap { it.value.toList() }.toByteArray())
        val final = if (connection.higher) TemporaryHigherRoundHttpClient(f, connection, evidence).next(frozen) else frozen
        if (connection.higher) assertEquals("REVIEW_REQUIRED", TemporarySubmissionRepository(f.db, connection).upload(final.id).state)
        consume(f, connection, final, evidence)
    }

    private suspend fun consume(f: TemporaryFixture, connection: TemporaryContinuationHttpConnection, frozen: TemporarySubmission, evidence: File) {
        File(evidence, "android-uploaded.json").writeText(JSONObject().put("submissionId", frozen.id).put("payloadSha256", frozen.sha256).toString())
        val saved = withTimeout(60_000) {
            var row: TemporarySubmission
            do { delay(250); row = TemporarySubmissionRepository(f.db, connection).upload(frozen.id) } while (row.state != "SYNCED")
            row
        }
        f.reopen()
        assertEquals("SYNCED", TemporarySubmissionRepository(f.db, connection).checked(saved.id).state)
        val receipt = JSONObject(saved.receipt!!)
        assertEquals(if (connection.higher) "3" else "2", receipt.getString("workRound")); assertFalse(receipt.getBoolean("localDeletionAllowed"))
        assertNotNull(f.db.temporarySuccess().evidence(saved.id))
        File(evidence, "android-completed.json").writeText(JSONObject().put("submissionId", saved.id).put("status", saved.state)
            .put("localDeletionAllowed", false).put("actualCount", receipt.getString("actualCount")).toString())
    }
}

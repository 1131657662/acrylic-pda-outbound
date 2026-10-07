package com.acrylic.outbound.pda.temporary

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
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryMappedTargetTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun changedTargetRequiresFreshMappingForExactRootScopeAndRound() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val local = frozen(f); val reply = JSONObject().put("orderId", UUID.randomUUID().toString()).put("status", "REVIEW_REQUIRED")
            val row = mapping(f, local, reply)
            for ((key, wrong) in mapOf("scopeId" to UUID.randomUUID().toString(), "currentSubmissionId" to UUID.randomUUID().toString(),
                "workRound" to "1", "canonicalOrderId" to UUID.randomUUID().toString(), "mappingVersion" to "1",
                "mappingOrigin" to "INITIAL", "submissionStatus" to "ABORTED", "installRequired" to false)) {
                val connection = connection(f, JSONObject(row.toString()).put(key, wrong))
                assertThrows("must reject $key", Exception::class.java) {
                    TemporaryMappedTarget(f.db, connection).refresh(local, reply, checkNotNull(connection.current()))
                }
                assertEquals(local, f.db.temporarySubmissions().get(local.id))
                assertNull(f.db.temporarySuccess().evidence(local.id))
            }
            val connection = connection(f, row)
            assertEquals(reply.getString("orderId"), TemporaryMappedTarget(f.db, connection).refresh(local, reply, checkNotNull(connection.current())))
            assertEquals(reply.getString("orderId"), f.db.sourceBindings().get(row.getString("scopeId"))!!.canonicalOrderId)
            assertEquals(local, f.db.temporarySubmissions().get(local.id))
        }
    }

    @Test fun staleCacheAndSessionChangeCannotAuthorizeNewResultTarget() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val local = frozen(f); val reply = JSONObject().put("orderId", UUID.randomUUID().toString()).put("status", "REVIEW_REQUIRED")
            val row = mapping(f, local, reply); val good = connection(f, row)
            TemporaryMappedTarget(f.db, good).refresh(local, reply, checkNotNull(good.current()))
            val prior = f.db.sourceBindings().get(row.getString("scopeId"))!!
            f.db.sourceBindings().install(prior.copy(mappingVersion = "3"))
            assertThrows(Exception::class.java) { TemporaryMappedTarget(f.db, good).refresh(local, reply, checkNotNull(good.current())) }
            f.db.sourceBindings().install(prior)
            val absent = connection(f, null)
            assertThrows(Exception::class.java) { TemporaryMappedTarget(f.db, absent).refresh(local, reply, checkNotNull(absent.current())) }
            val wrong = object : TemporaryConnection by good {
                override fun call(request: TemporaryConnection.Call): JSONObject {
                    val page = good.call(request); f.work.fixture.auth!!.put("localSessionId", UUID.randomUUID().toString()); return page
                }
            }
            val expected = JSONObject(checkNotNull(wrong.current()).toString())
            assertThrows(Exception::class.java) { TemporaryMappedTarget(f.db, wrong).refresh(local, reply, expected) }
            assertEquals(local, f.db.temporarySubmissions().get(local.id))
        }
    }

    private fun frozen(f: TemporaryContinuationFixture): TemporarySubmission {
        val next = f.activate(); val ticket = f.receipt.getJSONObject("businessDecision").getString("ticketId")
        f.work.fixture.ack = f.downloadAck(f.db.temporary().request(ticket)!!)
        TemporaryContinuationDownload(f.work.store).acknowledge(ticket)
        val store = TemporaryWorkStore(f.db, f.work.fixture, true, f.work.store.sample)
        TemporaryContinuationWork(store).start(next.clientOrderId)
        val local = TemporaryFreeze(store).freeze(next.clientOrderId, "合成映射核对")
        f.db.temporarySubmissions().progress(local.id, "REVIEW_REQUIRED", JSONObject().put("orderId", UUID.randomUUID().toString()).toString(), "", false)
        return f.db.temporarySubmissions().get(local.id)!!
    }

    private fun mapping(f: TemporaryContinuationFixture, local: TemporarySubmission, reply: JSONObject) = JSONObject()
        .put("scopeId", f.receipt.getJSONObject("businessDecision").getString("scopeId")).put("clientOrderId", f.work.id)
        .put("currentSubmissionId", local.id).put("workRound", "2").put("submissionStatus", reply.getString("status"))
        .put("canonicalOrderId", reply.getString("orderId")).put("mappingVersion", "2").put("mappingId", UUID.randomUUID().toString())
        .put("mappingOrigin", "CONTROLLED_LINK").put("installRequired", true).put("phase", "FROZEN").put("orderNo", "FORMAL-SYNTHETIC")

    private fun connection(f: TemporaryContinuationFixture, row: JSONObject?) = object : TemporaryConnection by f.work.fixture {
        override fun call(request: TemporaryConnection.Call): JSONObject {
            check(request.path.startsWith("pda/temporary-source-bindings?"))
            return JSONObject().put("items", JSONArray().apply { if (row != null) put(row) }).put("nextCursor", JSONObject.NULL)
        }
    }
}

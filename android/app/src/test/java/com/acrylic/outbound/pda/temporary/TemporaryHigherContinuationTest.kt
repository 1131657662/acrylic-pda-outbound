package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHigherContinuationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun thirdRoundSurvivesRestartAndKeepsBothOriginalPrefixes() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val second = secondStopped(f); val secondBody = JSONObject(second.payload)
            val draft = checkNotNull(f.db.temporaryDrafts().get(second.clientOrderId))
            val third = TemporaryContinuationActivation(f.work.store).activate(draft.clientOrderId, f.nextRaw(draft, second))
            acknowledge(f, third)
            f.work.reopen()
            val store = TemporaryWorkStore(f.db, f.work.fixture, true, f.work.store.sample)
            TemporaryContinuationWork(store).start(third.clientOrderId)
            val processor = TemporaryProcessor(store) {}
            val capture = processor.reception.receive(TemporaryInput(third.clientOrderId, f.work.barcodes[1], "BROADCAST", store.sample()))
            processor.drain(third.clientOrderId)
            assertEquals(2L, capture.orderScanSeq)
            val body = JSONObject(TemporaryFreeze(store).freeze(third.clientOrderId, "第三轮合成验证").payload)
            assertEquals("3", body.getString("workRound")); assertEquals("2", body.getString("expectedCount"))
            for (field in listOf("captures", "scans", "events")) {
                val prefix = secondBody.getJSONArray(field); val actual = body.getJSONArray(field)
                for (i in 0 until prefix.length()) assertEquals(SubmissionJson.canonical(prefix.getJSONObject(i)),
                    SubmissionJson.canonical(actual.getJSONObject(i)))
            }
            assertEquals(second, f.db.temporarySubmissions().get(second.id))
            assertEquals(f.frozen, f.db.temporarySubmissions().get(f.frozen.id))
            assertEquals(3L, f.db.localE2aStops().get(third.clientOrderId)!!.workRound)
        }
    }

    @Test fun highRoundWithoutVerifiedEarlierOriginalCannotActivate() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val frozen = secondStopped(f); val draft = checkNotNull(f.db.temporaryDrafts().get(frozen.clientOrderId))
            val raw = f.nextRaw(draft, frozen)
            val source = f.work.store.source(draft.clientOrderId)
            val input = TemporaryContinuationProtocol.Input(draft, frozen, source.manifest, checkNotNull(f.work.fixture.auth), f.key)
            assertThrows(Exception::class.java) { TemporaryContinuationProtocol.verify(raw, input) }
            val broken = JSONObject(f.frozen.payload)
            broken.getJSONArray("captures").getJSONObject(0).put("rawInput", "changed original")
            assertThrows(Exception::class.java) { TemporaryContinuationProtocol.verify(raw, input.copy(predecessor = broken, rootClientOrderId = f.work.id)) }
        }
    }

    private fun secondStopped(f: TemporaryContinuationFixture): TemporarySubmission {
        val next = f.activate(); acknowledge(f, next)
        val store = TemporaryWorkStore(f.db, f.work.fixture, true, f.work.store.sample)
        TemporaryContinuationWork(store).start(next.clientOrderId)
        val frozen = TemporaryFreeze(store).freeze(next.clientOrderId, "合成停止输入").copy(state = "ABORTED")
        f.db.temporarySubmissions().progress(frozen.id, "ABORTED", null, "", false)
        f.work.store.load(next.clientOrderId)
        return frozen
    }

    @Test fun technicalStopRequiresEnabledDevelopmentAndVerifiedEvidence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = f.activate(); acknowledge(f, next)
            val store = TemporaryWorkStore(f.db, f.work.fixture, true, f.work.store.sample)
            TemporaryContinuationWork(store).start(next.clientOrderId)
            val frozen = TemporaryFreeze(store).freeze(next.clientOrderId, "合成停止输入")
            assertThrows(IllegalStateException::class.java) {
                TemporarySubmissionRepository(f.db, f.work.fixture, false).stopContinuation(frozen.id, "stop")
            }
            assertThrows(IllegalStateException::class.java) {
                TemporarySubmissionRepository(f.db, f.work.fixture, true).stopContinuation(frozen.id, "stop")
            }
            assertEquals(frozen, f.db.temporarySubmissions().get(frozen.id))
            assertEquals(f.frozen, f.db.temporarySubmissions().get(f.frozen.id))
        }
    }

    private fun acknowledge(f: TemporaryContinuationFixture, draft: TemporaryDraft) {
        val link = checkNotNull(f.db.temporaryContinuations().forTarget(draft.clientOrderId))
        f.work.fixture.ack = f.downloadAck(checkNotNull(f.db.temporary().request(link.ticketId)))
        TemporaryContinuationDownload(f.work.store).acknowledge(link.ticketId)
    }
}

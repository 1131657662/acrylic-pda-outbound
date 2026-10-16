package com.acrylic.outbound.pda.queue

import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.evidence.*
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Same native protocol for TEMP's own durable tables; never borrows FORMAL eligibility. */
class TemporaryForegroundDeviceTest {
    @Test fun retainedManualInputSurvivesPauseAndSeparateNativeProcess() {
        NativeQueueEvidence("temporary").use { f ->
            val connection = connection(f)
            val store = TemporaryWorkStore(f.db, connection) { TimeSample(1000, connection.now().toEpochMilli(), 1) }
            val sounds = CopyOnWriteArrayList<String>()
            val engine = TemporaryEngine(store) { type ->
                assertFalse(f.db.inTransaction())
                if (type == "SAVED") assertEquals(1L, store.total(f.id).count)
                sounds.add(type)
            }
            val delivered = AtomicInteger()
            val receiver = ScannerReceiver(f.context, { delivered.incrementAndGet(); engine.receive(it, "BROADCAST") }, engine::stop)
            try {
                engine.resume(f.id)
                f.await { engine.state.value.queued == 0 && engine.state.value.candidate?.id == f.captureId }
                assertEquals("WAITING_CONFIRM", engine.state.value.candidate?.state)
                assertEquals(0L, store.total(f.id).count)
                receiver.register()
                assertThrows(Exception::class.java) { UpdateGate.beginInstall() }
                assertThrows(Exception::class.java) { UpdateGate.enterBackground() }
                if (f.phase == "restore") save(f, engine, store, receiver, delivered, sounds)
                else pause(f, engine, store, receiver, delivered, sounds)
            } finally { receiver.unregister(); engine.close() }
        }
    }

    private fun pause(f: NativeQueueEvidence, engine: TemporaryEngine, store: TemporaryWorkStore,
                      receiver: ScannerReceiver, delivered: AtomicInteger, sounds: List<String>) {
        val original = f.db.temporaryScans().capture(f.captureId)
        receiver.unregister(); engine.pause(); f.await { engine.state.value.queued == 0 }
        UpdateGate.enterBackground().close()
        UpdateGate.beginInstall(); UpdateGate.endInstall()
        f.broadcast()
        assertEquals(0, delivered.get()); assertFalse(engine.state.value.ready)
        assertEquals(original, f.db.temporaryScans().capture(f.captureId))
        assertEquals(1, f.db.temporaryScans().captures(f.id).size)
        assertEquals(0L, store.total(f.id).count); assertTrue(sounds.isEmpty())
        f.result("completed", JSONObject().put("broadcastDelivered", delivered.get()).put("saved", 0)
            .put("pending", "WAITING_CONFIRM").put("installerAndBackgroundReleased", true))
    }

    private fun save(f: NativeQueueEvidence, engine: TemporaryEngine, store: TemporaryWorkStore,
                     receiver: ScannerReceiver, delivered: AtomicInteger, sounds: List<String>) {
        engine.confirm(f.captureId)
        f.await { engine.state.value.queued == 0 && store.total(f.id).count == 1L }
        assertEquals("COMMITTED", f.db.temporaryScans().capture(f.captureId)?.state)
        assertEquals(listOf("SAVED"), sounds.toList())
        f.broadcast()
        f.await { engine.state.value.queued == 0 && engine.state.value.candidate?.state == "INVALID" }
        assertEquals(1, delivered.get())
        val duplicate = checkNotNull(engine.state.value.candidate)
        assertEquals("该条码已在本机订单中保存，不能重复装车", duplicate.error)
        engine.acknowledge(duplicate.id); f.await { engine.state.value.queued == 0 && engine.state.value.candidate == null }
        assertEquals(1L, store.total(f.id).count)
        assertEquals(2, f.db.temporaryScans().captures(f.id).size)
        receiver.unregister(); engine.pause(); f.broadcast(); assertEquals(1, delivered.get())
        f.result("completed", JSONObject().put("broadcastDelivered", delivered.get()).put("saved", 1)
            .put("duplicateId", duplicate.id).put("callbacks", JSONArray(sounds)).put("originalIdCommitted", true))
    }

    private fun connection(f: NativeQueueEvidence): TemporaryConnection = object : TemporaryConnection {
        override fun current() = f.data.getJSONObject("session")
        override fun now() = Instant.parse(f.data.getString("now"))
        override fun call(request: TemporaryConnection.Call): JSONObject = error("Offline queue must not call HTTP")
        override fun original(request: TemporaryConnection.Original): ByteArray = error("Offline queue must not call HTTP")
        override fun download(request: TemporaryConnection.Download): Unit = error("Offline queue must not download")
        override fun stopProof(facts: E2aStopProof.Facts): E2aStopProof.Signed = error("No evidence signing")
        override fun localDraftStopKey(): String = error("No evidence signing")
        override fun signLocalDraftStop(stop: LocalDraftStop): LocalDraftStopProof.Signed = error("No evidence signing")
    }
}

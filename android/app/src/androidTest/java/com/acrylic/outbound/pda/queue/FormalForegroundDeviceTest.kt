package com.acrylic.outbound.pda.queue

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.update.UpdateGate
import com.acrylic.outbound.pda.work.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Native receiver/engine protocol; identity/time are synthetic, not product login or Activity lifecycle. */
class FormalForegroundDeviceTest {
    @Test fun retainedManualInputSurvivesPauseAndSeparateNativeProcess() {
        NativeQueueEvidence("formal").use { f ->
            val sounds = CopyOnWriteArrayList<String>()
            val engine = WorkEngine(WorkStore(f.db, access(f))) { type ->
                assertFalse(f.db.inTransaction())
                if (type == "SAVED") assertEquals(1L, f.db.scans().total(f.id).count)
                sounds.add(type)
            }
            val delivered = AtomicInteger()
            val receiver = ScannerReceiver(f.context, { delivered.incrementAndGet(); engine.receive(it, "BROADCAST") }, engine::stop)
            try {
                engine.resume(f.id)
                f.await { engine.state.value.queued == 0 && engine.state.value.candidate?.id == f.captureId }
                assertEquals("WAITING_CONFIRM", engine.state.value.candidate?.state)
                assertEquals(0L, f.db.scans().total(f.id).count)
                receiver.register()
                assertThrows(Exception::class.java) { UpdateGate.beginInstall() }
                assertThrows(Exception::class.java) { UpdateGate.enterBackground() }
                if (f.phase == "restore") save(f, engine, receiver, delivered, sounds)
                else pause(f, engine, receiver, delivered, sounds)
            } finally { receiver.unregister(); engine.close() }
        }
    }

    private fun pause(f: NativeQueueEvidence, engine: WorkEngine, receiver: ScannerReceiver,
                      delivered: AtomicInteger, sounds: List<String>) {
        val original = f.db.captures().get(f.captureId)
        receiver.unregister(); engine.pause(); f.await { engine.state.value.queued == 0 }
        UpdateGate.enterBackground().close()
        UpdateGate.beginInstall(); UpdateGate.endInstall()
        f.broadcast()
        assertEquals(0, delivered.get()); assertFalse(engine.state.value.ready)
        assertEquals(original, f.db.captures().get(f.captureId))
        assertEquals(1, f.db.captures().page(f.id, 0, 10).size)
        assertEquals(0L, f.db.scans().total(f.id).count); assertTrue(sounds.isEmpty())
        f.result("completed", JSONObject().put("broadcastDelivered", delivered.get()).put("saved", 0)
            .put("pending", "WAITING_CONFIRM").put("installerAndBackgroundReleased", true))
    }

    private fun save(f: NativeQueueEvidence, engine: WorkEngine, receiver: ScannerReceiver,
                     delivered: AtomicInteger, sounds: List<String>) {
        engine.confirm(f.captureId)
        f.await { engine.state.value.queued == 0 && f.db.scans().total(f.id).count == 1L }
        assertEquals("COMMITTED", f.db.captures().get(f.captureId)?.state)
        assertEquals(listOf("SAVED"), sounds.toList())
        f.broadcast()
        f.await { engine.state.value.queued == 0 && engine.state.value.candidate?.state == "INVALID" }
        assertEquals(1, delivered.get())
        val duplicate = checkNotNull(engine.state.value.candidate)
        assertEquals("该条码已在本机订单中保存，不能重复装车", duplicate.error)
        engine.acknowledge(duplicate.id); f.await { engine.state.value.queued == 0 && engine.state.value.candidate == null }
        assertEquals(1L, f.db.scans().total(f.id).count)
        assertEquals(2, f.db.captures().page(f.id, 0, 10).size)
        receiver.unregister(); engine.pause(); f.broadcast(); assertEquals(1, delivered.get())
        f.result("completed", JSONObject().put("broadcastDelivered", delivered.get()).put("saved", 1)
            .put("duplicateId", duplicate.id).put("callbacks", JSONArray(sounds)).put("originalIdCommitted", true))
    }

    private fun access(f: NativeQueueEvidence): WorkAccess = object : WorkAccess {
        private val session = f.data.getJSONObject("session")
        private val now = Instant.parse(f.data.getString("now"))
        override fun identity(order: PreparedOrder) {
            check(order.operatorId == session.getString("userId") && order.deviceId == session.getString("deviceId"))
        }
        override fun authorize(order: PreparedOrder): Instant {
            identity(order)
            JwtVerifier.authorization(JSONObject(order.assignmentJson), session, JSONObject(order.signingKeyJson), now)
            return now
        }
        override fun sample() = TimeSample(1000, now.toEpochMilli(), 1)
    }
}

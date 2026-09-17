package com.acrylic.m71demo.fixtures

import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.domain.*
import com.acrylic.m71demo.workflow.DemoStore
import javax.inject.Inject

/** Synthetic durability/size fixture. This is never a server receipt or measured scan run. */
class HistoryFixture @Inject constructor(private val store: DemoStore, private val signer: DemoSigner) {
    @Volatile var cancelled = false
    private var serial = 1L
    private var auditSeq = 1L
    private var origin = ""

    fun seed(total: Int = 360_000, progress: (Int) -> Unit) {
        require(total > 0 && total % 400 == 0)
        cancelled = false
        serial = 1; auditSeq = 1
        origin = "HISTORY-${java.util.UUID.randomUUID()}"
        repeat(total / 400) { batch ->
            if (cancelled) return
            seedOrder(batch, total / 400)
            progress((batch + 1) * 400)
        }
    }

    private fun seedOrder(batch: Int, batches: Int) = store.db.runInTransaction {
        val day = (batch * 90 / batches).coerceAtMost(89) + 1
        val at = store.clock.wallMs() - day * 86_400_000L
        val clock = object : AppClock {
            override fun wallMs() = at
            override fun elapsedNs() = 1_000_000_000L
            override fun bootId() = "$origin-$day"
        }
        val auth = AuthorizationService(clock, signer).issue(origin)
        val id = java.util.UUID.randomUUID().toString()
        store.db.audit().insertAuthorization(auth)
        store.db.inventory().insertSnapshot(InventorySnapshot(id, "SYNTHETIC", 400, "HISTORY_FIXTURE"))
        val order = DemoOrder(id, "历史合成-${batch + 1}", id, auth.id, source = "HISTORY_FIXTURE",
            state = "CONFIRMED_TEST_FIXTURE", nextOrderScanSeq = 401, createdAt = at)
        store.db.orders().insert(order)
        val device = store.device()
        check(device.nextFixtureSerial + 400 < 10_000_000) { "夹具序号空间不足" }
        store.db.audit().updateDevice(device.copy(nextFixtureSerial = device.nextFixtureSerial + 400))
        val bales = (0 until 400).map { historyBale(id, it, device.nextFixtureSerial) }
        store.db.inventory().insertBales(bales)
        store.db.inventory().insertStates(bales.map { CloudBaleState(it.barcode, state = "CLAIMED_TEST") })
        seedCaptures(order, auth, bales)
        store.db.orders().insertLines(bales.groupBy { it.deliveryCode }.map { (code, rows) ->
            OrderLine(id, code, rows.size, rows.size, rows.sumOf { it.weightGrams }) })
        store.db.orders().update(order.copy(actualCount = 400, weightGrams = bales.sumOf { it.weightGrams }))
    }

    private fun historyBale(snapshot: String, index: Int, start: Long): InventoryBale {
        val code = if (index % 2 == 0) "003" else "571"
        // H is separate from the benchmark X namespace; serials are transactionally reserved.
        val barcode = code + "H" + (start + index).toString().padStart(7, '0')
        return InventoryBale(snapshot, barcode, code,
            "SYNTHETIC HISTORY", "历史容量夹具（非真实扫码）", 300500, "300.5")
    }

    private fun seedCaptures(order: DemoOrder, auth: DemoAuthorization, bales: List<InventoryBale>) {
        val captures = mutableListOf<InputCapture>()
        val scans = mutableListOf<ScanRecord>()
        val guards = mutableListOf<BarcodeGuard>()
        val audit = mutableListOf<AuditRecord>()
        bales.forEachIndexed { index, bale ->
            val id = java.util.UUID.randomUUID().toString()
            val elapsed = 2_000_000_000L + index * 250_000_000L
            val c = CaptureContext(order.id, origin, index + 1L, serial++, bale.barcode, bale.barcode,
                "HISTORY_FIXTURE", order.createdAt + index * 250, elapsed, auth.bootSessionId,
                order.snapshotId, 1L, order.recoveryEpoch, auth.id)
            captures.add(InputCapture(id, c, state = "COMMITTED", confirmedAtNs = elapsed + 10_000_000,
                inputCommittedNs = elapsed + 1_000_000, confirmCommittedNs = elapsed + 20_000_000))
            audit.add(historyAudit(c, id, "RECEIVED", elapsed))
            audit.add(historyAudit(c, id, "CONFIRM_REQUESTED", elapsed + 10_000_000))
            val saved = historyAudit(c, id, "SAVED", elapsed + 20_000_000).also { audit.add(it) }
            scans.add(ScanRecord(id, c, bale.weightGrams, bale.rawWeight, bale.deliveryCode,
                saved.localSeq, c.receivedAt + 20, elapsed + 10_000_000))
            guards.add(BarcodeGuard(bale.barcode, order.id, id, 1L, "CONFIRMED_TEST_FIXTURE"))
        }
        store.db.captures().insertBatch(captures)
        store.db.scans().insertBatch(scans)
        store.db.scans().insertGuards(guards)
        store.db.audit().insertBatch(audit)
    }

    private fun historyAudit(c: CaptureContext, id: String, type: String, elapsed: Long): AuditRecord {
        val seq = auditSeq++
        return AuditRecord(java.util.UUID.randomUUID().toString(), origin, seq,
            c.orderId, id, type, "HISTORY_FIXTURE: synthesized, never measured or server-confirmed",
            c.receivedAt, elapsed, c.bootSessionId)
    }
}

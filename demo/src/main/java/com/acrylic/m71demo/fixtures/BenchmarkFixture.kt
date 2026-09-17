package com.acrylic.m71demo.fixtures

import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.device.Telemetry
import com.acrylic.m71demo.workflow.DemoStore
import org.json.JSONObject
import javax.inject.Inject

class BenchmarkFixture @Inject constructor(private val store: DemoStore, private val catalog: OrderCatalog,
    private val telemetry: Telemetry) {
    fun create(count: Int): BenchmarkRun {
        require(count in 1..400_000)
        val snapshot = "synthetic-${java.util.UUID.randomUUID()}"
        val start = reserveSerials(count)
        store.db.runInTransaction {
            store.db.inventory().insertSnapshot(InventorySnapshot(snapshot, "GENERATED", count, "SYNTHETIC_TEST"))
            (0 until count).chunked(200).forEach { batch ->
                val bales = batch.map { bale(snapshot, start + it, it) }
                store.db.inventory().insertBales(bales)
                store.db.inventory().insertStates(bales.map { CloudBaleState(it.barcode) })
            }
        }
        val plans = if (count == 1) "003:1" else "003:${(count + 1) / 2},571:${count / 2}"
        val id = catalog.create(OrderRequest("合成压测-${count}包", plans, snapshot, "SYNTHETIC_TEST"))
        val run = BenchmarkRun(id, snapshot, start, count)
        writeExpected(run)
        return run
    }

    private fun reserveSerials(count: Int): Long {
        var start = 0L
        store.db.runInTransaction {
            val device = store.device()
            start = device.nextFixtureSerial
            require(start + count < 10_000_000) { "测试条码序号空间不足，请归档" }
            store.db.audit().updateDevice(device.copy(nextFixtureSerial = start + count))
        }
        return start
    }

    private fun writeExpected(run: BenchmarkRun) {
        java.io.File(telemetry.directory, "expected-${run.orderId}.ndjson").bufferedWriter().use { writer ->
            repeat(run.count) { index ->
                val b = bale(run.snapshot, run.startSerial + index, index)
                writer.write(JSONObject().put("orderScanSeq", index + 1).put("barcode", b.barcode)
                    .put("weightGrams", b.weightGrams.toString()).put("deliveryCode", b.deliveryCode).toString())
                writer.newLine()
            }
        }
    }

    companion object {
        fun bale(snapshot: String, serial: Long, index: Int): InventoryBale {
            val code = if (index % 2 == 0) "003" else "571"
            val raw = if (index % 3 == 0) "300.5" else "301"
            return InventoryBale(snapshot, code + "X" + serial.toString().padStart(7, '0'), code,
                "DEMO ONLY", "合成测试包", com.acrylic.m71demo.domain.Weight.grams(raw), raw)
        }
    }
}

data class BenchmarkRun(val orderId: String, val snapshot: String, val startSerial: Long, val count: Int)

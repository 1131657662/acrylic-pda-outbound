package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.work.WorkFixture
import java.io.File
import java.nio.charset.Charset
import java.time.Instant

/** Synthetic rows for read-only selection; these are not evidence of a signed export. */
internal class RescueOrdersFixture(root: File) : AutoCloseable {
    val work = WorkFixture(root)
    val db get() = work.db
    private val temporary = TemporaryWorkFixture(root, db)
    private val template = checkNotNull(db.temporaryDrafts().get(temporary.id))
    val identity = RescueReadIdentity(work.session.getString("deviceId"), work.session.getString("userId"),
        work.session.getString("warehouseId"))
    val grant = RescueReadGrant(identity) {}
    init { db.temporaryDrafts().update(template.copy(operatorId = "synthetic-template-only")) }

    fun draft(id: String, at: String, vehicle: String = "合成车$id") {
        db.temporaryDrafts().insert(template.copy(clientOrderId = id, creatorDeviceId = identity.deviceId,
            operatorId = identity.operatorId, warehouseId = identity.warehouseId, createdAt = at,
            vehicleNo = vehicle, temporaryNo = "合成单$id"))
    }
    fun independent(id: String, at: Instant) {
        val sample = TimeSample(1000, at.toEpochMilli(), 1)
        val bytes = ("MODEL".padEnd(20) + "NAME".padEnd(14) + "003A0000001".padEnd(13) + "15.501".padEnd(10))
            .toByteArray(Charset.forName("GB18030"))
        val imported = IndependentSkuRepository(db) { sample }.stage(bytes, "synthetic-sku.txt").imported
        db.independentTasks().insertTask(IndependentTask(id, imported.id, imported.sha256, "synthetic", "synthetic",
            "{}", "{}", "synthetic", identity.operatorId, identity.deviceId, identity.warehouseId, "synthetic",
            "合成独立车", "选择器排序测试", 1, "synthetic", sample.wall, sample.elapsed, sample.boot))
        db.independentTasks().insertState(IndependentTaskState(id))
    }
    override fun close() { temporary.close(); work.close() }
}

package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.security.TimeSample
import java.io.File
import java.util.UUID

class TemporaryWorkFixture(root: File, borrowed: PdaDatabase? = null, contract: org.json.JSONObject? = null) : AutoCloseable {
    val fixture = TemporaryFixture(root, borrowed, contract)
    val db get() = fixture.db
    var store = newStore()
    val drafts get() = TemporaryDraftRepository(store)
    val feedback = mutableListOf<String>()
    val processor get() = TemporaryProcessor(store) { feedback.add(it) }
    val id = UUID.randomUUID().toString()
    init { fixture.repository.prepare {}; drafts.create(id) }
    fun newStore() = TemporaryWorkStore(db, fixture) { TimeSample(1000, fixture.time.toEpochMilli(), 1) }
    val catalog get() = store.source(id)
    val barcodes get() = catalog.bales.keys.toList()
    fun plan(count: String = "2") = TemporaryPlanInput(checkNotNull(db.temporaryDrafts().get(id)).revision,
        "合成车001", "合成柜001", "现场无后台订单", catalog.products.keys.map { it to count })
    fun start(count: String = "2") { val row = drafts.save(id, plan(count)); drafts.start(id, row.revision) }
    fun receive(barcode: String = barcodes[0], source: String = "BROADCAST") =
        processor.reception.receive(TemporaryInput(id, barcode, source, store.sample()))
    fun scan(barcode: String = barcodes[0]) = receive(barcode).also { processor.drain(id) }
    fun reopen() { fixture.reopen(); store = newStore(); store.load(id) }
    fun cloud(barcode: String = barcodes[0], status: String = "AVAILABLE", version: Long = 2, cycle: Long = 1) {
        val source = catalog; val bale = source.bales.getValue(barcode); val request = source.request
        db.sync().saveState(LocalSyncState(request.recoveryEpoch, request.warehouseId, "BARCODE", activeBaselineId = "temp-test", cursor = "cursor"))
        db.barcodes().save(LocalCloudBarcode(request.recoveryEpoch, request.warehouseId, "temp-test", barcode, bale.baleId,
            status, cycle, version, fixture.time.toString()))
    }
    override fun close() = fixture.close()
}

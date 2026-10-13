package com.acrylic.outbound.pda.independent

import java.util.UUID

internal class IndependentTaskFixture(barcode: String = "003A0000001") : AutoCloseable {
    val sku = IndependentSkuFixture()
    val identity = IndependentCredentialFixture().also { it.provision() }
    val active = sku.activate(barcode)
    var actor = IndependentTaskActor(identity.operator, identity.device, identity.warehouse)
    val repository get() = IndependentTaskRepository(sku.db, { identity.sample }) { actor }
    fun command() = IndependentTaskCommand(UUID.randomUUID().toString(), active.imported.id, "沪A12345", "服务中断独立采集", mapOf("003" to 2L))
    fun grant(): IndependentCaptureGrant {
        val open = identity.store.unlock(identity.password, identity.context())
        return IndependentCaptureGrant(identity.store.validate(open, identity.context())) { identity.store.snapshot(open, identity.context()) }
    }
    override fun close() = sku.close()
}

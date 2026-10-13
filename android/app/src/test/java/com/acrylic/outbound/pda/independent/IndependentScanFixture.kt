package com.acrylic.outbound.pda.independent

internal class IndependentScanFixture(barcode: String = "003A0000001") : AutoCloseable {
    val task = IndependentTaskFixture(barcode)
    val command = task.command()
    val grant = task.grant()
    val created = task.repository.create(command, grant)
    val environment = IndependentScanEnvironment({ task.identity.sample }, { task.actor })
    var store = IndependentScanStore(task.sku.db, grant, environment).also { it.open(command.id) }
    val reception get() = IndependentScanReception(store)
    val saving get() = IndependentScanSaving(store)
    val corrections get() = IndependentScanCorrections(store)
    fun start() = saving.start(command.id)
    private val barcode = checkNotNull(task.active.preview).rows.first().parsedFields!!.barcode
    fun input(raw: String = barcode, source: String = "BROADCAST") = IndependentInput(command.id, raw, source, task.identity.sample)
    fun save(input: IndependentInput = input()): IndependentCaptureView {
        val row = reception.receive(input); reception.validate(row.capture.id)
        if (input.source == "MANUAL") saving.confirm(row.capture.id)
        check(saving.save(row.capture.id) == "SAVED")
        return checkNotNull(store.dao.capture(row.capture.id))
    }
    fun reopen() {
        task.sku.reopen(); store = IndependentScanStore(task.sku.db, grant, environment).also { it.open(command.id) }
    }
    override fun close() = task.close()
}

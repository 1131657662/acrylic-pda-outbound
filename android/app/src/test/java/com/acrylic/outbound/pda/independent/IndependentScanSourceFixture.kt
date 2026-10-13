package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.preparation.*
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.work.*
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Synthetic signed contract fixtures in one real Room database; not a device/production identity. */
internal class IndependentScanSourceFixture(root: File) : AutoCloseable {
    val input = ContractFixture.input(root)
    val barcode = org.json.JSONArray(String(input.payloads.getValue("inventory"), Charsets.UTF_8)).getJSONObject(0).getString("barcode")
    val independent = IndependentScanFixture(barcode)
    val db get() = independent.task.sku.db
    val assignment = input.assignment.getString("id")
    private val access = object : WorkAccess {
        override fun identity(order: PreparedOrder) {
            check(order.operatorId == input.session.getString("userId") && order.deviceId == input.session.getString("deviceId"))
        }
        override fun authorize(order: PreparedOrder): Instant {
            identity(order); JwtVerifier.authorization(JSONObject(order.assignmentJson), input.session, JSONObject(order.signingKeyJson), input.now)
            return input.now
        }
        override fun sample() = TimeSample(1000, input.now.toEpochMilli(), 1)
    }
    val formal get() = WorkStore(db, access)
    val processor get() = WorkProcessor(formal) { _, _ -> }
    init { LocalActivation(db).activate(input); formal.open(assignment); independent.start() }
    fun formalInput(barcode: String = this.barcode, source: String = "BROADCAST") =
        processor.reception.receive(WorkInput(assignment, barcode, source, access.sample()))
    override fun close() = independent.close()
}

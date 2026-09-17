package com.acrylic.outbound.pda.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.preparation.*
import com.acrylic.outbound.pda.security.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

class WorkFixture(directory: File) : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    var db: PdaDatabase
    val input = ContractFixture.input(directory)
    val assignment = input.assignment.getString("id")
    val barcodes = JSONArray(String(input.payloads.getValue("inventory"), Charsets.UTF_8)).let { a ->
        (0 until a.length()).map { a.getJSONObject(it).getString("barcode") } }
    var now = input.now
    var session = JSONObject(input.session.toString())
    val access = object : WorkAccess {
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
    val sounds = ArrayList<String>()
    val store get() = WorkStore(db, access)
    val processor get() = WorkProcessor(store) { type, _ -> sounds.add(type) }
    init {
        context.deleteDatabase("outbound-pda.db"); db = PdaDatabase.open(context)
        LocalActivation(db).activate(input); store.open(assignment)
    }
    fun receive(raw: String = barcodes[0], source: String = "BROADCAST"): WorkCapture =
        processor.reception.receive(WorkInput(assignment, raw, source, access.sample()))
    fun scan(raw: String = barcodes[0]): WorkCapture = receive(raw).also { processor.drain(assignment) }
    fun reload() { db.close(); db = PdaDatabase.open(context); store.open(assignment) }
    override fun close() { db.close(); context.deleteDatabase("outbound-pda.db") }
}

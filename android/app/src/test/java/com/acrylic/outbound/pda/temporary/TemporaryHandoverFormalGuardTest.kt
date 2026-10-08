package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.preparation.ContractFixture
import com.acrylic.outbound.pda.preparation.LocalActivation
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.work.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHandoverFormalGuardTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun formalScannerRejectsRetainedTemporaryHandoverBarcode() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            f.repository.install(f.id, f.original)
            val input = ContractFixture.input(folder.newFolder("formal"))
            LocalActivation(f.db).activate(input)
            val store = WorkStore(f.db, access(input))
            val id = input.assignment.getString("id"); store.open(id)
            val guard = f.db.temporaryHandoverGuards().forCase(f.id).single()
            val inventory = org.json.JSONArray(input.payloads.getValue("inventory").toString(Charsets.UTF_8))
            val bale = f.db.preparations().bale(id, inventory.getJSONObject(0).getString("barcode"))!!
            f.db.preparations().insertBales(listOf(bale.copy(barcode = guard.barcode, baleId = java.util.UUID.randomUUID().toString())))
            assertNotNull(f.db.preparations().bale(id, guard.barcode))
            val processor = WorkProcessor(store) { _, _ -> }
            val capture = processor.reception.receive(WorkInput(id, guard.barcode, "BROADCAST", store.access.sample()))
            processor.drain(id)
            assertEquals("INVALID", f.db.captures().get(capture.id)!!.state)
            assertTrue(f.db.captures().get(capture.id)!!.error.contains("不能重复装车"))
            assertEquals(0L, f.db.scans().total(id).count)
            assertEquals(guard, f.db.temporaryHandoverGuards().get(guard.barcode))
        }
    }

    private fun access(input: com.acrylic.outbound.pda.preparation.VerifiedPackage) = object : WorkAccess {
        override fun identity(order: PreparedOrder) {
            check(order.operatorId == input.session.getString("userId") && order.deviceId == input.session.getString("deviceId"))
        }
        override fun authorize(order: PreparedOrder) = input.now.also {
            identity(order); JwtVerifier.authorization(JSONObject(order.assignmentJson), input.session, input.key, it)
        }
        override fun sample() = TimeSample(1000, input.now.toEpochMilli(), 1)
    }
}

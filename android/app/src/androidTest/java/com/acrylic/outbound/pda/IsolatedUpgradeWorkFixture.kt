package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.preparation.*
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.submission.SubmissionFreeze
import com.acrylic.outbound.pda.work.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.time.Instant
import java.util.Base64

/** Signed integration-test data only; never claims or uploads an actual server order. */
internal class IsolatedUpgradeWorkFixture(private val app: PdaApplication) {
    fun seed(): JSONObject {
        IsolatedUpgradeFacts(app).requireEmulator()
        val input = input()
        assertNotEquals(app.session.settings.device, input.session.getString("deviceId"))
        val id = input.assignment.getString("id")
        assertNull(app.database.preparations().order(id))
        LocalActivation(app.database).activate(input)
        val store = WorkStore(app.database, access(input)); store.open(id)
        val processor = WorkProcessor(store) { _, _ -> }
        val barcode = JSONArray(String(input.payloads.getValue("inventory"))).getJSONObject(0).getString("barcode")
        val invalid = scan(processor, WorkInput(id, "invalid-upgrade-fixture", "BROADCAST", access(input).sample()))
        assertEquals("INVALID", app.database.captures().get(invalid.id)!!.state)
        processor.corrections.acknowledge(invalid.id)
        val valid = WorkInput(id, barcode, "BROADCAST", access(input).sample())
        val first = scan(processor, valid); processor.corrections.voidLast(id, first.id, "隔离升级测试作废")
        scan(processor, WorkInput(id, barcode, "BROADCAST", access(input).sample()))
        val frozen = SubmissionFreeze(store) { input.now }.freeze(id, "隔离升级测试未上传批次")
        assertEquals(1L, app.database.work().order(id)!!.actualCount)
        assertEquals(3, app.database.captures().page(id, 0, 10).size)
        assertEquals(2, app.database.scans().page(id, 0, 10).size)
        assertNull(app.updates.installBlockReason())
        return JSONObject().put("assignmentId", id).put("submissionId", frozen.id)
            .put("frozenSha256", frozen.sha256).put("captureCount", 3).put("scanCount", 2)
            .put("syntheticContractFixture", true).put("uploaded", false)
    }

    fun finishInterruptedSeed(): JSONObject {
        IsolatedUpgradeFacts(app).requireEmulator()
        val input = input(); val id = input.assignment.getString("id")
        val store = WorkStore(app.database, access(input)); val processor = WorkProcessor(store) { _, _ -> }
        assertEquals(2, app.database.captures().page(id, 0, 10).size)
        assertEquals("VOID", app.database.scans().page(id, 0, 10).single().status)
        assertEquals(0L, app.database.work().order(id)!!.actualCount)
        assertNull(app.database.submissions().forOrder(id))
        val barcode = JSONArray(String(input.payloads.getValue("inventory"))).getJSONObject(0).getString("barcode")
        scan(processor, WorkInput(id, barcode, "BROADCAST", access(input).sample()))
        val frozen = SubmissionFreeze(store) { input.now }.freeze(id, "隔离升级测试未上传批次")
        assertEquals(1L, app.database.work().order(id)!!.actualCount)
        assertEquals(3, app.database.captures().page(id, 0, 10).size)
        assertEquals(2, app.database.scans().page(id, 0, 10).size)
        assertNull(app.updates.installBlockReason())
        return JSONObject().put("assignmentId", id).put("submissionId", frozen.id)
            .put("frozenSha256", frozen.sha256).put("captureCount", 3).put("scanCount", 2)
            .put("syntheticContractFixture", true).put("uploaded", false).put("resumedTestFixture", true)
    }

    private fun scan(processor: WorkProcessor, input: WorkInput): WorkCapture =
        processor.reception.receive(input).also { processor.drain(input.assignmentId) }

    private fun input(): VerifiedPackage {
        val file = File(app.filesDir, "upgrade-contract-fixture.json")
        val fixture = JSONObject(file.readText()); check(file.delete())
        val parts = JSONObject(fixture.getJSONObject("receipt").getString("manifest")).getJSONArray("parts")
        val directory = File(app.cacheDir, "upgrade-fixture").apply { mkdirs() }
        val payloads = (0 until parts.length()).associate { index ->
            val part = parts.getJSONObject(index); val name = part.getString("name")
            require(name in setOf("inventory", "availability", "plan"))
            val compressed = File(directory, "$name.gz")
            compressed.writeBytes(Base64.getDecoder().decode(fixture.getJSONObject("gzip").getString(name)))
            val bytes = PackageVerifier.unpack(compressed, part); check(compressed.delete())
            name to bytes
        }
        return VerifiedPackage(fixture.getJSONObject("assignment"), fixture.getJSONObject("receipt"), fixture.getJSONObject("signingKey"),
            fixture.getJSONObject("session"), payloads, Instant.parse(fixture.getJSONObject("assignment").getString("issuedAt")).plusSeconds(1))
    }

    private fun access(input: VerifiedPackage) = object : WorkAccess {
        override fun identity(order: PreparedOrder) {
            check(order.deviceId == input.session.getString("deviceId") && order.operatorId == input.session.getString("userId"))
        }
        override fun authorize(order: PreparedOrder): Instant {
            identity(order); JwtVerifier.authorization(JSONObject(order.assignmentJson), input.session, input.key, input.now)
            return input.now
        }
        override fun sample() = TimeSample(1000, input.now.toEpochMilli(), 1)
    }
}

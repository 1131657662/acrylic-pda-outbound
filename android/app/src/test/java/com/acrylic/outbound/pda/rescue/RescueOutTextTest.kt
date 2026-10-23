package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.Charset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RescueOutTextTest {
    @get:Rule val folder = TemporaryFolder()
    private val gbk = Charset.forName("GBK")

    @Test
    fun formalSnapshotExportsFiveColumnsInScanOrderWithoutHeader() {
        val snapshot = snapshot(
            order = order("FORMAL"),
            scans = listOf(
                scan("1", "VOID", "2026-10-11T01:01:03Z", "9.000"),
                scan("2", "ACTIVE", "2026-10-11T01:02:03Z", "12.340"),
                scan("3", "ACTIVE", "2026-10-11T01:03:03Z", "0.500")
            )
        )

        val text = RescueOutText.encode(snapshot).toString(gbk)

        assertEquals(
            "型号\t003A0000002\t12.340\t张三\t2026/10/11 9:02\r\n" +
                "型号\t003A0000003\t0.500\t张三\t2026/10/11 9:03\r\n",
            text
        )
        assertTrue(!text.startsWith("型号\t条码"))
        assertArrayEquals(text.toByteArray(gbk), RescueOutText.encode(snapshot))
    }

    @Test
    fun disposedActiveScanIsExcludedAlongsideVoidScan() {
        val active = scan("1", "ACTIVE", "2026-10-11T01:02:03Z", "12.340")
        val snapshot = snapshot(
            order = order("FORMAL"),
            scans = listOf(active, scan("2", "VOID", "2026-10-11T01:03:03Z", "9.000")),
            dispositions = listOf(JSONObject().put("captureId", "capture-1")
                .put("evidenceId", "evidence-1").put("action", "QUARANTINE"))
        )

        assertEquals("", RescueOutText.encode(snapshot).toString(gbk))
    }

    @Test
    fun missingTrustedOperatorIsRejected() {
        val snapshot = snapshot(order = order("FORMAL", operatorName = null), scans = listOf(scan("1")))

        val error = runCatching { RescueOutText.encode(snapshot) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("操作员"))
    }

    @Test
    fun temporaryOrderIsRejectedEvenWithOperatorName() {
        val snapshot = snapshot(order = order("TEMP"), scans = listOf(scan("1")))

        assertTrue(runCatching { RescueOutText.encode(snapshot) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun tabNewlineAndUnencodableFieldsAreRejected() {
        val tab = scan("1").put("model", "型号\t异常")
        val newline = scan("1").put("model", "型号\n异常")
        val unencodable = scan("1").put("model", "型号🙂")

        assertTrue(runCatching { RescueOutText.encode(snapshot(scans = listOf(tab))) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { RescueOutText.encode(snapshot(scans = listOf(newline))) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { RescueOutText.encode(snapshot(scans = listOf(unencodable))) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun invalidOriginalScanTimeIsRejected() {
        val original = snapshot(scans = listOf(scan("1", savedAt = "not-a-time")))

        val error = runCatching { RescueOutText.encode(original) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("扫码时间"))
    }

    @Test
    fun savedTextMatchesOriginalBytesAndReturnedDigest() {
        val original = snapshot()
        val target = folder.newFile("out.txt")
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val grant = RescueReadGrant(RescueReadIdentity("device-1", "operator-1", "warehouse-1")) {}

        val digest = RescueOutTextFiles(context).copy(original, android.net.Uri.fromFile(target), grant)

        assertArrayEquals(RescueOutText.encode(original), target.readBytes())
        assertEquals(RescueBundle.sha(target.readBytes()), digest)
    }

    @Test
    fun differentOperatorCannotWriteDestination() {
        val target = folder.newFile("out.txt")
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val grant = RescueReadGrant(RescueReadIdentity("device-1", "other-operator", "warehouse-1")) {}

        val error = runCatching { RescueOutTextFiles(context).copy(snapshot(), android.net.Uri.fromFile(target), grant) }
            .exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(0L, target.length())
    }

    private fun snapshot(
        order: JSONObject = order("FORMAL"),
        scans: List<JSONObject> = listOf(scan("1")),
        dispositions: List<JSONObject> = emptyList()
    ): RescueSnapshot {
        val captures = scans.map { scan ->
            JSONObject().put("id", scan.getString("captureId"))
                .put("orderScanSeq", scan.getString("orderScanSeq"))
                .put("state", "COMMITTED")
        }
        return RescueSnapshot(
            order = order,
            captures = captures,
            scans = scans,
            events = emptyList(),
            lines = listOf(JSONObject().put("deliveryCode", "003").put("plannedCount", "3")),
            dispositions = dispositions
        )
    }

    private fun order(source: String, operatorName: String? = "张三") = JSONObject()
        .put("source", source).put("orderId", "order-1").put("localOrderId", "local-order-1")
        .put("deviceId", "device-1").put("operatorId", "operator-1")
        .put("warehouseId", "warehouse-1").put("state", "SCANNING")
        .put("timeTrust", "UNCONFIRMED_EXPORT_TIME").apply {
            if (operatorName != null) put("operatorName", operatorName)
        }

    private fun scan(
        sequence: String,
        status: String = "ACTIVE",
        savedAt: String = "2026-10-11T01:02:03Z",
        rawWeight: String = "12.340"
    ) = JSONObject().put("captureId", "capture-$sequence").put("orderScanSeq", sequence)
        .put("status", status).put("deliveryCode", "003").put("model", "型号")
        .put("barcode", "003A000000$sequence")
        .put("rawWeight", rawWeight).put("weightGrams", "12340").put("savedAt", savedAt)
}

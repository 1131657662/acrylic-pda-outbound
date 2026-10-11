package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RescueFormatTest {
    private val password get() = "synthetic-rescue-password-only".toCharArray()
    private fun snapshot(): RescueSnapshot {
        val order = JSONObject().put("source", "TEMP").put("orderId", "synthetic-order")
            .put("localOrderId", "synthetic-local").put("deviceId", "synthetic-device")
            .put("operatorId", "synthetic-operator").put("warehouseId", "synthetic-warehouse")
            .put("state", "SCANNING").put("vehicleNo", "合成车").put("timeTrust", "UNCONFIRMED_EXPORT_TIME")
        val captures = (1..4).map { n -> JSONObject().put("id", "capture-$n")
            .put("orderScanSeq", (9007199254740992L + n).toString())
            .put("state", when (n) { 3 -> "WAITING_CONFIRM"; 4 -> "INVALID"; else -> "COMMITTED" }) }
        val scans = (1..2).map { n -> JSONObject().put("captureId", "capture-$n")
            .put("orderScanSeq", (9007199254740992L + n).toString()).put("barcode", "003A000000$n")
            .put("deliveryCode", "003").put("model", "合成型号").put("name", "=危险\t文本\n仍保留")
            .put("rawWeight", "123.456").put("weightGrams", "123456").put("status", if (n == 1) "ACTIVE" else "VOID")
            .put("savedAt", "2026-10-11T01:02:03Z") }
        val events = (1..3).map { n -> JSONObject().put("id", "event-$n").put("orderPosition", n.toString()).put("type", "SYNTHETIC") }
        return RescueSnapshot(order, captures, scans, events, listOf(JSONObject().put("deliveryCode", "003").put("plannedCount", "2")))
    }
    private fun signer(): RescueSigner {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return RescueSigner(pair.public.encoded) { text ->
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(pair.private); signature.update(text.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(signature.sign())
        }
    }
    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val rows = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) { rows[entry.name] = zip.readBytes(); entry = zip.nextEntry }
        }
        return rows
    }
    @Test fun encryptedVectorPreservesLongSequencesVoidPendingAndHumanProjection() {
        val signer = signer(); val bytes = RescueBundle.build(snapshot(), signer)
        val encrypted = RescueEnvelope.encrypt(bytes, password)
        assertArrayEquals(bytes, RescueEnvelope.decrypt(encrypted, password))
        val files = entries(bytes); val manifest = JSONObject(files.getValue("manifest.json").toString(Charsets.UTF_8))
        assertEquals("1", manifest.getJSONObject("summary").getString("active"))
        assertEquals("1", manifest.getJSONObject("summary").getString("void"))
        assertEquals("1", manifest.getJSONObject("summary").getString("pending"))
        assertEquals("1", manifest.getJSONObject("summary").getString("failed"))
        assertEquals("123456", manifest.getJSONObject("summary").getString("weightGrams"))
        assertTrue(files.getValue("scan-details.tsv").toString(Charsets.UTF_8).contains("9007199254740993\tcapture-1"))
        assertTrue(files.getValue("scan-details.tsv").toString(Charsets.UTF_8).contains("'=危险\\t文本\\n仍保留"))
        val directory = File("build/rescue-format-vector").apply { mkdirs() }
        File(directory, "synthetic.pdarescue").writeBytes(encrypted)
        File(directory, "synthetic-trust.json").writeText(JSONObject().put("synthetic-device", JSONObject()
            .put("publicKeyDerBase64", Base64.getEncoder().encodeToString(signer.publicKey))
            .put("operatorIds", org.json.JSONArray(listOf("synthetic-operator")))
            .put("warehouseIds", org.json.JSONArray(listOf("synthetic-warehouse")))).toString())
    }
    @Test fun wrongPasswordAndHeaderOrCiphertextChangesNeverDecrypt() {
        val bytes = RescueEnvelope.encrypt(RescueBundle.build(snapshot(), signer()), password)
        assertThrows(Exception::class.java) { RescueEnvelope.decrypt(bytes, "wrong-password-but-long".toCharArray()) }
        listOf(12, bytes.lastIndex).forEach { index ->
            val changed = bytes.copyOf(); changed[index] = (changed[index].toInt() xor 1).toByte()
            assertThrows(Exception::class.java) { RescueEnvelope.decrypt(changed, password) }
        }
    }
    @Test fun duplicateOrReorderedFactsCannotBecomeValidPackage() {
        val snapshot = snapshot()
        assertThrows(IllegalArgumentException::class.java) { RescueBundle.build(snapshot.copy(scans = snapshot.scans.reversed()), signer()) }
        assertThrows(IllegalArgumentException::class.java) { RescueBundle.build(snapshot.copy(captures = snapshot.captures + snapshot.captures.first()), signer()) }
    }
    @Test fun arbitraryFilesAndShortPasswordsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { RescueBundle.build(snapshot().copy(originals = mapOf("../secrets" to byteArrayOf(1))), signer()) }
        assertThrows(IllegalArgumentException::class.java) { RescueEnvelope.encrypt(byteArrayOf(1), "short".toCharArray()) }
        assertEquals("'@danger\\r\\ntext\\t\\u0001", RescueText.cell("@danger\r\ntext\t\u0001"))
    }
    @Test fun frozenOriginalBodyIsCopiedByteForByteAndNeverInventedForOpenOrder() {
        val payload = "{ \"submissionId\" : \"synthetic-batch\" }\n".toByteArray(Charsets.UTF_8)
        val info = JSONObject().put("id", "synthetic-batch").put("sha256", RescueBundle.sha(payload)).toString().toByteArray()
        val frozen = snapshot().copy(originals = mapOf("submission.json" to payload, "submission-info.json" to info,
            "receipt.json" to "{\"syntheticReceipt\":true}".toByteArray()))
        assertArrayEquals(payload, entries(RescueBundle.build(frozen, signer())).getValue("submission.json"))
        assertFalse(entries(RescueBundle.build(snapshot(), signer())).containsKey("submission.json"))
        assertThrows(IllegalArgumentException::class.java) {
            RescueBundle.build(frozen.copy(originals = frozen.originals + ("submission.json" to payload + 32.toByte())), signer())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RescueBundle.build(snapshot().copy(originals = mapOf("receipt.json" to byteArrayOf(1))), signer())
        }
        val directory = File("build/rescue-format-vector").apply { mkdirs() }
        File(directory, "synthetic-frozen.zip").writeBytes(RescueBundle.build(frozen, signer()))
    }
    @Test fun zeroValidAndEmptyFactsRemainExportableWithoutFreezing() {
        val sample = snapshot()
        val empty = sample.copy(captures = emptyList(), scans = emptyList(), events = emptyList(), lines = emptyList())
        assertEquals("0", RescueFacts.summary(empty).getString("active"))
        assertTrue(entries(RescueBundle.build(empty, signer())).getValue("captures.ndjson").isEmpty())
        assertFalse(entries(RescueBundle.build(empty, signer())).containsKey("submission.json"))
        val void = sample.copy(scans = sample.scans.map { JSONObject(it.toString()).put("status", "VOID") })
        assertEquals("0", RescueFacts.summary(void).getString("weightGrams"))
        val directory = File("build/rescue-format-vector").apply { mkdirs() }
        File(directory, "synthetic-empty.zip").writeBytes(RescueBundle.build(empty, signer()))
    }
    @Test fun missingScopeOrUnknownPlanCannotBeSigned() {
        val sample = snapshot()
        val missing = sample.copy(order = JSONObject(sample.order.toString()).put("operatorId", ""))
        assertThrows(IllegalArgumentException::class.java) { RescueBundle.build(missing, signer()) }
        assertThrows(IllegalArgumentException::class.java) { RescueBundle.build(sample.copy(lines = emptyList()), signer()) }
        val negative = sample.copy(scans = sample.scans.map { JSONObject(it.toString()).put("weightGrams", "-1") })
        assertThrows(IllegalArgumentException::class.java) { RescueBundle.build(negative, signer()) }
    }
    @Test fun dispositionPreservesOriginalActiveScanAndExcludesItsContribution() {
        val proof = JSONObject().put("captureId", "capture-1").put("evidenceId", "synthetic-evidence").put("action", "QUARANTINE")
        val sample = snapshot().copy(dispositions = listOf(proof))
        assertEquals("0", RescueFacts.summary(sample).getString("active"))
        assertEquals("1", RescueFacts.summary(sample).getString("disposed"))
        assertEquals("1", RescueFacts.summary(sample).getString("void"))
        assertEquals("0", RescueFacts.summary(sample).getString("weightGrams"))
        assertEquals("ACTIVE", sample.scans.first().getString("status"))
        val directory = File("build/rescue-format-vector").apply { mkdirs() }
        File(directory, "synthetic-disposed.zip").writeBytes(RescueBundle.build(sample, signer()))
    }
}

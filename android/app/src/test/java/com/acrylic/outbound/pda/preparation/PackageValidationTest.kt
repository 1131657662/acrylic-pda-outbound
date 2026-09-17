package com.acrylic.outbound.pda.preparation

import com.acrylic.outbound.pda.security.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class PackageValidationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun truncatedAndCorruptedGzipAreRejected() {
        val fixture = ContractFixture.read(); val manifest = JSONObject(fixture.getJSONObject("receipt").getString("manifest"))
        val part = manifest.getJSONArray("parts").getJSONObject(1)
        val bytes = Base64.getDecoder().decode(fixture.getJSONObject("gzip").getString("inventory"))
        val file = File(folder.root, "inventory.gz").apply { writeBytes(bytes.copyOf(bytes.size - 1)) }
        assertThrows(Exception::class.java) { PackageVerifier.unpack(file, part) }
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertThrows(Exception::class.java) { PackageVerifier.unpack(file, part) }
    }

    @Test fun alteredManifestCannotUseOriginalSignature() {
        val input = ContractFixture.input(folder.root)
        val receipt = JSONObject(input.receipt.toString())
        val manifest = JSONObject(receipt.getString("manifest")).put("revision", 999).toString()
        receipt.put("manifest", manifest).put("manifestSha256", JwtVerifier.sha(manifest.toByteArray()))
        assertThrows(Exception::class.java) { PackageVerifier.manifest(receipt, input.assignment, input.key, input.now) }
    }

    @Test fun jwtWithModifiedPayloadFailsSignature() {
        val fixture = ContractFixture.read(); val token = fixture.getJSONObject("assignment").getString("offlineToken").split('.')
        val changed = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".toByteArray())
        assertThrows(Exception::class.java) { JwtVerifier.verify("${token[0]}.$changed.${token[2]}", fixture.getJSONObject("signingKey"), "acrylic-pda-offline") }
    }

    @Test fun duplicateBarcodeIsRejectedBeforeActivation() {
        val data = ContractFixture.input(folder.root).payloads
        val inventory = JSONArray(String(data.getValue("inventory"))); val states = JSONArray(String(data.getValue("availability")))
        inventory.put(1, inventory.getJSONObject(0)); states.put(1, states.getJSONObject(0))
        assertThrows(Exception::class.java) { InventoryDecoder.decode("assignment", inventory, states) }
    }

    @Test fun mismatchedWeightAndDispatchCycleAreRejected() {
        val data = ContractFixture.input(folder.root).payloads
        val inventory = JSONArray(String(data.getValue("inventory"))); val states = JSONArray(String(data.getValue("availability")))
        inventory.getJSONObject(0).put("weightGrams", "1")
        assertThrows(Exception::class.java) { InventoryDecoder.decode("assignment", inventory, states) }
        states.getJSONObject(0).put("dispatchCycle", "0")
        assertThrows(Exception::class.java) { InventoryDecoder.decode("assignment", JSONArray(String(data.getValue("inventory"))), states) }
    }

    @Test fun monotonicClockAcceptsElapsedTimeButRejectsRebootAndWallClockChange() {
        val start = TimeSample(1000, 10000, 3)
        val anchor = TrustedTime.anchor("2026-09-17T00:00:00Z", start)
        assertEquals("2026-09-17T00:00:01Z", TrustedTime.now(anchor, TimeSample(2000, 11000, 3)).toString())
        assertThrows(Exception::class.java) { TrustedTime.now(anchor, TimeSample(2000, 11000, 4)) }
        assertThrows(Exception::class.java) { TrustedTime.now(anchor, TimeSample(2000, 500000, 3)) }
    }
}

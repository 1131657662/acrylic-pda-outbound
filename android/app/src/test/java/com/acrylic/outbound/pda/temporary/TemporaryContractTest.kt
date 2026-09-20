package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryContractTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun serverSignedQualificationPayloadsAndActivationReceiptVerifyTogether() = io {
        TemporaryFixture(folder.root).use { f ->
            val manifest = TemporaryVerifier.manifest(f.receipt, f.key)
            TemporaryVerifier.identity(manifest, f.auth!!); TemporaryPayloads.verify(manifest, f.payloads)
            assertEquals(172800L, Instant.parse(manifest.getString("expiresAt")).epochSecond - Instant.parse(manifest.getString("issuedAt")).epochSecond)
            val row = f.repository.prepare {}
            assertEquals(2, row.inventoryRows); assertNotNull(row.ackJson)
            val claims = JwtVerifier.verify(f.ack.getString("signedAck"), f.key, "acrylic-pda-temporary-activation")
            assertEquals(claims.getString("commandSha256"), TemporaryAckVerifier.commandDigest(JSONObject(row.ackBodyJson)))
            f.reopen(); assertEquals(row, f.repository.prepare {}); assertEquals(5, f.downloads.size)
        }
    }
    @Test fun tamperedManifestAndSubstitutedTokenPurposeAreRejected() = io {
        TemporaryFixture(folder.root).use { f ->
            val changed = JSONObject(f.receipt.toString()).put("manifest", f.receipt.getString("manifest") + " ")
            assertThrows(Exception::class.java) { TemporaryVerifier.manifest(changed, f.key) }
            val wrong = JSONObject(f.receipt.toString()).put("offlineToken", f.receipt.getString("signedManifest"))
            assertThrows(Exception::class.java) { TemporaryVerifier.manifest(wrong, f.key) }
        }
    }
    @Test fun wrongIdentityVersionsMissingAndCorruptPayloadsNeverActivate() = io {
        TemporaryFixture(folder.root).use { f ->
            val row = f.saveRequest(); val expected = f.auth!!
            for (field in listOf("userId", "deviceId", "warehouseId", "userAuthVersion", "deviceVersion", "serverRecoveryEpoch")) {
                val other = JSONObject(expected.toString()).put(field, "other")
                assertThrows(Exception::class.java) { TemporaryVerifier.identity(f.manifest, other) }
            }
            for (parts in listOf(f.payloads - "rules", f.payloads + ("inventory" to "[]".toByteArray()))) {
                assertThrows(Exception::class.java) { TemporaryActivation(f.db, f).activate(TemporaryActivation.Input(row, expected, parts)) }
            }
            assertNull(f.repository.prepared(row)); assertEquals(row, f.db.temporary().request(row.key))
        }
    }
    @Test fun eligibilityStopsExactlyAtExpiryWithoutDeletingOriginalData() = io {
        TemporaryFixture(folder.root).use { f ->
            val request = f.saveRequest(); val expiry = Instant.parse(f.manifest.getString("expiresAt"))
            f.time = expiry; assertThrows(Exception::class.java) { f.activate(request) }; assertNull(f.repository.prepared(request))
            f.time = expiry.minusMillis(1); val row = f.activate(request)
            f.time = expiry; assertTrue(f.repository.readiness(request).contains("到期")); assertEquals(row, f.repository.prepared(request))
        }
    }
    @Test fun semanticMismatchIsRejectedEvenWhenUnsignedDescriptorHashIsAdjusted() = io {
        TemporaryFixture(folder.root).use { f ->
            val availability = JSONObject(String(f.payloads.getValue("availability"), Charsets.UTF_8))
            availability.put("warehouseId", java.util.UUID.randomUUID().toString())
            val bytes = availability.toString().toByteArray(); val manifest = JSONObject(f.manifest.toString())
            TemporaryVerifier.descriptors(manifest).first { it.getString("name") == "availability" }
                .put("bytes", bytes.size).put("sha256", JwtVerifier.sha(bytes))
            assertThrows(Exception::class.java) { TemporaryPayloads.verify(manifest, f.payloads + ("availability" to bytes)) }
        }
    }
    private fun io(block: () -> Unit) = runBlocking<Unit>(Dispatchers.IO) { block() }
}

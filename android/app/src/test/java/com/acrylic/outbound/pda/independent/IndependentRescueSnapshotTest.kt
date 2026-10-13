package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.rescue.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentRescueSnapshotTest {
    private fun scenario(block: (IndependentRescueFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { IndependentRescueFixture().use(block) }
    @Test fun voidRescanAndUnconfirmedInputExportWithOriginalSkuAndNoBusinessWrite() = scenario { f ->
        f.scan.start(); val first = f.scan.save()
        f.scan.corrections.voidSelected(f.scan.command.id, first.capture.id, "实物已卸下")
        f.scan.save(); val pending = f.scan.reception.receive(f.scan.input(source = "MANUAL"))
        val before = f.digest(); val snapshot = f.read()
        assertEquals(before, f.digest()); assertEquals(3, snapshot.captures.size)
        assertEquals(pending.capture.rawInput, snapshot.captures.last().getString("rawInput"))
        assertEquals(listOf("VOID", "ACTIVE"), snapshot.scans.map { it.getString("status") })
        assertArrayEquals(f.scan.task.sku.bytes(), snapshot.originals.getValue("independent-sku.bin"))
        val facts = RescueFacts.summary(snapshot)
        assertEquals("1", facts.getString("active")); assertEquals("1", facts.getString("pending")); assertEquals("15501", facts.getString("weightGrams"))
        assertFalse(snapshot.originals.containsKey("submission.json")); assertNull(f.db.work().device(f.scan.task.actor.deviceId))
        f.vector(snapshot)
    }
    @Test fun expiryRebootLogoutAndNewCatalogDoNotPreventOwnReadOnlyRecovery() = scenario { f ->
        f.scan.start(); f.scan.save(); val original = f.scan.created.task
        f.scan.task.sku.activate("004A0000001")
        f.scan.task.identity.advance(172801000); f.scan.task.identity.sample = f.scan.task.identity.sample.copy(boot = 9)
        f.scan.task.identity.session = JSONObject(); f.scan.task.sku.reopen()
        val before = f.digest(); val snapshot = f.read()
        assertEquals(before, f.digest()); assertEquals(original.skuSha256, JSONObject(snapshot.originals.getValue("independent-source.json").toString(Charsets.UTF_8))
            .getJSONObject("task").getString("skuSha256"))
        assertEquals("INDEPENDENT", snapshot.order.getString("source")); assertFalse(snapshot.order.getBoolean("serverAuthorizationVerified"))
    }
    @Test fun readGrantScopeAndRevocationControlPickerAndSnapshot() = scenario { f ->
        assertEquals(f.target, RescueOrders(f.db).page(f.grant).rows.single().target)
        val bad = listOf(RescueReadGrant(f.grant.identity.copy(operatorId = "foreign")) {},
            RescueReadGrant(f.grant.identity.copy(warehouseId = "foreign")) {},
            RescueReadGrant(f.grant.identity, targets = setOf(RescueTarget("TEMP", f.target.localOrderId))) {},
            RescueReadGrant(f.grant.identity) { error("synthetic revocation") })
        for (grant in bad) assertThrows(IllegalStateException::class.java) { RescueRoomSnapshot(f.db).read(f.target, grant) }
        assertTrue(RescueOrders(f.db).page(bad.first()).rows.isEmpty())
    }
    @Test fun exportedSourceNeverCarriesOriginalOperationCredentialOrLoginMaterial() = scenario { f ->
        val source = JSONObject(f.read().originals.getValue("independent-source.json").toString(Charsets.UTF_8))
        val token = f.scan.created.task.signedCredential
        assertEquals(RescueBundle.sha(token.toByteArray()), source.getJSONObject("credential").getString("credentialSha256"))
        for (secret in listOf(token, "signedCredential", "accessToken", "refreshToken", "password", "localSessionId")) assertFalse(source.toString().contains(secret))
        assertEquals(f.scan.created.task.credentialId, source.getJSONObject("credential").getString("credentialId"))
    }
    @Test fun corruptStateTotalsCannotProduceEffectivePackage() = scenario { f ->
        f.scan.start(); f.scan.save()
        val state = checkNotNull(f.db.independentTasks().state(f.scan.command.id))
        f.db.independentTasks().updateState(state.copy(weightGrams = 1))
        assertThrows(IllegalStateException::class.java) { f.read() }
    }
    @Test fun changedOriginalScanFieldsAreRejectedBeforeSigning() = scenario { f ->
        f.scan.start(); f.scan.save(); val snapshot = f.read()
        val changed = snapshot.scans.map { JSONObject(it.toString()).put("model", "changed") }
        assertThrows(IllegalStateException::class.java) { RescueBundle.build(snapshot.copy(scans = changed), f.signer) }
    }
    @Test fun realMovingClockKeepsDistinctVoidSampleAndAuthorizationReadable() = scenario { f ->
        f.scan.start(); val saved = f.scan.save()
        val environment = IndependentScanEnvironment({ f.scan.task.identity.advance(1); f.scan.task.identity.sample }, { f.scan.task.actor })
        val store = IndependentScanStore(f.db, f.scan.grant, environment).also { it.open(f.scan.command.id) }
        IndependentScanCorrections(store).voidSelected(f.scan.command.id, saved.capture.id, "实物已卸下")
        val original = checkNotNull(f.db.independentScans().voided(saved.capture.id))
        val sampled = com.acrylic.outbound.pda.security.TrustedTime.now(JSONObject(f.scan.created.task.timeAnchorJson),
            com.acrylic.outbound.pda.security.TimeSample(original.elapsed, original.wall, original.boot))
        assertTrue(java.time.Instant.parse(original.authorizedAt) > sampled)
        assertEquals("VOID", f.read().scans.single().getString("status"))
    }
    @Test fun backwardOrExcessiveVoidAuthorizationDelayCannotBeSigned() = scenario { f ->
        f.scan.start(); val saved = f.scan.save()
        f.scan.corrections.voidSelected(f.scan.command.id, saved.capture.id, "实物已卸下")
        val snapshot = f.read(); val original = JSONObject(snapshot.originals.getValue("independent-voids.ndjson").toString(Charsets.UTF_8))
        for (delay in listOf(-1L, 120001L)) {
            val changed = JSONObject(original.toString()).put("authorizedAt", java.time.Instant.parse(original.getString("authorizedAt")).plusMillis(delay).toString())
            val files = snapshot.originals + ("independent-voids.ndjson" to changed.toString().toByteArray(Charsets.UTF_8))
            assertThrows(IllegalStateException::class.java) { RescueBundle.build(snapshot.copy(originals = files), f.signer) }
        }
    }
    @Test fun independentCannotCarryFrozenSubmissionOrLoseSkuOriginal() = scenario { f ->
        val snapshot = f.read()
        for (files in listOf(snapshot.originals - "independent-sku.bin", snapshot.originals + ("submission.json" to "{}".toByteArray())))
            assertThrows(IllegalArgumentException::class.java) { RescueBundle.build(snapshot.copy(originals = files), f.signer) }
    }
    @Test fun anotherDeviceSigningKeyCannotSealOriginalCredentialScope() = scenario { f ->
        val wrong = RescueSigner(byteArrayOf(1, 2, 3)) { error("must reject before signing") }
        assertThrows(IllegalStateException::class.java) { RescueBundle.build(f.read(), wrong) }
    }
    @Test fun damagedAuditScopeOrTaskStateCannotBeSigned() = scenario { f ->
        f.scan.start(); f.scan.save(); val snapshot = f.read()
        val event = JSONObject(snapshot.events.first().toString()).put("taskId", "different-task")
        val badEvent = snapshot.copy(events = listOf(event) + snapshot.events.drop(1))
        val badState = snapshot.copy(order = JSONObject(snapshot.order.toString()).put("state", "SUCCESS"))
        for (bad in listOf(badEvent, badState))
            assertThrows(IllegalStateException::class.java) { RescueBundle.build(bad, f.signer) }
    }
    @Test fun privateEncryptedIndependentPackageCanBeRecoveredWithoutChangingTask() = scenario { f ->
        f.scan.start(); f.scan.save(); val before = f.digest()
        val files = RescueExportFiles(f.scan.task.sku.context)
        val generated = files.generate(f.read(), f.grant to f.signer, "synthetic-package-only-passphrase".toCharArray())
        val recovered = RescueExportFiles(f.scan.task.sku.context).recover(generated.id, f.grant)
        assertEquals(generated.sha256, recovered.sha256); assertEquals(before, f.digest())
        assertTrue(recovered.summary.contains("待主管对账")); assertTrue(recovered.summary.contains("服务端授权未核验"))
        assertEquals("SCANNING", f.db.independentTasks().state(f.scan.command.id)?.state)
    }
}

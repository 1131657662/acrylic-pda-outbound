package com.acrylic.outbound.pda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.update.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit phases; only opt-in isolated emulator, never a connected physical PDA. */
@RunWith(AndroidJUnit4::class)
class IsolatedUpgradeDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val facts get() = IsolatedUpgradeFacts(app).also { it.requireEmulator() }

    @Test fun pairAuthenticVersion15() {
        assertEquals(15L, facts.version)
        assertEquals("", app.session.settings.device); assertNull(app.session.saved())
        val file = File(app.filesDir, "upgrade-login-fixture.json")
        val fixture = JSONObject(file.readText()); check(file.delete())
        val originalKey = app.session.publicKey()
        val started = app.pairing.start("https://outbound-test.nbsmzwl.cn/api/v1/").getJSONObject("result")
        PairingAdmin(fixture).approve(started.getString("shortCode"))
        assertEquals("PENDING", app.pairing.resume().getJSONObject("result").getString("status"))
        assertEquals("COMPLETED", app.pairing.confirm().getJSONObject("result").getString("status"))
        assertEquals(originalKey, app.session.publicKey())
        app.session.login(fixture.getString("operator"), fixture.getString("operatorPassword"))
        assertEquals(app.session.settings.device, app.session.call("pda/devices/me").getString("id"))
        check(facts.directory.mkdirs())
        File(facts.directory, "paired.json").writeText(JSONObject().put("versionCode", facts.version)
            .put("roomVersion", app.database.openHelper.readableDatabase.version).put("signedLogin", true).toString())
    }

    @Test fun captureMeaningfulVersion15Baseline() {
        assertEquals(15L, facts.version)
        val baseline = File(facts.directory, "before.json"); check(!baseline.exists())
        val seeded = IsolatedUpgradeWorkFixture(app).seed()
        baseline.writeText(facts.snapshot().put("fixture", seeded).toString())
    }

    @Test fun originalClientCompatibilityIsMeasured() {
        assertEquals(15L, facts.version)
        val signed = SignedUpdateManifest.parse(File(app.filesDir, "upgrade-validation-manifest.json").readText())
        val checker = UpdateCompatibility(app.packageName, "staging", 15, android.os.Build.VERSION.SDK_INT)
        checker.check(signed.payload)
        val results = JSONObject()
        for (version in listOf("0.17.0", "0.18.0", "0.19.0", "0.20.0")) {
            results.put(version, runCatching { checker.check(signed.payload.copy(serverCompatibility = version)) }.isSuccess)
        }
        File(facts.directory, "original-client-compatibility.json").writeText(results.toString())
    }

    @Test fun resumeInterruptedFixtureWithoutClearingData() {
        assertEquals(15L, facts.version)
        val baseline = File(facts.directory, "before.json"); check(!baseline.exists())
        val seeded = IsolatedUpgradeWorkFixture(app).finishInterruptedSeed()
        baseline.writeText(facts.snapshot().put("fixture", seeded).toString())
    }

    @Test fun externalInstallerCancellationPreservesVersion15Baseline() {
        assertEquals(15L, facts.version)
        verifyOriginalFacts(11)
        File(facts.directory, "external-cancel-verified.json").writeText(JSONObject().put("versionCode", facts.version)
            .put("identityAndAllOriginalRowsPreserved", true).put("appUpdateFlowTested", false).toString())
    }

    @Test fun cancellationPreservesVersion15Baseline() {
        assertEquals(15L, facts.version)
        verifyOriginalFacts(11)
        assertEquals("READY_TO_INSTALL", app.updates.savedState()!!.state)
        File(facts.directory, "cancel-verified.json").writeText(JSONObject().put("versionCode", facts.version)
            .put("identityAndAllOriginalRowsPreserved", true).put("retryReady", true).toString())
    }

    @Test fun installedVersion16PreservesEveryOriginalFact() {
        assertEquals(16L, facts.version)
        verifyOriginalFacts(14)
        val before = JSONObject(File(facts.directory, "before.json").readText())
        val original = before.getJSONObject("fixture")
        val submission = app.database.submissions().get(original.getString("submissionId"))!!
        assertEquals(original.getString("frozenSha256"), submission.sha256)
        assertEquals(submission.sha256, com.acrylic.outbound.pda.security.JwtVerifier.sha(submission.payload.toByteArray()))
        val info = app.packageManager.getInstallSourceInfo(app.packageName)
        assertEquals("com.google.android.packageinstaller", info.installingPackageName)
        File(facts.directory, "after.json").writeText(facts.snapshot().put("originalFactsPreserved", true)
            .put("installer", info.installingPackageName).put("originatingPackage", info.originatingPackageName).toString())
    }

    @Test fun preservedSessionCanSynchronizeOnVersion16() {
        assertEquals(16L, facts.version)
        val original = facts.tables()
        assertEquals(app.session.settings.device, app.session.call("pda/devices/me").getString("id"))
        val result = JSONObject().put("orders", app.sync.refreshOrders()).put("barcodes", app.barcodeSync.refreshBarcodes())
        assertTrue(result.getString("orders").contains("已同步"))
        assertTrue(result.getString("barcodes").contains("已同步"))
        for (name in original.keys().asSequence().toList()) {
            if (name.startsWith("local_cloud_") || name in setOf("local_sync_state", "local_recovery_control")) original.remove(name)
        }
        facts.assertOriginalRows(original, facts.tables())
        File(facts.directory, "sync-after.json").writeText(result.put("originalSessionUsable", true)
            .put("businessRowsPreserved", true).put("syntheticFactsUploaded", false).toString())
    }

    private fun verifyOriginalFacts(room: Int) {
        val before = JSONObject(File(facts.directory, "before.json").readText()); val actual = facts.snapshot()
        assertEquals(room, actual.getInt("roomVersion"))
        assertEquals(before.getLong("firstInstallTime"), actual.getLong("firstInstallTime"))
        assertEquals(before.getJSONObject("identity").toString(), actual.getJSONObject("identity").toString())
        facts.assertOriginalRows(before.getJSONObject("tables"), actual.getJSONObject("tables"))
    }
}

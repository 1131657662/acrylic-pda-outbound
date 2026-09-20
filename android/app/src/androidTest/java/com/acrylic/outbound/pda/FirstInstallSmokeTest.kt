package com.acrylic.outbound.pda

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.update.UpdateCheckResult
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in smoke test on an empty emulator only. Never run against an existing PDA identity. */
@RunWith(AndroidJUnit4::class)
class FirstInstallSmokeTest {
    @Test fun emptyInstallPairsLogsInAndRefreshesAgainstStaging() {
        check(Build.MODEL.startsWith("sdk_gphone")) { "This test is restricted to the isolated emulator" }
        check(BuildConfig.PDA_UPDATE_CHANNEL == "staging")
        check(BuildConfig.PDA_SERVER_URL == "https://outbound-test.nbsmzwl.cn/api/v1/")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
        assertEquals("", app.session.settings.device); assertNull(app.session.saved())
        val file = File(app.filesDir, "first-install-fixture.json")
        val fixture = JSONObject(file.readText()); check(file.delete())
        val paired = pair(app, fixture)
        app.session.login(fixture.getString("operator"), fixture.getString("operatorPassword"))
        assertEquals(paired, app.session.call("pda/devices/me").getString("id"))
        val evidence = refresh(app).put("deviceId", paired).put("shortCodePaired", true).put("signedDeviceLogin", true)
        evidence.put("installedVersionCode", BuildConfig.VERSION_CODE).put("installedVersionName", BuildConfig.VERSION_NAME)
            .put("roomVersion", app.database.openHelper.readableDatabase.version).put("androidApi", Build.VERSION.SDK_INT)
            .put("installMethod", "This smoke test performs no installation; consult package-manager source evidence")
        File(app.filesDir, "first-install-evidence.json").writeText(evidence.toString())
    }

    private fun pair(app: PdaApplication, fixture: JSONObject): String {
        val originalKey = app.session.publicKey()
        val started = app.pairing.start(BuildConfig.PDA_SERVER_URL).getJSONObject("result")
        assertEquals("WAITING", started.getString("status"))
        PairingAdmin(fixture).approve(started.getString("shortCode"))
        assertEquals("PENDING", app.pairing.resume().getJSONObject("result").getString("status"))
        val completed = app.pairing.confirm().getJSONObject("result")
        assertEquals("COMPLETED", completed.getString("status"))
        assertEquals(originalKey, app.session.publicKey())
        assertEquals(completed.getString("deviceId"), app.session.settings.device)
        return app.session.settings.device
    }

    private fun refresh(app: PdaApplication): JSONObject {
        val result = JSONObject().put("orderSync", app.sync.refreshOrders()).put("barcodeSync", app.barcodeSync.refreshBarcodes())
        assertTrue(app.auditArchives.restore())
        result.put("auditUpload", app.auditSync.upload()).put("auditArchive", app.auditArchives.archive())
            .put("submissionArchive", app.archives.refresh())
        assertTrue(app.repository.page("pda/assignments", "beforeId").items.isEmpty())
        app.repository.page("pda/orders", "cursor")
        app.updates.verifyInstalledData()
        val update = app.updates.check()
        assertTrue(update is UpdateCheckResult.Current)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), (update as UpdateCheckResult.Current).currentVersionCode)
        return result.put("assignmentListEmpty", true).put("availableOrdersReadable", true)
            .put("signedPublicUpdateCheck", true).put("integrityCheck", true)
    }
}

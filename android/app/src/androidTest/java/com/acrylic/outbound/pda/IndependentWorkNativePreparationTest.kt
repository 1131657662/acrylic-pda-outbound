package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

/** Preparation for new UI consumers, without imported keys, sessions, time or business rows. */
class IndependentWorkNativePreparationTest {
    @Test fun freshNativeIdentityObtainsCredentialAndStagesSyntheticCatalog() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("independentWorkNative") == "true")
        check(F.app.session.settings.device.isBlank() && F.app.session.saved() == null)
        check(IndependentCredentialVault(F.app).read() == null)
        val directory = File(F.app.filesDir, "independent-work-native").also { check(it.isDirectory) }
        check(!File(directory, "preparation.json").exists())
        val input = JSONObject(File(directory, "input.json").readText())
        check(input.getString("purpose") == "SYNTHETIC_NATIVE_WORK_UI")
        check(input.getString("baseUrl") == "http://127.0.0.1:19498/api/v1/")
        registerAndLogin(input)
        assertTrue(F.credentials.provision(F.password.toCharArray()).contains("已保存签名独立身份"))
        assertTrue(F.credentials.verifyLocally(F.password.toCharArray()).contains("独立身份与可信期限已核验"))
        val catalog = IndependentSkuRepository(F.app.database) { TrustedTime.sample(F.app) }
        check(catalog.active() == null && F.app.database.independentTasks().count() == 0L)
        val staged = catalog.stage(syntheticCatalog(), "synthetic-work-ui.txt")
        val active = catalog.activate(staged.imported.id, false, null)
        assertEquals(4, active.preview!!.validRows)
        File(directory, "signed-receipt.json").writeText(F.active().getString("receipt"))
        File(directory, "preparation.json").writeText(JSONObject().put("nativeKeystore", true)
            .put("realHttpCredential", true).put("syntheticCatalog", true).put("sessionInjected", false)
            .put("timeInjected", false).put("catalogSha256", active.imported.sha256)
            .put("bootCount", TrustedTime.sample(F.app).boot).toString(2))
    }

    private fun registerAndLogin(input: JSONObject) {
        val publicKey = F.app.session.publicKey()
        val web = FaultRestartWebClient(input.getString("baseUrl"), input)
        web.login()
        val device = web.post("devices", JSONObject().put("deviceCode", "work-ui-${UUID.randomUUID().toString().take(20)}")
            .put("name", "合成独立采集原生界面验证").put("publicKey", publicKey))
        F.app.session.settings.bind(input.getString("baseUrl"), device.getString("deviceId"), publicKey)
        F.app.session.login(input.getString("username"), input.getString("password"))
        androidx.work.WorkManager.getInstance(F.app).cancelAllWork().result.get()
    }

    private fun syntheticCatalog(): ByteArray = (1..4).joinToString("\r\n") {
        "SYNTHETIC-UI-MODEL".padEnd(20) + "UI-COMBINATION".padEnd(14) + "003A000000$it".padEnd(13) + "15.501".padEnd(10)
    }.toByteArray(Charset.forName("GB18030"))
}

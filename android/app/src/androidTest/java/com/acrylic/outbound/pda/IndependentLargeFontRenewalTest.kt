package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.IndependentCredentialVault
import com.acrylic.outbound.pda.independent.IndependentSkuRepository
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

/** Renew a retained emulator identity through HTTP; never seed a session, token or clock. */
class IndependentLargeFontRenewalTest {
    @Test fun retainedIdentityObtainsFreshBootCredentialThroughRealHttp() {
        F.guard()
        check(InstrumentationRegistry.getArguments().getString("independentLargeFont") == "true")
        val directory = File(F.app.filesDir, "independent-large-font").also { check(!it.exists()); check(it.mkdirs()) }
        val input = JSONObject(File(F.app.filesDir, "independent-large-font-input.json").readText())
        val key = F.app.session.publicKey()
        val device = F.app.session.settings.device.also { check(it.isNotBlank()) }
        val before = checkNotNull(IndependentCredentialVault(F.app).read()).getJSONObject("entries")
        check(F.app.session.settings.server == "http://127.0.0.1:19498/api/v1/")
        check(input.getString("baseUrl") == F.app.session.settings.server)

        F.app.session.login(input.getString("username"), input.getString("password"))
        androidx.work.WorkManager.getInstance(F.app).cancelAllWork().result.get()
        assertTrue(F.credentials.provision(F.password.toCharArray()).contains("已保存签名独立身份"))
        assertTrue(F.credentials.verifyLocally(F.password.toCharArray()).contains("可信期限已核验"))

        assertEquals(key, F.app.session.publicKey())
        assertEquals(device, F.app.session.settings.device)
        val after = checkNotNull(IndependentCredentialVault(F.app).read()).getJSONObject("entries")
        before.keys().asSequence().forEach { assertEquals(before.getJSONObject(it).toString(), after.getJSONObject(it).toString()) }
        assertEquals(before.length() + 1, after.length())
        val catalogSha = stageCatalog()
        File(directory, "renewal.json").writeText(renewal(before.length(), device, catalogSha).toString(2))
    }

    private fun renewal(oldEntries: Int, device: String, catalogSha: String) =
        JSONObject().put("nativeKeyPreserved", true)
            .put("oldEntriesPreserved", oldEntries).put("newCredentialEntries", 1)
            .put("realHttp", true).put("timeInjected", false).put("bootCount", TrustedTime.sample(F.app).boot)
            .put("catalogSha256", catalogSha).put("deviceId", device)

    private fun stageCatalog(): String {
        val repository = IndependentSkuRepository(F.app.database) { TrustedTime.sample(F.app) }
        val previous = checkNotNull(repository.active()).imported.id
        val bytes = (1..4).joinToString("\r\n") {
            "UI-LARGE-FONT-MODEL".padEnd(20) + "UI-COMBINATION".padEnd(14) + "003A000001$it".padEnd(13) + "15.501".padEnd(10)
        }.toByteArray(Charset.forName("GB18030"))
        val staged = repository.stage(bytes, "synthetic-large-font.txt")
        val active = repository.activate(staged.imported.id, false, previous)
        assertEquals(4, active.preview!!.validRows)
        return active.imported.sha256
    }
}

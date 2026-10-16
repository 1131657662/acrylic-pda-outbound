package com.acrylic.outbound.pda.retention

import android.content.Context
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject
import org.junit.Assert.*
import java.security.Security

/** Bootstrap already authenticated synthetic token; login/refresh and device Keystore are excluded. */
internal class FormalRepositorySession(context: Context, input: JSONObject, db: PdaDatabase) : AutoCloseable {
    private val provider = JvmSessionKeyStore.install()
    private val vault = SessionVault(context)
    val session: PdaSession

    init {
        vault.clear()
        val auth = JSONObject(input.getJSONObject("session").toString())
            .put("accessToken", input.getString("accessToken")).put("localSessionId", "synthetic-formal-repository")
            .put("signingKey", input.getJSONObject("signingKey"))
        vault.save(auth)
        assertEquals(auth.toString(), checkNotNull(vault.read()).toString())
        val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
        check(prefs.edit().putString("server", input.getString("proxyUrl"))
            .putString("device", auth.getString("deviceId")).commit())
        session = PdaSession(context, ConnectionSettings(context), DeviceKeys(), vault, RecoveryProtection(db))
    }

    override fun close() {
        vault.clear()
        Security.removeProvider(provider.name)
    }
}

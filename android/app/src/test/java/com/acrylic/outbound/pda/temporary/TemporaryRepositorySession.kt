package com.acrylic.outbound.pda.temporary

import android.content.Context
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.retention.JvmSessionKeyStore
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.security.Security

/** Synthetic authenticated token; real vault/session/network, no device identity or login claim. */
internal class TemporaryRepositorySession(context: Context, input: JSONObject, db: PdaDatabase) : AutoCloseable {
    private val provider = JvmSessionKeyStore.install()
    private val vault = SessionVault(context)
    val connection: TemporarySession

    init {
        vault.clear()
        val auth = JSONObject(input.getJSONObject("session").toString())
            .put("accessToken", input.getString("accessToken")).put("localSessionId", "synthetic-temp-repository")
            .put("signingKey", input.getJSONObject("signingKey"))
        vault.save(auth)
        assertEquals(auth.toString(), checkNotNull(vault.read()).toString())
        check(context.getSharedPreferences("connection", Context.MODE_PRIVATE).edit()
            .putString("server", input.getString("proxyUrl")).putString("device", auth.getString("deviceId")).commit())
        connection = TemporarySession(PdaSession(context, ConnectionSettings(context), DeviceKeys(), vault, RecoveryProtection(db)))
    }

    override fun close() {
        vault.clear()
        Security.removeProvider(provider.name)
    }
}

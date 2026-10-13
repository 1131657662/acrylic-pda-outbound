package com.acrylic.outbound.pda.independent

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class IndependentCredentialParityTest {
    @Test fun androidConsumesUnchangedServerSignedVector() {
        val path = checkNotNull(System.getenv("E06_CREDENTIAL_VECTOR_OUTPUT")) { "Run the server producer first; no synthetic Android replacement" }
        val value = JSONObject(File(path).readText(Charsets.UTF_8))
        val proof = IndependentCredentialProof.verify(value.getString("signedCredential"), value.getJSONObject("signingKey"))
        val claims = value.getJSONObject("claims")
        assertEquals(claims.getString("credentialId"), proof.id)
        assertEquals(claims.getString("operatorId"), proof.identity.operatorId)
        assertEquals(claims.getString("deviceId"), proof.identity.deviceId)
        assertEquals(claims.getString("warehouseId"), proof.identity.warehouseId)
        proof.at(proof.issued)
    }
}

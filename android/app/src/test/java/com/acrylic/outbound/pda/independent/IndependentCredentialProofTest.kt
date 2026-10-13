package com.acrylic.outbound.pda.independent

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class IndependentCredentialProofTest {
    @Test fun signedCredentialHasBoundedIndependentPurpose() {
        val f = IndependentCredentialFixture(); val value = f.claims()
        val proof = IndependentCredentialProof.verify(f.token(value), f.trusted)
        assertEquals(f.operator, proof.identity.operatorId); proof.at(f.issued)
        assertTrue(runCatching { proof.at(proof.expires) }.isFailure)
    }
    @Test fun wrongPurposeCapabilitiesTypesAndWindowAreRejected() {
        val f = IndependentCredentialFixture()
        val mutations = listOf<(org.json.JSONObject) -> Unit>(
            { it.put("aud", "acrylic-pda-offline") }, { it.put("purpose", "OWN_READ_ONLY_RESCUE") },
            { it.getJSONObject("capabilities").put("maySubmit", true) }, { it.put("serverRecoveryEpoch", 1) },
            { it.put("expiresAt", f.issued.plusSeconds(48*3600 + 1).toString()) },
            { it.put("expiresAt", f.issued.toString()) }, { it.put("operatorId", "unregistered-name") }, { it.put("extra", "x") })
        mutations.forEach { change ->
            val claims = f.claims(); change(claims)
            assertTrue(claims.toString(), runCatching { IndependentCredentialProof.verify(f.token(claims), f.trusted) }.isFailure)
        }
    }
    @Test fun duplicateClaimsAndUntrustedKeyFailClosed() {
        val f = IndependentCredentialFixture(); val original = f.claims().toString()
        val duplicate = original.dropLast(1) + ",\"purpose\":\"INDEPENDENT_CAPTURE\"}"
        assertTrue(runCatching { IndependentCredentialProof.verify(f.rawToken(duplicate), f.trusted) }.isFailure)
        assertTrue(runCatching { IndependentCredentialProof.verify(f.token(f.claims()), IndependentCredentialFixture().trusted) }.isFailure)
    }
}

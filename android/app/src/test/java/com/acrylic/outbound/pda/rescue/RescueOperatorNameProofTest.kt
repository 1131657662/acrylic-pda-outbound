package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.independent.IndependentCredentialFixture
import com.acrylic.outbound.pda.independent.IndependentCredentialProof
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RescueOperatorNameProofTest {
    private val authority = IndependentCredentialFixture()
    private val original = authority.receipt().getString("signedCredential")
    private val id = UUID.randomUUID().toString()
    private val binding = RescueOperatorNameProof.Binding("INDEPENDENT", id, authority.operator,
        authority.device, authority.warehouse, original, authority.issued)
    private fun claims() = JSONObject().put("iss", "acrylic-outbound").put("aud", RescueOperatorNameProof.AUDIENCE)
        .put("purpose", "READONLY_OPERATOR_NAME").put("source", binding.source).put("authorizationId", id)
        .put("operatorId", binding.operatorId).put("deviceId", binding.deviceId).put("warehouseId", binding.warehouseId)
        .put("originalSha256", JwtVerifier.sha(original.toByteArray(Charsets.UTF_8))).put("displayName", "原姓名甲")
        .put("issuedAt", binding.issued.toString())

    @Test fun nameIsDisplayEvidenceAndCannotBecomeIndependentWorkCredential() {
        val token = authority.token(claims())
        assertEquals("原姓名甲", RescueOperatorNameProof.verify(token, authority.trusted, binding))
        assertTrue(runCatching { IndependentCredentialProof.verify(token, authority.trusted) }.isFailure)
    }

    @Test fun originalWorkCredentialCannotBeUsedAsNameEvidence() {
        assertTrue(runCatching { RescueOperatorNameProof.verify(original, authority.trusted, binding) }.isFailure)
    }

    @Test fun everyOriginalBindingMismatchIsRejected() {
        val token = authority.token(claims())
        val wrong = listOf(binding.copy(source = "TEMP"), binding.copy(authorizationId = UUID.randomUUID().toString()),
            binding.copy(operatorId = UUID.randomUUID().toString()), binding.copy(deviceId = UUID.randomUUID().toString()),
            binding.copy(warehouseId = UUID.randomUUID().toString()), binding.copy(originalToken = original + "x"),
            binding.copy(issued = binding.issued.plusSeconds(1)))
        wrong.forEach { assertTrue(it.toString(), runCatching { RescueOperatorNameProof.verify(token, authority.trusted, it) }.isFailure) }
    }

    @Test fun alteredSignatureAndUnknownPublicKeyAreRejected() {
        val token = authority.token(claims())
        val parts = token.split('.'); val signature = java.util.Base64.getUrlDecoder().decode(parts[2])
        signature[0] = (signature[0].toInt() xor 1).toByte()
        val changed = parts.take(2).joinToString(".") + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        assertTrue(runCatching { RescueOperatorNameProof.verify(changed, authority.trusted, binding) }.isFailure)
        assertTrue(runCatching { RescueOperatorNameProof.verify(token, IndependentCredentialFixture().trusted, binding) }.isFailure)
    }

    @Test fun signedDuplicateUnknownAndWrongTypedFieldsAreRejected() {
        val payloads = listOf(claims().put("scope", "pda:scan").toString(),
            claims().put("displayName", true).toString(), claims().put("displayName", "").toString(),
            claims().toString().dropLast(1) + ",\"displayName\":\"替换姓名\"}")
        payloads.forEach { payload ->
            assertTrue(runCatching { RescueOperatorNameProof.verify(authority.rawToken(payload), authority.trusted, binding) }.isFailure)
        }
    }
}

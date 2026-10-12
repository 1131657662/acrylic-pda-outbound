package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RescueRevocationTest {
    @Test fun signedDuplicateFieldsTrailingDocumentsWrongTypesAndOversizeScopeAreRejected() {
        val f = RescueSupervisorFixture(); val text = f.claims().toString()
        val duplicate = text.dropLast(1) + ",\"grantId\":\"${f.grantId}\"}"
        for (raw in listOf(duplicate, text + "{}", text.replace("\"readOriginalAllowed\":true", "\"readOriginalAllowed\":\"true\"")))
            assertThrows(Exception::class.java) { RescueDelegation.verify(f.rawToken(raw), f.trusted, f.binding) }
        val claims = f.claims(); val scope = org.json.JSONArray()
        repeat(101) { scope.put(JSONObject().put("source", "TEMP").put("localOrderId", java.util.UUID.randomUUID())) }
        assertThrows(IllegalStateException::class.java) { RescueDelegation.verify(f.token(claims.put("orderScope", scope)), f.trusted, f.binding) }
    }
    @Test fun independentSupervisorMayRevokeOnlyTheExactOriginalAuthorization() {
        val f = RescueSupervisorFixture(); val token = f.token(f.claims())
        val verified = RescueDelegation.verify(token, f.trusted, f.binding)
        assertEquals(f.now.plusSeconds(1), RescueDelegation.verifyRevocation(f.token(f.revocation(token)), f.trusted, verified))
    }
    @Test fun differentGrantPersonDeviceWarehouseKeyOrAuthorizationHashIsRejected() {
        val f = RescueSupervisorFixture(); val token = f.token(f.claims())
        val verified = RescueDelegation.verify(token, f.trusted, f.binding)
        for (field in listOf("grantId", "rescuerId", "deviceId", "operatorId", "warehouseId", "deviceKeySha256", "authorizationSha256")) {
            val changed = f.revocation(token).put(field, if (field.endsWith("Sha256")) "b".repeat(64) else java.util.UUID.randomUUID().toString())
            assertThrows(IllegalStateException::class.java) { RescueDelegation.verifyRevocation(f.token(changed), f.trusted, verified) }
        }
    }
    @Test fun wrongRootOpenPurposeUnexpectedClaimsOrRevocationBeforeIssueIsRejected() {
        val f = RescueSupervisorFixture(); val token = f.token(f.claims())
        val verified = RescueDelegation.verify(token, f.trusted, f.binding)
        val values = listOf(f.revocation(token).put("purpose", "SUPERVISOR_READ_ONLY_RESCUE"),
            f.revocation(token).put("localDeletionAllowed", true), f.revocation(token).put("revokedAt", f.now.minusSeconds(1)))
        for (value in values) assertThrows(IllegalStateException::class.java) { RescueDelegation.verifyRevocation(f.token(value), f.trusted, verified) }
        assertThrows(IllegalStateException::class.java) {
            RescueDelegation.verifyRevocation(f.token(f.revocation(token)), RescueSupervisorFixture().trusted, verified)
        }
    }
    @Test fun serverMaterialAndRawSignedRevocationUseOnlyTheEmbeddedSignedTokens() {
        val f = RescueSupervisorFixture(); val token = f.token(f.claims()); val revocation = f.token(f.revocation(token))
        val data = JSONObject().put("authorization", JSONObject().put("grantId", f.grantId).put("signedAuthorization", token))
            .put("revocation", JSONObject().put("signedRevocation", revocation)).put("signingKey", "untrusted-ignored")
        assertEquals(RescueSupervisorMaterial(token, revocation), RescueSupervisorMaterial.read(JSONObject().put("data", data).toString()))
        assertEquals(RescueSupervisorMaterial(null, revocation), RescueSupervisorMaterial.read(revocation))
        assertThrows(IllegalStateException::class.java) { RescueSupervisorMaterial.read(data.toString() + "{}") }
        assertThrows(IllegalStateException::class.java) { RescueSupervisorMaterial.read(JSONObject().put("status", "REQUESTED").toString()) }
    }
}

package com.acrylic.outbound.pda.pairing

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class PairingVerificationTest {
    private fun fixture(): Pair<JSONObject, JSONObject> {
        val id = UUID.randomUUID().toString(); val key = "unit-public-key"
        val p = JSONObject().put("applicationId", id).put("publicKey", key).put("credential", "x".repeat(43)).put("model", "test").put("approvalSha256", "")
        val approval = JSONObject().put("applicationId", id).put("warehouseId", UUID.randomUUID().toString()).put("warehouseName", "测试仓")
            .put("deviceCode", "P-1").put("name", "东侧PDA").put("keySha256", PairingVerification.sha(key)).put("serverRecoveryEpoch", "1")
        val hash = PairingVerification.sha(listOf(id, approval.getString("warehouseId"), approval.getString("keySha256"), "1",
            PairingVerification.sha("测试仓"), PairingVerification.sha("P-1"), PairingVerification.sha("东侧PDA")).joinToString("\n"))
        val result = JSONObject().put("applicationId", id).put("keySha256", PairingVerification.sha(key)).put("serverRecoveryEpoch", "1")
            .put("status", "PENDING").put("shortCode", "00001234").put("approval", approval).put("approvalSha256", hash).put("deviceId", JSONObject.NULL)
        return JSONObject().put("payload", p).put("epoch", "1") to result
    }
    @Test fun pendingApprovalDoesNotGrantDeviceIdentity() {
        val (saved, result) = fixture(); PairingVerification.result(saved, result)
        result.put("deviceId", UUID.randomUUID().toString())
        assertThrows(IllegalStateException::class.java) { PairingVerification.result(saved, result) }
    }
    @Test fun completionRequiresThePersistedOperatorConfirmation() {
        val (saved, result) = fixture(); result.put("status", "COMPLETED").put("deviceId", UUID.randomUUID().toString())
        assertThrows(IllegalStateException::class.java) { PairingVerification.result(saved, result) }
        saved.getJSONObject("payload").put("approvalSha256", result.getString("approvalSha256")); PairingVerification.result(saved, result)
    }
    @Test fun changedWarehouseNameOrCrossDeviceResultIsRejected() {
        val (saved, result) = fixture(); result.getJSONObject("approval").put("name", "被替换的设备")
        assertThrows(IllegalStateException::class.java) { PairingVerification.result(saved, result) }
        val (original, other) = fixture()
        assertThrows(IllegalStateException::class.java) { PairingVerification.result(original, result) }
        other.put("serverRecoveryEpoch", "2")
        assertThrows(IllegalStateException::class.java) { PairingVerification.result(original, other) }
    }
    @Test fun proofCannotSignWrongPurposeKeyOrBody() {
        val (saved, _) = fixture(); val p = saved.getJSONObject("payload"); val id = UUID.randomUUID().toString()
        val text = "acrylic-pairing-proof-v1\n$id\n${p.getString("applicationId")}\nSTATUS\n${"a".repeat(43)}\n${PairingVerification.sha(p.getString("publicKey"))}\n${PairingVerification.sha(PairingVerification.binding(p))}\n1"
        val challenge = JSONObject().put("challengeId", id).put("signedText", text)
        assertEquals(text, PairingVerification.challenge(p, "STATUS", challenge, "1"))
        assertThrows(IllegalStateException::class.java) { PairingVerification.challenge(p, "CONFIRM", challenge, "1") }
        p.put("credential", "b".repeat(43))
        assertThrows(IllegalStateException::class.java) { PairingVerification.challenge(p, "STATUS", challenge, "1") }
    }
}

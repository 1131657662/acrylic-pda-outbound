package com.acrylic.outbound.pda.independent

import org.json.JSONObject

internal data class IndependentCredentialSnapshot(val proof: IndependentCredentialProof, val trustedKeyJson: String,
    val timeAnchorJson: String, val localSessionId: String) {
    fun json(): String = JSONObject().put("signedCredential", proof.originalToken).put("trustedKey", JSONObject(trustedKeyJson))
        .put("timeAnchor", JSONObject(timeAnchorJson)).put("localSessionId", localSessionId).toString()
    fun requireSame(other: IndependentCredentialSnapshot) {
        check(proof.identity == other.proof.identity && localSessionId == other.localSessionId) { "原任务请求的人机仓或登录代次已变化；原件保留" }
    }
    companion object {
        fun read(original: String): IndependentCredentialSnapshot {
            val value = JSONObject(original); val key = value.getJSONObject("trustedKey").toString()
            return IndependentCredentialSnapshot(IndependentCredentialProof.verify(value.getString("signedCredential"), JSONObject(key)),
                key, value.getJSONObject("timeAnchor").toString(), value.getString("localSessionId"))
        }
    }
}

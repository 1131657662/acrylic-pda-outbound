package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import org.json.JSONTokener
import java.util.Base64

/** Transport wrappers carry signed tokens, never a verification authority or a claimed login identity. */
internal data class RescueSupervisorMaterial(val authorization: String?, val revocation: String?) {
    companion object {
        fun read(text: String): RescueSupervisorMaterial {
            check(text.length in 1..131_072) { "主管材料为空或超过读取上限" }
            val original = text.trim()
            if (!original.startsWith("{")) return token(original)
            val parser = JSONTokener(original)
            val wrapper = parser.nextValue() as? JSONObject ?: error("主管材料格式无效")
            check(parser.nextClean() == '\u0000') { "主管材料包含多余正文" }
            val material = wrapper.optJSONObject("data") ?: wrapper
            val auth = material.optJSONObject("authorization") ?: material
            val revocation = material.optJSONObject("revocation") ?: material
            val token = auth.optString("signedAuthorization").takeIf { it.isNotBlank() }
            val revoked = revocation.optString("signedRevocation").takeIf { it.isNotBlank() }
            check(token != null || revoked != null) { "材料尚未获主管签名授权或撤销" }
            return RescueSupervisorMaterial(token, revoked)
        }
        private fun token(token: String): RescueSupervisorMaterial {
            check(token.length <= 32_768 && token.split('.').size == 3) { "主管签名格式错误" }
            val claims = JSONObject(Base64.getUrlDecoder().decode(token.split('.')[1]).toString(Charsets.UTF_8))
            return when (claims.getString("purpose")) {
                "SUPERVISOR_READ_ONLY_RESCUE" -> RescueSupervisorMaterial(token, null)
                "SUPERVISOR_READ_ONLY_RESCUE_REVOCATION" -> RescueSupervisorMaterial(null, token)
                else -> error("材料不是主管只读救援凭据")
            }
        }
    }
}

package com.acrylic.outbound.pda.rescue

import org.json.JSONArray
import org.json.JSONObject

/** Evidence files never carry reusable login or offline operation credentials. */
internal object RescuePublicEvidence {
    private val secrets = setOf("password", "accesstoken", "refreshtoken", "offlinetoken", "privatekey", "rescuepassword")
    fun verify(value: Any, depth: Int = 0) {
        check(depth <= 32) { "公开核验材料嵌套超过上限" }
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                check(key.lowercase() !in secrets) { "公开核验材料含凭据，请保留原库并联系管理员" }
                verify(value.get(key), depth + 1)
            }
            is JSONArray -> (0 until value.length()).forEach { verify(value.get(it), depth + 1) }
        }
    }
}

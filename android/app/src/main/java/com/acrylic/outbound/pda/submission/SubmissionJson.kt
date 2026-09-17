package com.acrylic.outbound.pda.submission

import org.json.JSONArray
import org.json.JSONObject

object SubmissionJson {
    fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value).replace("\\/", "/")
        is Boolean -> value.toString()
        is Int, is Long -> value.toString().also { check(it.toLong() in -9007199254740991L..9007199254740991L) }
        else -> error("冻结正文存在不支持的数据类型")
    }
}

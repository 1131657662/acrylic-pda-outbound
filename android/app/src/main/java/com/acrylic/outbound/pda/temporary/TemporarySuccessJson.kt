package com.acrylic.outbound.pda.temporary

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Strict parsing for original success responses, whose envelope need not be canonical JSON. */
internal object TemporarySuccessJson {
    fun parse(bytes: ByteArray): JSONObject {
        check(bytes.size in 1..262144) { "成功原响应长度无效" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return JsonReader(StringReader(text)).use { reader ->
            val value = value(reader, 0)
            check(reader.peek() == JsonToken.END_DOCUMENT && value is JSONObject) { "成功原响应不完整" }
            value
        }
    }

    private fun value(reader: JsonReader, depth: Int): Any {
        check(depth <= 16) { "成功原响应嵌套过深" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> objectValue(reader, depth)
            JsonToken.BEGIN_ARRAY -> JSONArray().also { array ->
                reader.beginArray()
                while (reader.hasNext()) array.put(value(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString().also {
                check(it.matches(Regex("-?(0|[1-9][0-9]*)"))) { "成功原响应含非整数" }
            }.toLong()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> error("成功原响应格式无效")
        }
    }

    private fun objectValue(reader: JsonReader, depth: Int): JSONObject {
        val result = JSONObject(); reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            check(!result.has(name)) { "成功原响应含重复字段" }
            result.put(name, value(reader, depth + 1))
        }
        reader.endObject(); return result
    }
}

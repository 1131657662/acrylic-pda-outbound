package com.acrylic.outbound.pda.rescue

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Rescue protocol claims contain only strings, booleans, null and bounded objects/arrays. */
internal object RescueSignedJson {
    fun read(bytes: ByteArray): JSONObject {
        check(bytes.size in 1..32_768)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return JsonReader(StringReader(text)).use { reader ->
            val value = value(reader, 0)
            check(reader.peek() == JsonToken.END_DOCUMENT && value is JSONObject) { "主管签名正文不完整" }
            value
        }
    }
    private fun value(reader: JsonReader, depth: Int): Any {
        check(depth <= 8) { "主管签名正文嵌套过深" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> objectValue(reader, depth)
            JsonToken.BEGIN_ARRAY -> JSONArray().also { array ->
                reader.beginArray()
                while (reader.hasNext()) { check(array.length() < 100); array.put(value(reader, depth + 1)) }
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> error("主管签名正文字段类型无效")
        }
    }
    private fun objectValue(reader: JsonReader, depth: Int): JSONObject {
        val result = JSONObject(); reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName(); check(!result.has(name)) { "主管签名正文含重复字段" }
            result.put(name, value(reader, depth + 1))
        }
        reader.endObject(); return result
    }
}

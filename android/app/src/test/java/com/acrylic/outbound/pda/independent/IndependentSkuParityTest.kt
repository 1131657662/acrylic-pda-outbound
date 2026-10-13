package com.acrylic.outbound.pda.independent

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class IndependentSkuParityTest {
    @Test fun sameOriginalBytesMatchActualServerResultForEveryField() {
        val input = System.getenv("SKU_PARITY_OUTPUT")
        assumeTrue("Run with actual server-produced SKU_PARITY_OUTPUT", input != null)
        val vectors = JSONArray(File(checkNotNull(input)).readText())
        assertTrue(vectors.length() > 40)
        for (i in 0 until vectors.length()) verify(vectors.getJSONObject(i))
    }
    private fun verify(vector: JSONObject) {
        val name = vector.getString("name")
        println("PARITY original-byte-case=$name")
        val bytes = Base64.getDecoder().decode(vector.getString("bytes"))
        if (vector.has("errorCode")) {
            val error = assertThrows(IndependentSkuException::class.java) { IndependentSkuParser.parse(bytes) }
            assertEquals(name, vector.getString("errorCode"), error.code)
            assertEquals(name, vector.getString("message"), error.message)
        } else compare(name, vector.getJSONObject("preview"), json(IndependentSkuParser.parse(bytes)))
    }
    private fun compare(name: String, expected: Any, actual: Any) {
        when (expected) {
            is JSONObject -> {
                actual as JSONObject
                assertEquals(name, expected.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
                expected.keys().forEach { compare("$name.$it", expected.get(it), actual.get(it)) }
            }
            is JSONArray -> {
                actual as JSONArray; assertEquals(name, expected.length(), actual.length())
                for (i in 0 until expected.length()) compare("$name[$i]", expected.get(i), actual.get(i))
            }
            else -> assertEquals(name, expected.toString(), actual.toString())
        }
    }
    private fun json(p: IndependentSkuPreview): JSONObject = JSONObject().put("parserVersion", p.parserVersion).put("status", p.status)
        .put("totalRows", p.totalRows).put("validRows", p.validRows).put("errorRows", p.errorRows).put("duplicateRows", p.duplicateRows)
        .put("ignoredRows", p.ignoredRows).put("productCount", p.productCount).put("totalWeightGrams", p.totalWeightGrams)
        .put("bomRemoved", p.bomRemoved).put("rows", JSONArray(p.rows.map(::row)))
    private fun row(r: IndependentSkuRow): JSONObject = JSONObject().put("lineNo", r.lineNo).put("rawText", r.rawText)
        .put("errorCode", r.errorCode ?: JSONObject.NULL).put("errorDetail", r.errorDetail ?: JSONObject.NULL)
        .put("parsedFields", r.parsedFields?.let(::bale) ?: JSONObject.NULL)
    private fun bale(b: IndependentSkuBale): JSONObject = JSONObject().put("model", b.model).put("name", b.name).put("barcode", b.barcode)
        .put("deliveryCode", b.deliveryCode).put("rawWeight", b.rawWeight).put("weightGrams", b.weightGrams)
}

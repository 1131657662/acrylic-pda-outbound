package com.acrylic.outbound.pda.preparation

import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Base64

object ContractFixture {
    fun read(): JSONObject {
        val file = File(checkNotNull(System.getProperty("pda.contractFixture")))
        check(file.isFile) { "Run server integrationTest first to generate the real signed contract fixture" }
        val fixture = try {
            JSONObject(file.readText())
        } catch (cause: Exception) {
            throw IllegalStateException("Formal contract fixture is not valid JSON: ${file.absolutePath}", cause)
        }
        val required = listOf("assignment", "receipt", "signingKey", "session", "gzip")
        val missing = required.filterNot { fixture.has(it) && fixture.opt(it) is JSONObject }
        check(missing.isEmpty()) {
            "Formal contract fixture is incomplete; missing object fields: ${missing.joinToString(", ")} (${file.absolutePath})"
        }
        return fixture
    }

    fun unpack(root: File, fixture: JSONObject): Map<String, ByteArray> {
        val parts = JSONObject(fixture.getJSONObject("receipt").getString("manifest")).getJSONArray("parts")
        return (0 until parts.length()).associate { index ->
            val part = parts.getJSONObject(index); val name = part.getString("name")
            val file = File(root, "$name.gz").apply { writeBytes(Base64.getDecoder().decode(fixture.getJSONObject("gzip").getString(name))) }
            name to PackageVerifier.unpack(file, part)
        }
    }

    fun input(root: File, fixture: JSONObject = read()): VerifiedPackage = VerifiedPackage(
        fixture.getJSONObject("assignment"), fixture.getJSONObject("receipt"), fixture.getJSONObject("signingKey"),
        fixture.getJSONObject("session"), unpack(root, fixture), Instant.parse(fixture.getJSONObject("assignment").getString("issuedAt")).plusSeconds(1))
}

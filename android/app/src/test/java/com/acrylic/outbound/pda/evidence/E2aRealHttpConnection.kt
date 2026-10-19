package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject
import java.io.IOException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** Only replaces session and Keystore boundaries. Requests use production Retrofit/OkHttp code. */
class E2aRealHttpConnection(private val work: TemporaryWorkFixture, private val input: JSONObject) :
    TemporaryConnection by work.fixture {
    private val network = PdaNetwork(input.getString("baseUrl")) {
        com.acrylic.outbound.pda.sync.RecoveryProtection(work.db).observe(it)
    }
    private val headers = mapOf("Authorization" to "Bearer ${input.getString("accessToken")}", "X-Server-Recovery-Epoch" to "1")
    private val drops = mutableSetOf("OPEN", "RAW", "PAGE", "STOP", "VERIFY")
    val posts = mutableListOf<String>()
    var signatures = 0
    var unavailable = false
    val receiptBytes = linkedMapOf<String, ByteArray>()
    val originalRequests = linkedMapOf<String, JSONObject>()

    init {
        val port = System.getenv("E2A_HTTP_PORT")?.takeIf { it.isNotBlank() } ?: "19434"
        check(input.getString("baseUrl") == "http://127.0.0.1:$port/api/v1/")
    }

    override fun original(request: TemporaryConnection.Original): ByteArray {
        check(!work.db.inTransaction())
        val bytes = network.original(request.path, request.body, headers, request.raw)
        if (!request.path.contains("temporary-source-evidence/") && !request.path.contains("local-draft-evidence/")) return bytes
        val receipt = JSONObject(bytes.toString(Charsets.UTF_8))
        val operation = receipt.getString("operationKind")
        receiptBytes[receipt.getString("originalKey")] = bytes.copyOf()
        if (request.body != null || request.raw != null) {
            val retained = JSONObject().put("path", request.path).put("operation", operation)
            request.body?.let { retained.put("body", Base64.getEncoder().encodeToString(it)) }
            request.raw?.let { retained.put("raw", Base64.getEncoder().encodeToString(it)) }
            val key = receipt.getString("originalKey")
            originalRequests[key]?.let { check(it.toString() == retained.toString()) }
            originalRequests[key] = retained
            posts += request.path
            if (drops.remove(if (operation == "ITEM") "RAW" else operation)) throw IOException("Injected lost $operation response after server commit")
        }
        return bytes
    }

    override fun call(request: TemporaryConnection.Call): JSONObject {
        check(!work.db.inTransaction())
        val data = network.request(request.path, request.body, headers, request.raw).getJSONObject("data")
        if (request.path.contains("temporary-source-evidence/raw?")) {
            val raw = checkNotNull(request.raw)
            originalRequests[com.acrylic.outbound.pda.evidence.E2aWire.sha(raw)] = JSONObject()
                .put("path", request.path).put("operation", "ITEM").put("raw", Base64.getEncoder().encodeToString(raw))
            posts += request.path
            if (drops.remove("RAW")) throw IOException("Injected lost RAW response after server commit")
        }
        return data
    }

    override fun stopProof(facts: E2aStopProof.Facts): E2aStopProof.Signed {
        check(!unavailable) { "Original device signer intentionally unavailable after restart" }
        signatures++
        val publicKey = input.getString("devicePublicKey")
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(
            Base64.getDecoder().decode(input.getString("devicePrivateKey"))))
        return E2aStopProof.Signed(publicKey, E2aStopProof.sign(facts, publicKey) { text ->
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(key); signer.update(text.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(signer.sign())
        })
    }
    override fun localDraftStopKey() = input.getString("devicePublicKey")

    override fun signLocalDraftStop(stop: LocalDraftStop): LocalDraftStopProof.Signed {
        check(!unavailable) { "Original device signer intentionally unavailable after restart" }
        signatures++
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(
            Base64.getDecoder().decode(input.getString("devicePrivateKey"))))
        return LocalDraftStopProof.sign(stop, localDraftStopKey()) { text ->
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(key); signer.update(text.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(signer.sign())
        }
    }
}

package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID

/** Synthetic identity/EC signer; reopened stores consume the same serialized bytes. */
internal class RescueRequestFixture {
    val identity = RescueReadIdentity(id(), id(), id())
    val target = RescueTarget("TEMP", id())
    val rescuer = id()
    val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    val signer = RescueSigner(key.public.encoded) { text ->
        Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private); update(text.toByteArray(Charsets.UTF_8)); sign()
        })
    }
    val storage = RescueSupervisorFixture.Storage()
    fun intent(input: RescueRequestInput = RescueRequestInput(identity, target, rescuer, "synthetic read rescue")) =
        RescueRequestDocument.create(input, "1", signer)
    fun store() = RescueRequestStore(storage)
    fun receipt(intent: RescueRequestIntent) = JSONObject().put("requestId", id())
        .put("requestSha256", RescueBundle.sha(intent.originalText.toByteArray(Charsets.UTF_8)))
    fun material(intent: RescueRequestIntent) = JSONObject().put("request", JSONObject(checkNotNull(intent.receipt)))
        .put("originalText", intent.originalText).put("deviceSignature", intent.signature)
        .put("orderScope", intent.document.getJSONArray("orderScope")).put("status", "REQUESTED")
        .put("authorization", JSONObject.NULL).put("revocation", JSONObject.NULL).put("localDeletionAllowed", false)
    private fun id() = UUID.randomUUID().toString()
}

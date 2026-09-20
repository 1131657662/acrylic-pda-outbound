package com.acrylic.outbound.pda.network

import org.json.JSONObject

/** Navigation hints only: signed credentials and server permissions remain mandatory. */
object PdaCapabilities {
    fun formal(auth: JSONObject) = allowed(auth, "formalOrders", true)
    fun temporary(auth: JSONObject) = allowed(auth, "temporaryWork", false)
    fun barcodes(auth: JSONObject) = allowed(auth, "barcodeSync", true)
    fun audit(auth: JSONObject) = allowed(auth, "auditUpload", true)
    fun recovery(auth: JSONObject) = allowed(auth, "recoveryRead", true)
    private fun allowed(auth: JSONObject, field: String, legacy: Boolean): Boolean =
        if (!auth.has("capabilities")) legacy else auth.optJSONObject("capabilities")?.opt(field) == true
}

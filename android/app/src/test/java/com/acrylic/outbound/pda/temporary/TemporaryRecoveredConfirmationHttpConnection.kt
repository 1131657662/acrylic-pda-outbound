package com.acrylic.outbound.pda.temporary

import org.json.JSONObject
import java.io.File

/** Actual product HTTP bytes; one explicit client-boundary response loss tests durable replay. */
internal class TemporaryRecoveredConfirmationHttpConnection(input: JSONObject, private val output: File,
    private val delegate: TemporaryRecoveredHandoverHttpConnection = TemporaryRecoveredHandoverHttpConnection(input, output)
        .also { it.losePayload = false }) : TemporaryConnection by delegate {
    var loseConfirmation = false
    private var ordinal = 0
    override fun original(request: TemporaryConnection.Original): ByteArray {
        val response = delegate.original(request)
        if (request.path.endsWith("recovered-confirmation")) {
            File(output, "command-${++ordinal}.json").writeBytes(checkNotNull(request.body))
            File(output, "receipt-$ordinal.json").writeBytes(response)
            if (loseConfirmation) { loseConfirmation = false; throw java.io.IOException("Injected client-boundary loss of actual POST receipt") }
        }
        return response
    }
}

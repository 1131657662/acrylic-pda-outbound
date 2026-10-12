package com.acrylic.outbound.pda.rescue

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RescueRequestDocumentTest {
    @Test fun exactOriginalBodyHasValidScopedDeviceSignature() {
        val f = RescueRequestFixture(); val intent = f.intent()
        RescueRequestDocument.verify(intent, f.identity, f.key.public.encoded)
        assertEquals(f.target, intent.target); assertEquals(f.rescuer, intent.document.getString("rescuerId"))
    }
    @Test fun alteredBodySignatureIdentityOrKeyCannotBeSent() {
        val f = RescueRequestFixture(); val intent = f.intent()
        for (changed in listOf(intent.copy(originalText = intent.originalText.replace("synthetic read rescue", "altered request")),
            intent.copy(signature = "AA=="), intent.copy(keySha256 = "a".repeat(64))))
            assertThrows(Exception::class.java) { RescueRequestDocument.verify(changed, f.identity, f.key.public.encoded) }
        assertThrows(IllegalStateException::class.java) { RescueRequestDocument.verify(intent, f.identity.copy(operatorId = f.rescuer), f.key.public.encoded) }
        assertThrows(IllegalStateException::class.java) { RescueRequestDocument.verify(intent, f.identity, RescueRequestFixture().key.public.encoded) }
    }
    @Test fun selfRecipientInvalidIdentifiersEmptyReasonAndScopeRejectBeforePersistence() {
        val f = RescueRequestFixture()
        for (input in listOf(RescueRequestInput(f.identity, f.target, f.identity.operatorId, "reason"),
            RescueRequestInput(f.identity, f.target, "invalid", "reason"),
            RescueRequestInput(f.identity, f.target, f.rescuer, " "),
            RescueRequestInput(f.identity, f.target.copy(source = "OTHER"), f.rescuer, "reason")))
            assertThrows(Exception::class.java) { f.intent(input) }
    }
}

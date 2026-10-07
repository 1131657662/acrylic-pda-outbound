package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryInput
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Uses real local input to distinguish the device boundary from the source's own scan sequence. */
internal object E2aRealHttpOtherSource {
    fun capture(work: TemporaryWorkFixture) {
        val other = UUID.randomUUID().toString()
        val plan = work.plan("1").copy(revision = 0)
        work.drafts.create(other)
        val saved = work.drafts.save(other, plan)
        work.drafts.start(other, saved.revision)
        val capture = work.processor.reception.receive(TemporaryInput(other, "INVALID-OTHER-SOURCE", "MANUAL", work.store.sample()))
        work.processor.drain(other)
        work.processor.corrections.acknowledge(capture.id)
        assertTrue(capture.deviceCaptureSeq > 1)
        assertEquals(1, work.db.temporaryScans().captures(work.id).size)
        work.store.load(work.id)
    }
}

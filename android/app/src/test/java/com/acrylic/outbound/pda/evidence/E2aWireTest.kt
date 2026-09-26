package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class E2aWireTest {
    @Test fun emptySetMatchesTheServerProductionVectorByteForByte() {
        val vector = JSONObject(File(checkNotNull(System.getProperty("pda.e2aVector"))).readText())
        val source = JSONObject(vector.getString("manifestCoreUtf8"))
        val identity = E2aWire.Identity(source.getString("manifestId"), source.getString("snapshotId"),
            source.getString("sourceId"), source.getString("submissionId"), source.getLong("workRound"))

        val page = E2aWire.page(identity, 1, 1, null, JSONArray())
        val manifest = E2aWire.manifest(identity, listOf(page))

        assertEquals(vector.getString("pageCoreUtf8"), page.canonicalCore)
        assertEquals(vector.getString("pageHash"), page.hash)
        assertEquals(vector.getString("manifestCoreUtf8"), manifest.canonicalCore)
        assertEquals(vector.getString("manifestBytesHash"), manifest.bytesHash)
        assertEquals(vector.getString("setHash"), manifest.setHash)
    }

    @Test fun pageChainRejectsReorderedOrAlteredRoots() {
        val identity = identity()
        val first = E2aWire.page(identity, 1, 2, null, JSONArray())
        val second = E2aWire.page(identity, 2, 2, first.hash, JSONArray())

        E2aWire.manifest(identity, listOf(first, second))

        assertThrows(IllegalStateException::class.java) { E2aWire.manifest(identity, listOf(second, first)) }
        assertThrows(IllegalStateException::class.java) {
            E2aWire.manifest(identity, listOf(first, second.copy(hash = "0".repeat(64))))
        }
        assertThrows(IllegalStateException::class.java) { E2aWire.page(identity, 2, 2, null, JSONArray()) }
    }

    @Test fun duplicateRawObjectsAndInvalidScanReferencesNeverFormAPage() {
        val identity = identity()
        val record = UUID.randomUUID().toString()
        val raw = UUID.randomUUID().toString()
        val first = E2aWire.item("CAPTURE", record, "CAPTURE_RECORDS", raw,
            "capture".toByteArray(), 1, "a".repeat(64), "COMMITTED")

        assertThrows(IllegalStateException::class.java) {
            E2aWire.page(identity, 1, 1, null, JSONArray().put(first).put(JSONObject(first.toString())))
        }
        assertThrows(IllegalStateException::class.java) {
            E2aWire.item("SCAN", record, "CAPTURE_RECORDS", UUID.randomUUID().toString(),
                "scan".toByteArray(), 2, "b".repeat(64), "ACTIVE")
        }
    }

    @Test fun scanIdentifiersMustBeCanonicalUuids() {
        assertThrows(IllegalArgumentException::class.java) {
            E2aWire.item("SCAN", UUID.randomUUID().toString(), "CAPTURE_RECORDS", UUID.randomUUID().toString(),
                "scan".toByteArray(), 2, "b".repeat(64), "ACTIVE", "not-a-uuid", 1,
                UUID.randomUUID().toString())
        }
    }

    @Test fun pageRejectsIncompleteOrUnexpectedItemFields() {
        val reference = E2aWire.item("CAPTURE", UUID.randomUUID().toString(), "CAPTURE_RECORDS",
            UUID.randomUUID().toString(), "capture".toByteArray(), 1, "a".repeat(64), "COMMITTED")
        reference.remove("itemBytesSha256")
        assertThrows(IllegalStateException::class.java) {
            E2aWire.page(identity(), 1, 1, null, JSONArray().put(reference))
        }
        reference.put("itemBytesSha256", "b".repeat(64)).put("unrecognized", "value")
        assertThrows(IllegalStateException::class.java) {
            E2aWire.page(identity(), 1, 1, null, JSONArray().put(reference))
        }
    }

    @Test fun manifestRejectsStalePageTotalsAndFinalMarker() {
        val identity = identity()
        val page = E2aWire.page(identity, 1, 1, null, JSONArray())
        val altered = JSONObject(page.canonicalCore).put("byteCount", 1)
        assertThrows(IllegalStateException::class.java) {
            E2aWire.manifest(identity, listOf(page.copy(canonicalCore = SubmissionJson.canonical(altered))))
        }
        val notFinal = JSONObject(page.canonicalCore).put("isFinal", false)
        assertThrows(IllegalStateException::class.java) {
            E2aWire.manifest(identity, listOf(page.copy(canonicalCore = SubmissionJson.canonical(notFinal))))
        }
    }

    @Test fun manifestRejectsReusedRawObjectsAcrossPages() {
        val identity = identity()
        val reference = E2aWire.item("CAPTURE", UUID.randomUUID().toString(), "CAPTURE_RECORDS",
            UUID.randomUUID().toString(), "capture".toByteArray(), 1, "a".repeat(64), "COMMITTED")
        val first = E2aWire.page(identity, 1, 2, null, JSONArray().put(reference))
        val second = E2aWire.page(identity, 2, 2, first.hash, JSONArray().put(reference))
        assertThrows(IllegalStateException::class.java) { E2aWire.manifest(identity, listOf(first, second)) }
    }

    private fun identity() = E2aWire.Identity(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1)
}

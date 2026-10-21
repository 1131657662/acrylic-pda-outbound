package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.security.DeviceKeys
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Synthetic JCA/HTTP only. Captures the original product files for the PC consumer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescuePackageSecretVectorTest {
    @Test fun nonemptyOriginalRoomExportsMaterialAndPackageForIndependentPcConsumer() {
        val root = System.getenv("PDA_RESCUE_SECRET_VECTOR_DIR")?.let(::File)
        if (root != null) {
            assertTrue(root.mkdirs() || root.isDirectory)
            assertTrue(root.listFiles().orEmpty().isEmpty())
        }
        RescuePackageSetupUiFixture().use { f ->
            val public = DeviceKeys().publicKey(false)
            val trust = JSONObject().put(f.work.session.getString("deviceId"), JSONObject()
                .put("publicKeyDerBase64", public)
                .put("operatorIds", JSONArray().put(f.work.session.getString("userId")))
                .put("warehouseIds", JSONArray().put(f.work.session.getString("warehouseId"))))
            f.vault.clear(); f.server.shutdown(); f.open()
            val material = f.save(); val document = JSONObject(material.readText())
            f.verify(material); f.confirm()
            assertTrue(f.model.state.value.canCopy)
            assertEquals("FORMAL", document.getString("source"))
            assertEquals(f.work.assignment, document.getString("localOrderId"))
            assertEquals(f.work.session.getString("deviceId"), document.getString("deviceId"))
            val files = mapOf("package-secret.json" to material.readBytes(),
                "external-trust.json" to trust.toString().toByteArray(Charsets.UTF_8),
                "android-room.pdarescue" to f.packages().single().readBytes())
            if (root != null) files.forEach { (name, bytes) -> Files.write(root.toPath().resolve(name), bytes, CREATE_NEW) }
        }
    }
}

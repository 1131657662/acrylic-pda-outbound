package com.acrylic.outbound.pda.update

import android.util.Base64
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class UpdateManifestVerifierTest {
    @Test fun signedManifestWithMatchingChannelAndVersionIsAccepted() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val payload = payloadJson()
        val raw = payload.toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(keys.private); update(raw) }.sign()
        val manifest = SignedUpdateManifest.parse(JSONObject().put("payload", Base64.encodeToString(raw, Base64.NO_WRAP))
            .put("signature", Base64.encodeToString(signature, Base64.NO_WRAP)).toString())

        val verified = UpdateManifestVerifier(Base64.encodeToString(keys.public.encoded, Base64.NO_WRAP)).verify(manifest)

        assertEquals("staging", verified.channel)
        UpdateCompatibility("com.acrylic.outbound.pda.staging", "staging", 10, 35).check(verified)
    }

    @Test fun modifiedPayloadIsRejectedEvenWhenJsonShapeRemainsValid() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val original = payloadJson().toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(keys.private); update(original) }.sign()
        val modified = JSONObject(String(original, Charsets.UTF_8)).put("releaseNotes", "被篡改").toString().toByteArray(Charsets.UTF_8)
        val manifest = SignedUpdateManifest.parse(JSONObject().put("payload", Base64.encodeToString(modified, Base64.NO_WRAP))
            .put("signature", Base64.encodeToString(signature, Base64.NO_WRAP)).toString())

        assertThrows(IllegalStateException::class.java) {
            UpdateManifestVerifier(Base64.encodeToString(keys.public.encoded, Base64.NO_WRAP)).verify(manifest)
        }
    }

    @Test fun incompatibleChannelIsRejectedBeforeInstall() {
        val payload = UpdatePayload.parse(payloadJson().toString())

        assertThrows(IllegalStateException::class.java) {
            UpdateCompatibility("com.acrylic.outbound.pda.debug", "debug", 10, 35).check(payload)
        }
    }

    @Test fun unknownServerCompatibilityIsNotReportedAsCurrent() {
        val payload = UpdatePayload.parse(payloadJson().put("serverCompatibility", "unknown").toString())
        assertThrows(IllegalStateException::class.java) {
            UpdateCompatibility(payload.packageName, "staging", 10, 28).check(payload)
        }
    }

    @Test fun sourceOutsideVerifiedUpgradeRangeIsRejected() {
        val payload = UpdatePayload.parse(payloadJson().toString())
        assertThrows(IllegalStateException::class.java) {
            UpdateCompatibility(payload.packageName, "staging", 9, 28).check(payload)
        }
    }

    @Test fun truncatedApkIsRejectedBeforeReadingItsIdentity() {
        val file = java.io.File.createTempFile("truncated-update", ".apk")
        try {
            file.writeText("short")
            assertThrows(IllegalStateException::class.java) {
                ApkVerifier(androidx.test.core.app.ApplicationProvider.getApplicationContext()).verify(file,
                    UpdatePayload.parse(payloadJson().toString()))
            }
        } finally { file.delete() }
    }

    @Test fun changedApkWithCorrectSizeIsRejectedByDigest() {
        val file = java.io.File.createTempFile("changed-update", ".apk")
        try {
            file.writeBytes(ByteArray(1024) { 1 })
            val error = assertThrows(IllegalStateException::class.java) {
                ApkVerifier(androidx.test.core.app.ApplicationProvider.getApplicationContext()).verify(file,
                    UpdatePayload.parse(payloadJson().toString()))
            }
            org.junit.Assert.assertTrue(error.message!!.contains("摘要校验失败"))
        } finally { file.delete() }
    }

    private fun payloadJson() = JSONObject().put("apkPath", "updates/staging/r05-update-01.apk")
        .put("certificateSha256", "a".repeat(64)).put("channel", "staging")
        .put("maxSupportedVersionCode", JSONObject.NULL).put("minAndroidApi", 28)
        .put("minSupportedVersionCode", 10).put("packageName", "com.acrylic.outbound.pda.staging")
        .put("publishedAt", "2026-09-18T14:00:00Z").put("releaseId", "r05-update-01")
        .put("releaseNotes", "更新测试").put("serverCompatibility", "0.17.0")
        .put("sha256", "b".repeat(64)).put("sizeBytes", 1024).put("versionCode", 11)
        .put("versionName", "0.10.0-r05-update")
}

package com.acrylic.outbound.pda.update

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class UpdateHttpTest {
    @get:Rule val temporary = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var http: UpdateHttp

    @Before fun setUp() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val identity = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server.useHttps(identity.sslSocketFactory(), false); server.start()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        http = UpdateHttp(server.url("api/v1/").newBuilder().host("localhost").build().toString(), "staging",
            OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build())
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun plainHttpIsRejectedEvenForLoopback() {
        assertThrows(IllegalStateException::class.java) { UpdateHttp("http://localhost:8080/api/v1/", "staging") }
    }

    @Test fun validRangeResumesExistingBytes() {
        val file = temporary.newFile().apply { writeText("abc") }
        server.enqueue(MockResponse().setResponseCode(206).setHeader("ETag", "\"one\"")
            .setHeader("Content-Range", "bytes 3-5/6").setBody("def"))
        val result = http.download(UpdateHttp.DownloadRequest("updates/staging/test.apk", file, 6, 3, "\"one\""), { _, _ -> }, {})
        assertTrue(result.resumed); assertEquals("abcdef", file.readText())
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
    }

    @Test fun changedResourceReturning200RestartsTheFile() {
        val file = temporary.newFile().apply { writeText("old") }
        server.enqueue(MockResponse().setHeader("ETag", "\"two\"").setBody("abcdef"))
        http.download(UpdateHttp.DownloadRequest("updates/staging/test.apk", file, 6, 3, "\"one\""), { _, _ -> }, {})
        assertEquals("abcdef", file.readText())
    }

    @Test fun unexpectedEtagOnPartialResponseDoesNotAppend() {
        val file = temporary.newFile().apply { writeText("abc") }
        server.enqueue(MockResponse().setResponseCode(206).setHeader("ETag", "\"two\"")
            .setHeader("Content-Range", "bytes 3-5/6").setBody("def"))
        assertThrows(IllegalStateException::class.java) {
            http.download(UpdateHttp.DownloadRequest("updates/staging/test.apk", file, 6, 3, "\"one\""), { _, _ -> }, {})
        }
        assertEquals("abc", file.readText())
    }

    @Test fun interruptedDownloadKeepsBytesForRetry() {
        val file = temporary.newFile()
        server.enqueue(MockResponse().setBody("x".repeat(32768)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertThrows(java.io.IOException::class.java) {
            http.download(UpdateHttp.DownloadRequest("updates/staging/test.apk", file, 32768, 0, null), { _, _ -> }, {})
        }
        assertTrue(file.length() in 1..32767)
    }

    @Test fun cancelKeepsPartialDownload() {
        val file = temporary.newFile()
        server.enqueue(MockResponse().setBody("x".repeat(131072)).throttleBody(8192, 10, java.util.concurrent.TimeUnit.MILLISECONDS))
        assertThrows(java.io.IOException::class.java) {
            http.download(UpdateHttp.DownloadRequest("updates/staging/test.apk", file, 131072, 0, null),
                { done, _ -> if (done > 0) http.cancelDownload() }, {})
        }
        assertTrue(file.length() in 1..131071)
    }

    @Test fun redirectsAreNotFollowed() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://localhost/insecure"))
        assertThrows(IllegalStateException::class.java) { http.manifest() }
        assertEquals(1, server.requestCount)
    }

    @Test fun crossChannelPathIsRejectedBeforeNetwork() {
        assertThrows(IllegalArgumentException::class.java) {
            http.download(UpdateHttp.DownloadRequest("updates/production/test.apk", temporary.newFile(), 6, 0, null), { _, _ -> }, {})
        }
        assertEquals(0, server.requestCount)
    }
}

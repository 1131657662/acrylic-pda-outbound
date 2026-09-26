package com.acrylic.outbound.pda.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PdaNetworkFailureTest {
    @Test fun dnsFailureShowsSafeChineseMessageWithoutRawHost() {
        val client = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException(hostname)
        }).build()
        val network = PdaNetwork("https://outbound-test.nbsmzwl.cn/api/v1/", client = client)
        val failure = assertThrows(PdaTransportFailure::class.java) { network.request("pda/orders") }
        assertTrue(failure.message!!.contains("无法解析服务器地址"))
        assertFalse(failure.message!!.contains("outbound-test.nbsmzwl.cn"))
    }

    @Test fun lostPostResponseDoesNotRetryWriteWithAnotherRequest() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val network = PdaNetwork(server.url("/api/v1/").toString())
            val failure = assertThrows(PdaTransportFailure::class.java) {
                network.request("pda/orders/one/assignments", JSONObject().put("expectedVersion", "1"))
            }
            assertTrue(failure.message!!.contains("结果未知"))
            assertEquals(1, server.requestCount)
        }
    }
}

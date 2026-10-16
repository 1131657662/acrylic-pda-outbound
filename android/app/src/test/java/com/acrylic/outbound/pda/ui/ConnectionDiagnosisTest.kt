package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.network.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ConnectionDiagnosisTest {
    @Test fun successfulHttpReadReportsOnlyConnectionAndIdentity() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"OK","serviceMode":"NORMAL","data":{}}"""))
            val envelope = PdaNetwork(server.url("/").toString()).request("pda/devices/me")
            assertEquals("NORMAL", envelope.getString("serviceMode"))
            assertEquals("GET", server.takeRequest().method)
            assertTrue(ConnectionDiagnosis.success().summary.contains("订单结果与作业资格仍按原单核验"))
        }
    }
    @Test fun realForbiddenHttpRetainsOriginalCodeOutsideSummary() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"PERMISSION_DENIED","message":"isolated permission detail"}"""))
            val failure = runCatching { PdaNetwork(server.url("/").toString()).request("pda/devices/me") }.exceptionOrNull() as ApiFailure
            val diagnosis = ConnectionDiagnosis.failure(failure)
            assertTrue(diagnosis.summary.contains("当前身份无权"))
            assertFalse(diagnosis.summary.contains("PERMISSION_DENIED"))
            assertTrue(diagnosis.details.contains("HTTP 403"))
            assertTrue(diagnosis.details.contains("isolated permission detail"))
        }
    }
    @Test fun transportCausesExplainNextActionWithoutClaimingWifiDisconnected() {
        val cases = listOf(UnknownHostException() to "DNS", ConnectException() to "未能连接后台",
            SocketTimeoutException() to "检查超时", SSLHandshakeException("untrusted") to "勿绕过证书校验",
            IOException() to "连接或传输中断")
        for ((cause, expected) in cases) {
            val diagnosis = ConnectionDiagnosis.failure(PdaTransportFailure("transport", cause))
            assertTrue(diagnosis.summary, diagnosis.summary.contains(expected))
            assertTrue(diagnosis.summary.contains("原作业记录保留"))
            assertFalse(diagnosis.summary.contains("Wi-Fi已断开"))
        }
    }
    @Test fun recoveryResponseKeepsOriginalWorkStopped() {
        val diagnosis = ConnectionDiagnosis.failure(ApiFailure("SERVER_RECOVERY_REQUIRED", "recovery", httpStatus = 503))
        assertTrue(diagnosis.summary.contains("保持原作业停止"))
        assertFalse(diagnosis.summary.contains("服务暂不能"))
    }
    @Test fun identityAndServerFailureRemainDistinct() {
        assertTrue(ConnectionDiagnosis.failure(ApiFailure("AUTH_EXPIRED", "expired", httpStatus = 401)).summary.contains("身份不可用"))
        assertTrue(ConnectionDiagnosis.failure(ApiFailure("INTERNAL_ERROR", "service", httpStatus = 503)).summary.contains("服务暂不能"))
        assertTrue(ConnectionDiagnosis.failure(ApiFailure("BUSY", "busy", httpStatus = 429)).summary.contains("检查过于频繁"))
    }
    @Test fun unknownFailureDoesNotInventNetworkCause() {
        val diagnosis = ConnectionDiagnosis.failure(IllegalStateException("unclassified-original"))
        assertTrue(diagnosis.summary.contains("原因尚待核对"))
        assertTrue(diagnosis.details.contains("unclassified-original"))
        assertTrue(ConnectionDiagnosis.signedOut().summary.contains("请先返回人员登录"))
    }
}

package com.logforge.external.client

import com.logforge.domain.entity.Tenant
import com.logforge.domain.entity.TenantStatus
import com.logforge.domain.service.TenantService
import com.logforge.external.config.ExternalClientProperties
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class DefaultExternalLogClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: ExternalLogClient
    private lateinit var tenantService: TenantService
    private lateinit var properties: ExternalClientProperties

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer().also { it.start() }
        tenantService = Mockito.mock(TenantService::class.java)
        initClient(defaultProperties())
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `정상 응답을 수신하면 로그 리스트를 반환한다`() = runBlocking {
        val responseJson = """
            [
              {"occurredAt":"2025-01-01T00:00:00Z","payload":"p1"},
              {"occurredAt":"2025-01-01T01:00:00Z","payload":"p2"}
            ]
        """.trimIndent()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson)
        )
        stubTenant(baseUrl = mockWebServer.url("/").toString(), apiKey = "secret-key")

        val result = client.fetchLogs("1", "fromTs", "toTs")

        assertEquals(2, result.size)
        val recorded = mockWebServer.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/logs?from=fromTs&to=toTs", recorded.path)
        assertEquals("secret-key", recorded.getHeader("X-API-KEY"))
    }

    @Test
    fun `서버 오류 발생 시 재시도 후 성공하면 결과를 반환한다`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"occurredAt":"2025-01-01T00:00:00Z","payload":"p1"}]""")
        )
        stubTenant(baseUrl = mockWebServer.url("/").toString(), apiKey = "retry-key")

        val result = client.fetchLogs("1", "fromTs", "toTs")

        assertEquals(1, result.size)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `연속 실패 시 ExternalLogClientException을 던진다`() {
        repeat(3) {
            mockWebServer.enqueue(MockResponse().setResponseCode(503))
        }
        stubTenant(baseUrl = mockWebServer.url("/").toString(), apiKey = "fail-key")

        assertThrows(ExternalLogClientException::class.java) {
            runBlocking {
                client.fetchLogs("1", "fromTs", "toTs")
            }
        }
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `응답 지연이 타임아웃을 초과하면 TimeoutCancellationException을 던진다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"occurredAt":"2025-01-01T00:00:00Z","payload":"p1"}]""")
                .setBodyDelay(1500, TimeUnit.MILLISECONDS)
        )
        initClient(
            properties = properties.copy(
                requestTimeoutMillis = 500,
                responseTimeoutMillis = 500
            )
        )
        stubTenant(baseUrl = mockWebServer.url("/").toString(), apiKey = "timeout-key")

        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                client.fetchLogs("1", "fromTs", "toTs")
            }
        }
    }

    /**
     * MockWebServer 주소를 사용하는 테넌트 스텁을 TenantService에 주입한다.
     */
    private fun stubTenant(baseUrl: String, apiKey: String) {
        val tenant = Tenant(
            name = "tenant-1",
            status = TenantStatus.ACTIVE,
            externalApiBaseUrl = baseUrl,
            apiKey = apiKey
        ).apply {
            id = 1L
        }
        Mockito.`when`(tenantService.getActiveTenant("1"))
            .thenReturn(tenant)
    }

    /**
     * 테스트마다 필요한 설정으로 클라이언트를 재구성한다.
     */
    private fun initClient(properties: ExternalClientProperties) {
        this.properties = properties
        val semaphore = Semaphore(properties.maxConcurrentRequests)
        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(properties.responseTimeoutMillis))
        val webClient = WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
        client = DefaultExternalLogClient(webClient, tenantService, properties, semaphore)
    }

    /**
     * 기본 ExternalClientProperties 스텁
     */
    private fun defaultProperties() = ExternalClientProperties(
        maxConcurrentRequests = 2,
        requestTimeoutMillis = 3_000,
        retryAttempts = 3,
        retryDelayMillis = 50,
        connectTimeoutMillis = 1_000,
        responseTimeoutMillis = 3_000
    )
}

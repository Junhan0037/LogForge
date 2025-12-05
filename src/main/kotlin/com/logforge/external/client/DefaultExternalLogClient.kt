package com.logforge.external.client

import com.logforge.domain.service.TenantService
import com.logforge.external.config.ExternalClientProperties
import com.logforge.external.dto.ExternalLogDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * 외부 로그 API 호출의 기본 구현체
 * - 테넌트별 엔드포인트와 API KEY를 헤더에 추가해 호출
 * - 세마포어/타임아웃/재시도 정책으로 안정성을 확보
 */
@Component
class DefaultExternalLogClient(
    private val webClient: WebClient,
    private val tenantService: TenantService,
    private val properties: ExternalClientProperties,
    private val semaphore: Semaphore
) : ExternalLogClient {

    private val logger = LoggerFactory.getLogger(DefaultExternalLogClient::class.java)

    /**
     * 지정된 기간(from~to)의 로그를 외부 API에서 조회
     * - 동시 호출 제한과 요청 타임아웃, 간단한 재시도를 적용해 네트워크 안정성을 확보
     */
    override suspend fun fetchLogs(tenantId: String, from: String, to: String): List<ExternalLogDto> {
        val tenant = tenantService.getActiveTenant(tenantId)
        val requestUri = buildLogRequestUri(tenant.externalApiBaseUrl, from, to)

        return semaphore.withPermit {
            withTimeout(properties.requestTimeoutMillis) {
                executeWithRetry {
                    webClient.get()
                        .uri(requestUri)
                        .header("X-API-KEY", tenant.apiKey)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError) { response ->
                            response.createException().map { ex ->
                                ExternalLogClientException("외부 API 호출 실패(status=${response.statusCode()})", ex)
                            }
                        }
                        .bodyToFlux<ExternalLogDto>()
                        .collectList()
                        .awaitSingle()
                }
            }
        }
    }

    /**
     * API 호출용 URI 생성
     */
    private fun buildLogRequestUri(baseUrl: String, from: String, to: String): URI =
        UriComponentsBuilder
            .fromUriString(baseUrl.trimEnd('/'))
            .path("/logs")
            .queryParam("from", from)
            .queryParam("to", to)
            .build(true)
            .toUri()

    /**
     * 간단한 선형 재시도 로직
     */
    private suspend fun <T> executeWithRetry(action: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(properties.retryAttempts) { attempt ->
            try {
                return action()
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                lastException = ex
                logger.warn("외부 로그 API 호출 실패 - attempt={} / retryAttempts={}", attempt + 1, properties.retryAttempts, ex)
                if (attempt < properties.retryAttempts - 1) {
                    delay(properties.retryDelayMillis)
                }
            }
        }
        throw ExternalLogClientException("외부 로그 API 호출 실패(재시도 초과)", lastException)
    }
}

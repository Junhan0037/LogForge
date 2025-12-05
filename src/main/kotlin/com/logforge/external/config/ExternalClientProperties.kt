package com.logforge.external.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 외부 로그 API 호출에 사용되는 공통 속성
 * - 멀티테넌트 환경에서 동일한 호출 정책을 적용하기 위해 설정 기반으로 주입
 */
@ConfigurationProperties(prefix = "external.client")
data class ExternalClientProperties(
    val maxConcurrentRequests: Int = 5,
    val requestTimeoutMillis: Long = 5_000,
    val retryAttempts: Int = 3,
    val retryDelayMillis: Long = 200,
    val connectTimeoutMillis: Int = 3_000,
    val responseTimeoutMillis: Long = 5_000
) {
    init {
        require(maxConcurrentRequests > 0) { "maxConcurrentRequests는 1 이상이어야 합니다." }
        require(requestTimeoutMillis > 0) { "requestTimeoutMillis는 1 이상이어야 합니다." }
        require(retryAttempts > 0) { "retryAttempts는 1 이상이어야 합니다." }
        require(retryDelayMillis >= 0) { "retryDelayMillis는 0 이상이어야 합니다." }
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis는 1 이상이어야 합니다." }
        require(responseTimeoutMillis > 0) { "responseTimeoutMillis는 1 이상이어야 합니다." }
    }
}

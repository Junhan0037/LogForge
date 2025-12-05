package com.logforge.external.client

/**
 * 외부 로그 API 호출 중 발생한 도메인 예외
 */
class ExternalLogClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

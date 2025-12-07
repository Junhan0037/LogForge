package com.logforge.batch.normalize

/**
 * RawLog를 NormalizedEvent로 변환하는 과정에서 발생하는 도메인 예외
 * - 스킵 정책에서 식별 가능하도록 런타임 예외로 정의
 */
class LogNormalizeException(
    val rawLogId: Long?,
    val tenantId: String,
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

package com.logforge.external.dto

import java.time.Instant

/**
 * 외부 원천 로그를 표현하는 DTO
 * - RawLog 적재 단계에서 그대로 저장해 재처리/정제에 활용
 */
data class ExternalLogDto(
    val occurredAt: Instant,
    val payload: String
)

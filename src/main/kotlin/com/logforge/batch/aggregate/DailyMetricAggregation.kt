package com.logforge.batch.aggregate

import java.math.BigDecimal
import java.time.LocalDate

/**
 * NormalizedEvent를 테넌트/일자/이벤트 타입 기준으로 그룹핑한 집계 결과를 담는 DTO
 * - Reader에서 DB 집계 결과를 받아 Processor/Writer로 전달하기 위한 중간 모델
 */
data class DailyMetricAggregation(
    val tenantId: String,
    val eventDate: LocalDate,
    val eventType: String,
    val eventCount: Long,
    val amountSum: BigDecimal
)

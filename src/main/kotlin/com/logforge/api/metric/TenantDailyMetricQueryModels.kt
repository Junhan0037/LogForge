package com.logforge.api.metric

import com.logforge.domain.entity.TenantDailyMetric
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.LocalDate

/**
 * TenantDailyMetric 조회 요청 파라미터 객체
 * - 날짜 범위를 사전 검증해 잘못된 요청을 초기 단계에서 차단
 */
data class TenantDailyMetricQueryRequest(
    val tenantId: String,
    val fromDate: LocalDate?,
    val toDate: LocalDate?,
    val eventTypes: List<String>,
    val pageable: Pageable
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId는 필수입니다." }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw IllegalArgumentException("fromDate는 toDate보다 이후일 수 없습니다.")
        }
    }
}

/**
 * TenantDailyMetric REST 응답 DTO
 * - 엔티티 직접 노출을 피하고 API 응답 모델을 명확히 분리
 */
data class TenantDailyMetricResponse(
    val tenantId: String,
    val eventDate: LocalDate,
    val eventType: String,
    val eventCount: Long,
    val amountSum: BigDecimal
) {
    companion object {
        /**
         * TenantDailyMetric 엔티티를 API 응답으로 변환
         */
        fun from(entity: TenantDailyMetric): TenantDailyMetricResponse =
            TenantDailyMetricResponse(
                tenantId = entity.tenantId,
                eventDate = entity.eventDate,
                eventType = entity.eventType,
                eventCount = entity.eventCount,
                amountSum = entity.amountSum
            )
    }
}

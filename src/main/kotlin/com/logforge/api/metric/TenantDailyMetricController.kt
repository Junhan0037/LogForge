package com.logforge.api.metric

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * TenantDailyMetric 조회용 REST 컨트롤러
 * - 테넌트/날짜 범위/이벤트 타입 필터와 페이지네이션을 지원
 */
@RestController
@RequestMapping("/api/metrics")
class TenantDailyMetricController(
    private val tenantDailyMetricQueryService: TenantDailyMetricQueryService
) {

    /**
     * 일별 집계 데이터 조회 엔드포인트
     * - eventType은 다중 값 전달을 허용하며, 공백/중복을 정규화해 필터링
     */
    @GetMapping("/daily")
    fun getTenantDailyMetrics(
        @RequestParam tenantId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: LocalDate?,
        @RequestParam(name = "eventType", required = false) eventTypes: List<String>?,
        @PageableDefault(size = 20, sort = ["eventDate"], direction = Sort.Direction.DESC) pageable: Pageable
    ): Page<TenantDailyMetricResponse> {
        val sanitizedEventTypes = eventTypes
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

        val request = TenantDailyMetricQueryRequest(
            tenantId = tenantId,
            fromDate = fromDate,
            toDate = toDate,
            eventTypes = sanitizedEventTypes,
            pageable = pageable
        )

        return tenantDailyMetricQueryService.findMetrics(request)
            .map { TenantDailyMetricResponse.from(it) }
    }
}

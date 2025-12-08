package com.logforge.api.metric

import com.logforge.domain.entity.TenantDailyMetric
import com.logforge.domain.repository.TenantDailyMetricRepository
import com.logforge.domain.service.TenantService
import org.springframework.data.domain.Page
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * TenantDailyMetric 조회 비즈니스 로직을 담당하는 서비스
 * - 활성 테넌트 검증 후 Specification 조합으로 필터링된 페이지 결과를 조회
 */
@Service
class TenantDailyMetricQueryService(
    private val tenantDailyMetricRepository: TenantDailyMetricRepository,
    private val tenantService: TenantService
) {

    /**
     * 테넌트/기간/이벤트 타입 조건에 맞는 집계 데이터를 페이지네이션하여 반환
     */
    @Transactional(readOnly = true)
    fun findMetrics(request: TenantDailyMetricQueryRequest): Page<TenantDailyMetric> {
        tenantService.getActiveTenant(request.tenantId)

        val specifications = mutableListOf(tenantEquals(request.tenantId))
        fromDateGoe(request.fromDate)?.let { specifications.add(it) }
        toDateLoe(request.toDate)?.let { specifications.add(it) }
        eventTypeIn(request.eventTypes)?.let { specifications.add(it) }

        val specification = specifications.reduce { acc, spec -> acc.and(spec) }

        return tenantDailyMetricRepository.findAll(specification, request.pageable)
    }

    /**
     * 필수 조건인 tenantId 일치 사양
     */
    private fun tenantEquals(tenantId: String): Specification<TenantDailyMetric> =
        Specification { root, _, cb -> cb.equal(root.get<String>("tenantId"), tenantId) }

    /**
     * fromDate 이상 필터
     */
    private fun fromDateGoe(fromDate: LocalDate?): Specification<TenantDailyMetric>? =
        fromDate?.let { date ->
            Specification { root, _, cb -> cb.greaterThanOrEqualTo(root.get("eventDate"), date) }
        }

    /**
     * toDate 이하 필터
     */
    private fun toDateLoe(toDate: LocalDate?): Specification<TenantDailyMetric>? =
        toDate?.let { date ->
            Specification { root, _, cb -> cb.lessThanOrEqualTo(root.get("eventDate"), date) }
        }

    /**
     * eventType 목록 필터
     */
    private fun eventTypeIn(eventTypes: List<String>): Specification<TenantDailyMetric>? =
        if (eventTypes.isEmpty()) {
            null
        } else {
            Specification { root, _, _ -> root.get<String>("eventType").`in`(eventTypes) }
        }
}

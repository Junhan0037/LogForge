package com.logforge.domain.repository

import com.logforge.domain.entity.TenantDailyMetric
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.LocalDate

interface TenantDailyMetricRepository : JpaRepository<TenantDailyMetric, Long>, JpaSpecificationExecutor<TenantDailyMetric> {

    /**
     * 멀티 테넌트/기간/이벤트 타입 범위에 해당하는 기존 집계 데이터를 한 번에 로딩해 upsert 시 추가 조회를 최소화
     */
    fun findAllByTenantIdInAndEventDateBetweenAndEventTypeIn(
        tenantIds: Collection<String>,
        from: LocalDate,
        to: LocalDate,
        eventTypes: Collection<String>
    ): List<TenantDailyMetric>
}

package com.logforge.domain.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 테넌트/이벤트 타입/일자 기준 집계 데이터를 보관
 * - 고유 제약을 통해 동일 기간 재실행 시 중복 적재를 방지(idempotent)
 */
@Entity
@Table(
    name = "tenant_daily_metrics",
    indexes = [
        Index(name = "idx_tenant_daily_metrics_tenant_date", columnList = "tenant_id, event_date"),
        Index(name = "idx_tenant_daily_metrics_event_type", columnList = "event_type")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_tenant_daily_metrics_tenant_date_type",
            columnNames = ["tenant_id", "event_date", "event_type"]
        )
    ]
)
class TenantDailyMetric(
    @Column(name = "tenant_id", nullable = false, length = 50)
    var tenantId: String,

    @Column(name = "event_date", nullable = false)
    var eventDate: LocalDate,

    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,

    @Column(name = "event_count", nullable = false)
    var eventCount: Long,

    @Column(name = "amount_sum", precision = 18, scale = 2, nullable = false)
    var amountSum: BigDecimal,
) : AuditableEntity()

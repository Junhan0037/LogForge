package com.logforge.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * 정제된 이벤트 엔티티
 * - 원본 로그를 파싱/검증해 표준화된 스키마로 저장
 * - 테넌트/이벤트 시각/타입 기준 조회가 빈번하므로 인덱스를 설정
 */
@Entity
@Table(
    name = "normalized_events",
    indexes = [
        Index(name = "idx_normalized_events_tenant_time", columnList = "tenant_id, event_time"),
        Index(name = "idx_normalized_events_event_type", columnList = "event_type")
    ]
)
class NormalizedEvent(
    @Column(name = "tenant_id", nullable = false, length = 50)
    var tenantId: String,

    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,

    @Column(name = "event_time", nullable = false)
    var eventTime: Instant,

    @Column(name = "user_id", length = 100)
    var userId: String? = null,

    @Column(name = "session_id", length = 150)
    var sessionId: String? = null,

    @Column(name = "amount", precision = 18, scale = 2)
    var amount: BigDecimal? = null,

    @Lob
    @Column(name = "metadata_json")
    var metadataJson: String? = null,
) : AuditableEntity()

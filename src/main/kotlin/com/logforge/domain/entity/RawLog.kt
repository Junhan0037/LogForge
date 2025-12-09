package com.logforge.domain.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 외부에서 수집한 원천 로그를 저장하는 엔티티
 * - payloadJson은 원본을 그대로 보존해 정제/재처리에 사용
 * - 테넌트와 발생 시각으로 조회 효율을 위해 인덱스를 추가
 */
@Entity
@Table(
    name = "raw_logs",
    indexes = [
        Index(name = "idx_raw_logs_tenant_occurred", columnList = "tenant_id, occurred_at")
    ]
)
class RawLog(
    @Column(name = "tenant_id", nullable = false, length = 50)
    var tenantId: String,

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant,

    @Lob
    @Column(name = "payload_json", nullable = false)
    var payloadJson: String,

    @Column(name = "ingested_at", nullable = false)
    var ingestedAt: Instant = Instant.now(),
) : AuditableEntity()

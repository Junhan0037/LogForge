package com.logforge.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table

/**
 * 정규화/처리 실패한 로그를 별도 보관하여 재처리 및 원인 분석에 사용
 */
@Entity
@Table(
    name = "failed_logs",
    indexes = [
        Index(name = "idx_failed_logs_tenant", columnList = "tenant_id")
    ]
)
class FailedLog(
    @Column(name = "tenant_id", nullable = false, length = 50)
    var tenantId: String,

    @Column(name = "raw_log_id")
    var rawLogId: Long? = null,

    @Column(name = "reason", nullable = false, length = 500)
    var reason: String,

    @Lob
    @Column(name = "payload_json", nullable = false)
    var payloadJson: String,
) : AuditableEntity()

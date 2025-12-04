package com.logforge.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 외부 로그 소스(테넌트) 정보를 보관하는 엔티티
 * - 테넌트명은 유니크하게 관리
 * - 활성/비활성 상태와 외부 API 접속 정보를 함께 저장
 */
@Entity
@Table(
    name = "tenants",
    indexes = [
        Index(name = "idx_tenants_name", columnList = "name", unique = true)
    ]
)
class Tenant(
    @Column(name = "name", nullable = false, unique = true, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TenantStatus,

    @Column(name = "external_api_base_url", nullable = false, length = 255)
    var externalApiBaseUrl: String,

    @Column(name = "api_key", nullable = false, length = 255)
    var apiKey: String,
) : AuditableEntity()

/**
 * 테넌트 운영 상태
 */
enum class TenantStatus {
    ACTIVE,
    INACTIVE
}

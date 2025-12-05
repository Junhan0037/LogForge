package com.logforge.domain.service

import com.logforge.domain.entity.Tenant

/**
 * 멀티테넌트 환경에서 테넌트 정보를 조회하는 서비스 인터페이스
 * - 배치 파티셔닝과 외부 API 호출에서 재사용할 수 있도록 활성 테넌트 기준 조회를 표준화
 */
interface TenantService {
    fun getActiveTenants(): List<Tenant>
    fun getActiveTenant(tenantId: String): Tenant
}

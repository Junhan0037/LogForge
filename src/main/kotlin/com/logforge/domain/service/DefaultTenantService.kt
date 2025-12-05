package com.logforge.domain.service

import com.logforge.domain.entity.Tenant
import com.logforge.domain.entity.TenantStatus
import com.logforge.domain.repository.TenantRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 테넌트 조회 규칙을 구현하는 기본 서비스
 * - tenantId는 테이블 pk(Long)을 문자열로 사용하는 것을 기본 정책으로 삼아, 컬럼 기반 멀티테넌트 식별자와 일치시킨다
 */
@Service
class DefaultTenantService(
    private val tenantRepository: TenantRepository
) : TenantService {

    /**
     * 활성 테넌트를 id 오름차순으로 정렬해 반환
     * - 배치 파티셔닝 시 shard 순서를 일정하게 유지
     */
    @Transactional(readOnly = true)
    override fun getActiveTenants(): List<Tenant> =
        tenantRepository.findByStatus(TenantStatus.ACTIVE).sortedBy { it.id ?: Long.MAX_VALUE }

    /**
     * tenantId(문자열) → Long pk 변환 후 활성 상태를 검증
     * - 변환 실패: InvalidTenantIdentifierException
     * - 미존재: TenantNotFoundException
     * - 비활성: InactiveTenantException
     */
    @Transactional(readOnly = true)
    override fun getActiveTenant(tenantId: String): Tenant {
        val tenantKey = tenantId.toLongOrNull() ?: throw InvalidTenantIdentifierException(tenantId)
        val tenant = tenantRepository.findByIdOrNull(tenantKey) ?: throw TenantNotFoundException(tenantId)

        if (tenant.status != TenantStatus.ACTIVE) {
            throw InactiveTenantException(tenantId)
        }

        return tenant
    }
}

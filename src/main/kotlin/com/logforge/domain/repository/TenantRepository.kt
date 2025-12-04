package com.logforge.domain.repository

import com.logforge.domain.entity.Tenant
import com.logforge.domain.entity.TenantStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<Tenant, Long> {
    fun findByStatus(status: TenantStatus): List<Tenant>
}

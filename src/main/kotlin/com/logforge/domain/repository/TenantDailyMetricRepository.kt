package com.logforge.domain.repository

import com.logforge.domain.entity.TenantDailyMetric
import org.springframework.data.jpa.repository.JpaRepository

interface TenantDailyMetricRepository : JpaRepository<TenantDailyMetric, Long>

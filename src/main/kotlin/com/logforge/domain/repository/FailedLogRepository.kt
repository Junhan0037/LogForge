package com.logforge.domain.repository

import com.logforge.domain.entity.FailedLog
import org.springframework.data.jpa.repository.JpaRepository

interface FailedLogRepository : JpaRepository<FailedLog, Long>

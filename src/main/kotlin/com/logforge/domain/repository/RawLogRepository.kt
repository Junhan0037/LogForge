package com.logforge.domain.repository

import com.logforge.domain.entity.RawLog
import org.springframework.data.jpa.repository.JpaRepository

interface RawLogRepository : JpaRepository<RawLog, Long>

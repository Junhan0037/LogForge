package com.logforge.domain.repository

import com.logforge.domain.entity.NormalizedEvent
import org.springframework.data.jpa.repository.JpaRepository

interface NormalizedEventRepository : JpaRepository<NormalizedEvent, Long>

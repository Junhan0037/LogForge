package com.logforge.external.client

import com.logforge.external.dto.ExternalLogDto

/**
 * 외부 로그 소스에서 테넌트별 원시 로그를 수집하는 포트
 * - 코루틴 기반으로 구현해 I/O 병렬성을 확보
 */
interface ExternalLogClient {
    suspend fun fetchLogs(tenantId: String, from: String, to: String): List<ExternalLogDto>
}

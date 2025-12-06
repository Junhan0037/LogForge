package com.logforge.batch.fetch

import com.logforge.domain.entity.RawLog
import com.logforge.domain.repository.RawLogRepository
import com.logforge.external.dto.ExternalLogDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 외부에서 수집한 로그 DTO 리스트를 RawLog 엔티티로 변환해 저장하는 Writer
 * - 배치 Tasklet 내부에서 호출되어 테넌트별 적재를 담당
 */
@Component
class RawLogWriter(
    private val rawLogRepository: RawLogRepository
) {

    private val logger = LoggerFactory.getLogger(RawLogWriter::class.java)

    /**
     * 외부 로그 DTO를 RawLog 엔티티로 변환 후 일괄 저장
     * - 단일 트랜잭션으로 처리해 동일 테넌트의 수집 데이터를 일관되게 기록
     */
    @Transactional
    fun write(tenantId: String, logs: List<ExternalLogDto>) {
        if (logs.isEmpty()) {
            logger.info("수집 로그 없음 - tenantId={}, count=0", tenantId)
            return
        }

        val ingestedAt = Instant.now()
        val entities = logs.map { dto ->
            RawLog(
                tenantId = tenantId,
                occurredAt = dto.occurredAt,
                payloadJson = dto.payload,
                ingestedAt = ingestedAt
            )
        }

        rawLogRepository.saveAll(entities)
        logger.info("RawLog 저장 완료 - tenantId={}, count={}", tenantId, entities.size)
    }
}

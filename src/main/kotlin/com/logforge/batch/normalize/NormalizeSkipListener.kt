package com.logforge.batch.normalize

import com.logforge.domain.entity.FailedLog
import com.logforge.domain.entity.RawLog
import com.logforge.domain.repository.FailedLogRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.SkipListener
import org.springframework.stereotype.Component

/**
 * 정규화 실패 시 FailedLog 테이블에 원본과 사유를 기록하는 SkipListener
 */
@Component
class NormalizeSkipListener(
    private val failedLogRepository: FailedLogRepository
) : SkipListener<RawLog, Any> {

    private val logger = LoggerFactory.getLogger(NormalizeSkipListener::class.java)

    /**
     * Processor에서 Skip 발생 시 재처리 및 장애 분석을 위해 실패 사유를 축약 저장
     */
    override fun onSkipInProcess(item: RawLog, t: Throwable) {
        val reason = buildReason(t)
        failedLogRepository.save(
            FailedLog(
                tenantId = item.tenantId,
                rawLogId = item.id,
                reason = reason.take(MAX_REASON_LENGTH),
                payloadJson = item.payloadJson
            )
        )
        logger.warn("정규화 실패 - tenantId={}, rawLogId={}, reason={}", item.tenantId, item.id, reason)
    }

    /**
     * Reader 단계 Skip는 별도 처리 없이 로그 기록
     */
    override fun onSkipInRead(t: Throwable) {
        logger.warn("RawLog Reader 단계에서 Skip 발생: {}", t.message)
    }

    /**
     * Writer 단계 Skip도 로그로만 기록 (정규화 흐름은 Processor에서 주로 발생)
     */
    override fun onSkipInWrite(item: Any, t: Throwable) {
        logger.warn("NormalizedEvent Writer 단계에서 Skip 발생: {}", t.message)
    }

    private fun buildReason(t: Throwable): String =
        when (t) {
            is LogNormalizeException -> t.message
            else -> "정규화 중 알 수 없는 오류: ${t.message}"
        }

    companion object {
        private const val MAX_REASON_LENGTH = 500
    }
}

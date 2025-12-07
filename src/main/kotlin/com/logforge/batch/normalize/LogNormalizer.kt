package com.logforge.batch.normalize

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.logforge.domain.entity.NormalizedEvent
import com.logforge.domain.entity.RawLog
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant

/**
 * RawLog에 포함된 payloadJson을 표준화된 NormalizedEvent로 변환하는 변환기
 * - 필수 필드(eventType) 누락 시 LogNormalizeException을 발생시켜 Skip 처리
 * - eventTime이 없으면 원본 occurredAt을 대체값으로 사용해 데이터 손실을 방지
 */
@Component
class LogNormalizer(
    private val objectMapper: ObjectMapper
) {

    /**
     * RawLog -> NormalizedEvent 변환
     */
    fun normalize(rawLog: RawLog): NormalizedEvent {
        val payload = parsePayload(rawLog)
        val eventType = payload.eventType?.takeIf { it.isNotBlank() }
            ?: throw LogNormalizeException(rawLog.id, rawLog.tenantId, "payload에 eventType이 존재하지 않습니다.")

        val eventTime = payload.eventTime ?: rawLog.occurredAt
        val metadataJson = payload.metadata?.let { objectMapper.writeValueAsString(it) }

        return NormalizedEvent(
            tenantId = rawLog.tenantId,
            eventType = eventType,
            eventTime = eventTime,
            userId = payload.userId?.takeIf { it.isNotBlank() },
            sessionId = payload.sessionId?.takeIf { it.isNotBlank() },
            amount = payload.amount,
            metadataJson = metadataJson
        )
    }

    /**
     * payloadJson을 안전하게 역직렬화하고, 실패 시 Skip용 예외 처리
     */
    private fun parsePayload(rawLog: RawLog): NormalizablePayload =
        try {
            objectMapper.readValue(rawLog.payloadJson, NormalizablePayload::class.java)
        } catch (ex: Exception) {
            throw LogNormalizeException(rawLog.id, rawLog.tenantId, "payload JSON 파싱에 실패했습니다.", ex)
        }
}

/**
 * 정규화 대상 RawLog payload 모델
 * - snake_case/camelCase 모두 수용해 외부 시스템 포맷 차이를 흡수
 */
private data class NormalizablePayload(
    @JsonAlias("event_type", "eventType")
    val eventType: String?,
    @JsonAlias("event_time", "eventTime")
    val eventTime: Instant?,
    @JsonAlias("user_id", "userId")
    val userId: String?,
    @JsonAlias("session_id", "sessionId")
    val sessionId: String?,
    val amount: BigDecimal?,
    val metadata: JsonNode?
)

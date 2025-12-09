package com.logforge.batch.aggregate

import com.logforge.domain.entity.TenantDailyMetric
import com.logforge.domain.repository.TenantDailyMetricRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 일별 집계 결과를 TenantDailyMetric에 병합 저장하는 Writer
 * - 고유 키(tenantId, eventDate, eventType) 기준 upsert로 재실행 시에도 중복 없이 idempotent를 보장
 */
@Component
class TenantDailyMetricUpsertWriter(
    private val tenantDailyMetricRepository: TenantDailyMetricRepository
) : ItemWriter<TenantDailyMetric> {

    private val logger = LoggerFactory.getLogger(TenantDailyMetricUpsertWriter::class.java)

    override fun write(chunk: Chunk<out TenantDailyMetric>) {
        if (chunk.isEmpty()) {
            return
        }

        val tenantIds = chunk.map { it.tenantId }.toSet()
        val eventTypes = chunk.map { it.eventType }.toSet()
        val minDate = chunk.minOf { it.eventDate }
        val maxDate = chunk.maxOf { it.eventDate }

        // 동일 키에 대한 기존 레코드를 선조회, upsert 시 추가 쿼리를 줄이고 충돌을 회피
        val existingMetrics = tenantDailyMetricRepository
            .findAllByTenantIdInAndEventDateBetweenAndEventTypeIn(tenantIds, minDate, maxDate, eventTypes)
            .associateBy { MetricKey(it.tenantId, it.eventDate, it.eventType) }

        val upserts = chunk.map { metric ->
            val key = MetricKey(metric.tenantId, metric.eventDate, metric.eventType)
            val existing = existingMetrics[key]

            if (existing != null) {
                existing.eventCount = metric.eventCount
                existing.amountSum = metric.amountSum
                existing
            } else {
                TenantDailyMetric(
                    tenantId = metric.tenantId,
                    eventDate = metric.eventDate,
                    eventType = metric.eventType,
                    eventCount = metric.eventCount,
                    amountSum = metric.amountSum
                )
            }
        }

        tenantDailyMetricRepository.saveAll(upserts)
        logger.debug(
            "TenantDailyMetric upsert 완료 - batchSize={}, tenantCount={}, eventTypeCount={}",
            chunk.size(),
            tenantIds.size,
            eventTypes.size
        )
    }

    /**
     * TenantDailyMetric 고유 키 표현
     */
    private data class MetricKey(
        val tenantId: String,
        val eventDate: LocalDate,
        val eventType: String
    )
}

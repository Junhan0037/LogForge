package com.logforge.batch.aggregate

import com.logforge.batch.monitoring.BatchMetricsListener
import com.logforge.domain.entity.TenantDailyMetric
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersValidator
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.DefaultJobParametersValidator
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.batch.item.database.Order
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.sql.Timestamp
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.sql.DataSource

/**
 * NormalizedEvent를 일별/타입별로 집계해 TenantDailyMetric으로 저장하는 배치 잡 구성
 * - from/to Job Parameter로 처리 범위를 제어하고, 집계 쿼리를 Reader 단계에서 수행
 */
@Configuration
class AggregateDailyMetricsJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSource: DataSource,
    private val tenantDailyMetricUpsertWriter: TenantDailyMetricUpsertWriter,
    private val batchMetricsListener: BatchMetricsListener,
    @Value("\${batch.aggregate-daily-metrics.chunk-size:500}") private val chunkSize: Int
) {

    private val logger = LoggerFactory.getLogger(AggregateDailyMetricsJobConfig::class.java)

    init {
        require(chunkSize > 0) { "batch.aggregate-daily-metrics.chunk-size는 1 이상이어야 합니다." }
    }

    @Bean
    fun aggregateDailyMetricsJob(): Job =
        JobBuilder("aggregateDailyMetricsJob", jobRepository)
            .listener(batchMetricsListener)
            .incrementer(RunIdIncrementer())
            .validator(aggregateDailyMetricsJobParametersValidator())
            .start(aggregateDailyMetricsStep())
            .build()

    /**
     * 필수 Job Parameter 검증기 (from/to)
     */
    @Bean
    fun aggregateDailyMetricsJobParametersValidator(): JobParametersValidator =
        DefaultJobParametersValidator(arrayOf("from", "to"), emptyArray())

    /**
     * NormalizedEvent를 집계 후 TenantDailyMetric으로 upsert하는 Chunk 스텝
     */
    @Bean
    fun aggregateDailyMetricsStep(): Step =
        StepBuilder("aggregateDailyMetricsStep", jobRepository)
            .chunk<DailyMetricAggregation, TenantDailyMetric>(chunkSize, transactionManager)
            .reader(dailyMetricAggregationReader(null, null))
            .processor(dailyMetricAggregationProcessor())
            .writer(tenantDailyMetricUpsertWriter)
            .listener(batchMetricsListener)
            .build()

    /**
     * 집계된 NormalizedEvent 레코드를 페이징 조회하는 Reader
     * - DB 레벨에서 group by로 집계를 수행해 메모리 사용을 최소화
     */
    @Bean
    @StepScope
    fun dailyMetricAggregationReader(
        @Value("#{jobParameters['from']}") from: String?,
        @Value("#{jobParameters['to']}") to: String?
    ): JdbcPagingItemReader<DailyMetricAggregation> {
        val timeRange = extractTimeRange(from, to)
        logger.info("집계 대상 NormalizedEvent 조회 범위 - from={}, to={}", timeRange.from, timeRange.to)

        return JdbcPagingItemReaderBuilder<DailyMetricAggregation>()
            .name("dailyMetricAggregationReader")
            .dataSource(dataSource)
            .pageSize(chunkSize)
            .rowMapper { rs, _ ->
                DailyMetricAggregation(
                    tenantId = rs.getString("tenant_id"),
                    eventDate = rs.getDate("event_date").toLocalDate(),
                    eventType = rs.getString("event_type"),
                    eventCount = rs.getLong("event_count"),
                    amountSum = rs.getBigDecimal("amount_sum")
                )
            }
            .selectClause("tenant_id, CAST(event_time AS DATE) AS event_date, event_type, COUNT(*) AS event_count, COALESCE(SUM(amount), 0) AS amount_sum")
            .fromClause("from normalized_events")
            .whereClause("where event_time between :from and :to")
            .groupClause("group by tenant_id, CAST(event_time AS DATE), event_type")
            .sortKeys(
                mapOf(
                    "tenant_id" to Order.ASCENDING,
                    "event_date" to Order.ASCENDING,
                    "event_type" to Order.ASCENDING
                )
            )
            .parameterValues(
                mapOf(
                    "from" to timeRange.fromTimestamp,
                    "to" to timeRange.toTimestamp
                )
            )
            .build()
    }

    /**
     * Reader가 전달한 집계 DTO를 TenantDailyMetric 엔티티로 변환
     */
    @Bean
    fun dailyMetricAggregationProcessor(): ItemProcessor<DailyMetricAggregation, TenantDailyMetric> =
        ItemProcessor { aggregate ->
            TenantDailyMetric(
                tenantId = aggregate.tenantId,
                eventDate = aggregate.eventDate,
                eventType = aggregate.eventType,
                eventCount = aggregate.eventCount,
                amountSum = aggregate.amountSum
            )
        }

    private fun extractTimeRange(from: String?, to: String?): TimeRange {
        val fromInstant = parseInstant("from", from)
        val toInstant = parseInstant("to", to)
        require(!fromInstant.isAfter(toInstant)) { "Job parameter 'from'은 'to'보다 이후일 수 없습니다." }
        return TimeRange(fromInstant, toInstant)
    }

    private fun parseInstant(name: String, value: String?): Instant =
        try {
            Instant.parse(value ?: throw IllegalArgumentException("Job parameter '$name'는 필수입니다."))
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("Job parameter '$name'는 ISO-8601 형식이어야 합니다.", ex)
        }

    private data class TimeRange(
        val from: Instant,
        val to: Instant
    ) {
        val fromTimestamp: Timestamp = Timestamp.from(from)
        val toTimestamp: Timestamp = Timestamp.from(to)
    }
}

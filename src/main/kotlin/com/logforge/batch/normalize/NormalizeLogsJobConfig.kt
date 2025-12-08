package com.logforge.batch.normalize

import com.logforge.batch.monitoring.BatchMetricsListener
import com.logforge.domain.entity.NormalizedEvent
import com.logforge.domain.entity.RawLog
import jakarta.persistence.EntityManagerFactory
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
import org.springframework.batch.item.database.JpaPagingItemReader
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * RawLog를 NormalizedEvent로 정규화하는 Chunk 지향 배치 잡 구성
 * - from/to Job Parameter로 처리 구간을 제한해 재처리와 성능 튜닝에 용이
 * - 정규화 실패 건은 Skip 처리 후 FailedLog에 남겨 재분석 가능하게 유지
 */
@Configuration
class NormalizeLogsJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val entityManagerFactory: EntityManagerFactory,
    private val logNormalizer: LogNormalizer,
    private val normalizedEventWriter: NormalizedEventWriter,
    private val normalizeSkipListener: NormalizeSkipListener,
    private val batchMetricsListener: BatchMetricsListener,
    @Value("\${batch.normalize-logs.chunk-size:500}") private val chunkSize: Int
) {

    private val logger = LoggerFactory.getLogger(NormalizeLogsJobConfig::class.java)

    init {
        require(chunkSize > 0) { "batch.normalize-logs.chunk-size는 1 이상이어야 합니다." }
    }

    @Bean
    fun normalizeLogsJob(): Job =
        JobBuilder("normalizeLogsJob", jobRepository)
            .listener(batchMetricsListener)
            .incrementer(RunIdIncrementer())
            .validator(normalizeLogsJobParametersValidator())
            .start(normalizeLogsStep())
            .build()

    /**
     * 필수 Job Parameter 검증기
     * - from/to가 반드시 존재하도록 검증
     */
    @Bean
    fun normalizeLogsJobParametersValidator(): JobParametersValidator =
        DefaultJobParametersValidator(arrayOf("from", "to"), emptyArray())

    /**
     * RawLog를 Chunk 단위로 읽어 정규화하는 Step
     */
    @Bean
    fun normalizeLogsStep(): Step =
        StepBuilder("normalizeLogsStep", jobRepository)
            .chunk<RawLog, NormalizedEvent>(chunkSize, transactionManager)
            .reader(rawLogReader(null, null))
            .processor(normalizeProcessor())
            .writer(normalizedEventWriter)
            .faultTolerant()
            .skipLimit(1_000)
            .skip(LogNormalizeException::class.java)
            .listener(normalizeSkipListener)
            .listener(batchMetricsListener)
            .build()

    /**
     * Job Parameter 기반 RawLog Reader
     * - occurredAt이 from/to 구간에 포함된 로그만 조회
     * - StepScope로 파라미터를 주입받아 재사용 가능한 Reader 구성
     */
    @Bean
    @StepScope
    fun rawLogReader(
        @Value("#{jobParameters['from']}") from: String?,
        @Value("#{jobParameters['to']}") to: String?
    ): JpaPagingItemReader<RawLog> {
        val timeRange = extractTimeRange(from, to)
        logger.info("정규화 대상 RawLog 조회 범위 - from={}, to={}", timeRange.from, timeRange.to)

        return JpaPagingItemReaderBuilder<RawLog>()
            .name("rawLogReader")
            .entityManagerFactory(entityManagerFactory)
            .pageSize(chunkSize)
            .queryString("select r from RawLog r where r.occurredAt between :from and :to order by r.id")
            .parameterValues(mapOf("from" to timeRange.from, "to" to timeRange.to))
            .build()
    }

    /**
     * 정규화 Processor를 ItemProcessor 형태로 래핑해 StepBuilder에 제공
     */
    @Bean
    fun normalizeProcessor(): ItemProcessor<RawLog, NormalizedEvent> =
        ItemProcessor { rawLog ->
            logNormalizer.normalize(rawLog)
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
    )
}

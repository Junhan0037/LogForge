package com.logforge.batch.fetch

import com.logforge.batch.monitoring.BatchMetricsListener
import com.logforge.domain.service.TenantService
import com.logforge.external.client.ExternalLogClient
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersValidator
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.DefaultJobParametersValidator
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.partition.support.Partitioner
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * 테넌트별 원천 로그를 병렬로 수집하는 FetchRawLogs 배치 잡 구성
 * - Master/Worker 파티션 구조로 활성 테넌트를 병렬 처리
 * - from/to Job Parameter 유효성 검사와 로깅을 포함해 운영 시 추적성을 높인다
 */
@Configuration
class FetchRawLogsJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val tenantService: TenantService,
    private val externalLogClient: ExternalLogClient,
    private val rawLogWriter: RawLogWriter,
    private val batchMetricsListener: BatchMetricsListener,
    @Value("\${batch.fetch-raw-logs.grid-size:4}") private val gridSize: Int
) {

    private val logger = LoggerFactory.getLogger(FetchRawLogsJobConfig::class.java)

    init {
        require(gridSize > 0) { "batch.fetch-raw-logs.grid-size는 1 이상이어야 합니다." }
    }

    @Bean
    fun fetchRawLogsJob(): Job =
        JobBuilder("fetchRawLogsJob", jobRepository)
            .listener(batchMetricsListener)
            .incrementer(RunIdIncrementer())
            .validator(fetchRawLogsJobParametersValidator())
            .start(fetchRawLogsMasterStep())
            .build()

    /**
     * 필수 Job Parameter 검증기
     * - from/to가 반드시 존재하도록 선행 검증해 조기 실패를 유도
     */
    @Bean
    fun fetchRawLogsJobParametersValidator(): JobParametersValidator =
        DefaultJobParametersValidator(arrayOf("from", "to"), emptyArray())

    /**
     * 활성 테넌트 기반 마스터 스텝 (파티셔닝)
     */
    @Bean
    fun fetchRawLogsMasterStep(): Step =
        StepBuilder("fetchRawLogsMasterStep", jobRepository)
            .partitioner("fetchRawLogsWorkerStep", tenantPartitioner())
            .step(fetchRawLogsWorkerStep())
            .gridSize(gridSize)
            .taskExecutor(fetchRawLogsTaskExecutor())
            .listener(batchMetricsListener)
            .build()

    /**
     * 테넌트 단위 Worker 스텝
     * - 파티션 컨텍스트에서 tenantId를 읽고 외부 API 호출 후 RawLog로 저장
     */
    @Bean
    fun fetchRawLogsWorkerStep(): Step =
        StepBuilder("fetchRawLogsWorkerStep", jobRepository)
            .tasklet(workerTasklet(), transactionManager)
            .listener(batchMetricsListener)
            .build()

    /**
     * 파티션 컨텍스트 실행용 TaskExecutor
     * - gridSize와 동일한 동시 실행 상한을 부여해 테넌트별 병렬 수집 수를 제어
     */
    @Bean
    fun fetchRawLogsTaskExecutor(): TaskExecutor =
        SimpleAsyncTaskExecutor("fetch-raw-logs-").apply {
            concurrencyLimit = gridSize
        }

    @Bean
    fun tenantPartitioner(): Partitioner = TenantPartitioner(tenantService)

    /**
     * 테넌트 파티션별로 외부 로그를 동기적으로 수집하고 RawLog로 적재하는 Tasklet
     * - 파티션 컨텍스트에서 tenantId, JobParameter에서 기간을 추출
     * - 코루틴 기반 외부 호출을 runBlocking으로 감싸 트랜잭션 경계를 단일 스레드에서 유지
     */
    private fun workerTasklet(): Tasklet = Tasklet { _, chunkContext ->
        val tenantId = chunkContext.stepContext.stepExecutionContext["tenantId"] as? String ?: throw IllegalStateException("파티션 컨텍스트에 tenantId가 없습니다.")
        val timeRange = extractTimeRange(chunkContext.stepContext.jobParameters)

        logger.info("원천 로그 수집 시작 - tenantId={}, from={}, to={}", tenantId, timeRange.from, timeRange.to)

        runBlocking {
            // 코루틴 기반 외부 API 호출을 Tasklet 라이프사이클 내에서 동기적으로 수행해 트랜잭션 경계를 명확히 유지
            val logs = externalLogClient.fetchLogs(
                tenantId = tenantId,
                from = timeRange.from.toString(),
                to = timeRange.to.toString()
            )
            rawLogWriter.write(tenantId, logs)
        }

        logger.info("원천 로그 수집 완료 - tenantId={}, from={}, to={}", tenantId, timeRange.from, timeRange.to)

        RepeatStatus.FINISHED
    }

    /**
     * Job Parameter에서 기간(from/to)을 추출하고 ISO-8601 형식을 검증
     */
    private fun extractTimeRange(jobParameters: Map<String, Any?>): TimeRange {
        val fromRaw = jobParameters["from"] as? String ?: throw IllegalArgumentException("Job parameter 'from'은 필수입니다.")
        val toRaw = jobParameters["to"] as? String ?: throw IllegalArgumentException("Job parameter 'to'은 필수입니다.")

        val from = parseInstant("from", fromRaw)
        val to = parseInstant("to", toRaw)
        require(!from.isAfter(to)) { "Job parameter 'from'은 'to'보다 이후일 수 없습니다." }

        return TimeRange(from, to)
    }

    /**
     * ISO-8601 Instant 파싱 유틸리티
     */
    private fun parseInstant(name: String, value: String): Instant =
        try {
            Instant.parse(value)
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("Job parameter '$name'는 ISO-8601 형식이어야 합니다.", ex)
        }

    private data class TimeRange(
        val from: Instant,
        val to: Instant
    )
}

package com.logforge.batch.aggregate

import com.logforge.domain.entity.NormalizedEvent
import com.logforge.domain.repository.NormalizedEventRepository
import com.logforge.domain.repository.TenantDailyMetricRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class AggregateDailyMetricsJobIntegrationTest {

    @Autowired
    @Qualifier("aggregateDailyMetricsJob")
    private lateinit var job: Job

    @Autowired
    private lateinit var jobLauncher: JobLauncher

    @Autowired
    private lateinit var normalizedEventRepository: NormalizedEventRepository

    @Autowired
    private lateinit var tenantDailyMetricRepository: TenantDailyMetricRepository

    @BeforeEach
    fun setUp() {
        tenantDailyMetricRepository.deleteAll()
        normalizedEventRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        tenantDailyMetricRepository.deleteAll()
        normalizedEventRepository.deleteAll()
    }

    @Test
    fun `NormalizedEvent를 일별로 집계해 idempotent하게 TenantDailyMetric으로 저장한다`() {
        val tenant1 = "tenant-1"
        val tenant2 = "tenant-2"

        normalizedEventRepository.saveAll(
            listOf(
                normalizedEvent(tenant1, "LOGIN", "2025-01-01T01:00:00Z", amount = null),
                normalizedEvent(tenant1, "LOGIN", "2025-01-01T05:00:00Z", amount = null),
                normalizedEvent(tenant1, "PURCHASE", "2025-01-01T08:30:00Z", amount = BigDecimal("100.00")),
                normalizedEvent(tenant2, "PURCHASE", "2025-01-02T02:00:00Z", amount = BigDecimal("50.50")),
                normalizedEvent(tenant2, "PURCHASE", "2025-01-02T03:00:00Z", amount = null),
                normalizedEvent(tenant2, "SIGNUP", "2025-01-02T04:00:00Z", amount = null)
            )
        )

        val params = JobParametersBuilder()
            .addString("from", "2025-01-01T00:00:00Z")
            .addString("to", "2025-01-03T00:00:00Z")
            .addString("run.id", UUID.randomUUID().toString())
            .toJobParameters()

        val execution = jobLauncher.run(job, params)
        assertEquals(BatchStatus.COMPLETED, execution.status)

        assertMetrics(
            expected = mapOf(
                Triple(tenant1, LocalDate.parse("2025-01-01"), "LOGIN") to Pair(2L, BigDecimal("0.00")),
                Triple(tenant1, LocalDate.parse("2025-01-01"), "PURCHASE") to Pair(1L, BigDecimal("100.00")),
                Triple(tenant2, LocalDate.parse("2025-01-02"), "PURCHASE") to Pair(2L, BigDecimal("50.50")),
                Triple(tenant2, LocalDate.parse("2025-01-02"), "SIGNUP") to Pair(1L, BigDecimal("0.00"))
            )
        )

        // 동일 구간 재실행 시 기존 메트릭을 덮어써 idempotent를 보장하는지 확인
        normalizedEventRepository.save(
            normalizedEvent(tenant1, "PURCHASE", "2025-01-01T12:00:00Z", amount = BigDecimal("20.00"))
        )

        val secondParams = JobParametersBuilder()
            .addString("from", "2025-01-01T00:00:00Z")
            .addString("to", "2025-01-03T00:00:00Z")
            .addString("run.id", UUID.randomUUID().toString())
            .toJobParameters()

        val secondExecution = jobLauncher.run(job, secondParams)
        assertEquals(BatchStatus.COMPLETED, secondExecution.status)

        assertMetrics(
            expected = mapOf(
                Triple(tenant1, LocalDate.parse("2025-01-01"), "LOGIN") to Pair(2L, BigDecimal("0.00")),
                Triple(tenant1, LocalDate.parse("2025-01-01"), "PURCHASE") to Pair(2L, BigDecimal("120.00")),
                Triple(tenant2, LocalDate.parse("2025-01-02"), "PURCHASE") to Pair(2L, BigDecimal("50.50")),
                Triple(tenant2, LocalDate.parse("2025-01-02"), "SIGNUP") to Pair(1L, BigDecimal("0.00"))
            )
        )
    }

    private fun assertMetrics(expected: Map<Triple<String, LocalDate, String>, Pair<Long, BigDecimal>>) {
        val metrics = tenantDailyMetricRepository.findAll()
        assertEquals(expected.size, metrics.size)
        val metricMap = metrics.associateBy { Triple(it.tenantId, it.eventDate, it.eventType) }

        expected.forEach { (key, value) ->
            val metric = metricMap[key] ?: error("메트릭이 존재하지 않습니다: $key")
            assertEquals(value.first, metric.eventCount)
            assertEquals(0, metric.amountSum.compareTo(value.second))
        }
    }

    private fun normalizedEvent(
        tenantId: String,
        eventType: String,
        eventTime: String,
        amount: BigDecimal?
    ): NormalizedEvent =
        NormalizedEvent(
            tenantId = tenantId,
            eventType = eventType,
            eventTime = Instant.parse(eventTime),
            amount = amount
        )
}

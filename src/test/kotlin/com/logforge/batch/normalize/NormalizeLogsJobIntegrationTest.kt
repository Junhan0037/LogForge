package com.logforge.batch.normalize

import com.fasterxml.jackson.databind.ObjectMapper
import com.logforge.domain.entity.RawLog
import com.logforge.domain.repository.FailedLogRepository
import com.logforge.domain.repository.NormalizedEventRepository
import com.logforge.domain.repository.RawLogRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.time.Instant
import java.util.*

/**
 * NormalizeLogsJob이 RawLog를 정규화해 NormalizedEvent로 저장하고,
 * 실패한 건은 FailedLog로 기록하는지 검증하는 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class NormalizeLogsJobIntegrationTest {

    @Autowired
    @Qualifier("normalizeLogsJob")
    private lateinit var job: Job

    @Autowired
    private lateinit var jobLauncher: JobLauncher

    @Autowired
    private lateinit var rawLogRepository: RawLogRepository

    @Autowired
    private lateinit var normalizedEventRepository: NormalizedEventRepository

    @Autowired
    private lateinit var failedLogRepository: FailedLogRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        normalizedEventRepository.deleteAll()
        rawLogRepository.deleteAll()
        failedLogRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        normalizedEventRepository.deleteAll()
        rawLogRepository.deleteAll()
        failedLogRepository.deleteAll()
    }

    @Test
    fun `정상 payload는 NormalizedEvent로 적재된다`() {
        val rawLog = rawLogRepository.save(
            RawLog(
                tenantId = "tenant-1",
                occurredAt = Instant.parse("2025-01-01T10:00:00Z"),
                payloadJson = """
                    {
                      "event_type": "PURCHASE",
                      "event_time": "2025-01-01T10:00:00Z",
                      "user_id": "user-1",
                      "session_id": "session-1",
                      "amount": 199.99,
                      "metadata": {"channel": "web", "campaign": "winter"}
                    }
                """.trimIndent()
            )
        )

        val params = JobParametersBuilder()
            .addString("from", "2025-01-01T00:00:00Z")
            .addString("to", "2025-01-01T23:59:59Z")
            .addString("run.id", UUID.randomUUID().toString()) // JobInstance 충돌 방지
            .toJobParameters()

        val execution = jobLauncher.run(job, params)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        val events = normalizedEventRepository.findAll()
        assertEquals(1, events.size)

        val normalized = events.first()
        assertEquals(rawLog.tenantId, normalized.tenantId)
        assertEquals("PURCHASE", normalized.eventType)
        assertEquals(Instant.parse("2025-01-01T10:00:00Z"), normalized.eventTime)
        assertEquals("user-1", normalized.userId)
        assertEquals("session-1", normalized.sessionId)
        assertNotNull(normalized.metadataJson)

        val metadata = objectMapper.readTree(normalized.metadataJson!!)
        assertEquals("web", metadata.get("channel").asText())
    }

    @Test
    fun `필수 필드 누락 시 FailedLog로 기록하고 유효 건만 정규화한다`() {
        val validRawLog = rawLogRepository.save(
            RawLog(
                tenantId = "tenant-1",
                occurredAt = Instant.parse("2025-01-02T09:00:00Z"),
                payloadJson = """
                    {
                      "event_type": "LOGIN",
                      "user_id": "user-2",
                      "session_id": "session-2",
                      "metadata": {"ip": "127.0.0.1"}
                    }
                """.trimIndent()
            )
        )
        val invalidRawLog = rawLogRepository.save(
            RawLog(
                tenantId = "tenant-1",
                occurredAt = Instant.parse("2025-01-02T10:00:00Z"),
                payloadJson = """
                    {
                      "event_time": "2025-01-02T10:00:00Z",
                      "user_id": "user-3"
                    }
                """.trimIndent()
            )
        )

        val params = JobParametersBuilder()
            .addString("from", "2025-01-02T00:00:00Z")
            .addString("to", "2025-01-02T23:59:59Z")
            .addString("run.id", UUID.randomUUID().toString())
            .toJobParameters()

        val execution = jobLauncher.run(job, params)

        assertEquals(BatchStatus.COMPLETED, execution.status)

        val events = normalizedEventRepository.findAll()
        assertEquals(1, events.size)
        val normalized = events.first()

        // event_time 미지정 시 RawLog.occurredAt으로 대체되는지 검증
        assertEquals(validRawLog.occurredAt, normalized.eventTime)
        assertEquals("LOGIN", normalized.eventType)

        val failedLogs = failedLogRepository.findAll()
        assertEquals(1, failedLogs.size)

        val failed = failedLogs.first()
        assertEquals(invalidRawLog.id, failed.rawLogId)
        requireNotNull(failed.reason).also {
            assert(it.contains("eventType"))
        }
    }
}

package com.logforge.batch.fetch

import com.logforge.domain.entity.Tenant
import com.logforge.domain.entity.TenantStatus
import com.logforge.domain.repository.RawLogRepository
import com.logforge.domain.repository.TenantRepository
import com.logforge.external.client.ExternalLogClient
import com.logforge.external.dto.ExternalLogDto
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
class FetchRawLogsJobIntegrationTest {

    @Autowired
    @Qualifier("fetchRawLogsJob")
    private lateinit var job: Job

    @Autowired
    private lateinit var jobLauncher: JobLauncher

    @Autowired
    private lateinit var tenantRepository: TenantRepository

    @Autowired
    private lateinit var rawLogRepository: RawLogRepository

    @MockitoBean
    private lateinit var externalLogClient: ExternalLogClient

    private val fromParam = "2025-01-01T00:00:00Z"
    private val toParam = "2025-01-01T02:00:00Z"

    @BeforeEach
    fun cleanUp() {
        rawLogRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @AfterEach
    fun resetMocks() {
        Mockito.reset(externalLogClient)
    }

    @Test
    fun `FetchRawLogsJob는 활성 테넌트별 수집 데이터를 RawLog에 적재한다`() {
        val activeTenantA = tenantRepository.save(activeTenant("A"))
        val activeTenantB = tenantRepository.save(activeTenant("B"))
        tenantRepository.save(inactiveTenant("C")) // 비활성 테넌트는 파티션 대상에서 제외됨

        stubExternalLogs(activeTenantA.id!!, listOf(
            ExternalLogDto(occurredAt = Instant.parse("2025-01-01T00:00:00Z"), payload = """{"event":"a1"}"""),
            ExternalLogDto(occurredAt = Instant.parse("2025-01-01T00:05:00Z"), payload = """{"event":"a2"}""")
        ))
        stubExternalLogs(activeTenantB.id!!, listOf(
            ExternalLogDto(occurredAt = Instant.parse("2025-01-01T01:00:00Z"), payload = """{"event":"b1"}""")
        ))

        val params = JobParametersBuilder()
            .addString("from", fromParam)
            .addString("to", toParam)
            .addLong("run.id", System.currentTimeMillis()) // 테스트 반복 실행 시 JobInstance 충돌 방지
            .toJobParameters()

        val execution = jobLauncher.run(job, params)

        assertEquals(BatchStatus.COMPLETED, execution.status)

        val saved = rawLogRepository.findAll()
        assertEquals(3, saved.size)
        val tenantAId = activeTenantA.id!!.toString()
        val tenantBId = activeTenantB.id!!.toString()
        assertEquals(2, saved.count { it.tenantId == tenantAId })
        assertEquals(1, saved.count { it.tenantId == tenantBId })
        assertTrue(saved.all { it.payloadJson.isNotBlank() })
    }

    /**
     * 활성 테넌트 스텁 생성
     */
    private fun activeTenant(suffix: String) = Tenant(
        name = "tenant-$suffix",
        status = TenantStatus.ACTIVE,
        externalApiBaseUrl = "https://api.test/$suffix",
        apiKey = "api-key-$suffix"
    )

    /**
     * 비활성 테넌트 스텁 생성
     */
    private fun inactiveTenant(suffix: String) = Tenant(
        name = "tenant-$suffix",
        status = TenantStatus.INACTIVE,
        externalApiBaseUrl = "https://api.test/$suffix",
        apiKey = "api-key-$suffix"
    )

    /**
     * suspend 함수인 fetchLogs를 Mockito로 스텁
     */
    private fun stubExternalLogs(tenantId: Long, logs: List<ExternalLogDto>) {
        runBlocking {
            Mockito.doReturn(logs)
                .`when`(externalLogClient)
                .fetchLogs(tenantId.toString(), fromParam, toParam)
        }
    }
}

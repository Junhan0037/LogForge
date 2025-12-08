package com.logforge.api.metric

import com.logforge.domain.entity.Tenant
import com.logforge.domain.entity.TenantDailyMetric
import com.logforge.domain.entity.TenantStatus
import com.logforge.domain.repository.TenantDailyMetricRepository
import com.logforge.domain.repository.TenantRepository
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantDailyMetricControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tenantRepository: TenantRepository

    @Autowired
    private lateinit var tenantDailyMetricRepository: TenantDailyMetricRepository

    @BeforeEach
    fun setUp() {
        tenantDailyMetricRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    fun `테넌트와 날짜 범위, 이벤트 타입으로 집계 데이터를 조회한다`() {
        val tenant = tenantRepository.save(activeTenant("api"))
        val otherTenant = tenantRepository.save(activeTenant("other"))
        val tenantId = tenant.id!!.toString()
        val otherTenantId = otherTenant.id!!.toString()

        tenantDailyMetricRepository.saveAll(
            listOf(
                TenantDailyMetric(tenantId, LocalDate.parse("2025-01-01"), "ORDER_CREATED", 5, BigDecimal("120.50")),
                TenantDailyMetric(tenantId, LocalDate.parse("2025-01-02"), "ORDER_CREATED", 3, BigDecimal("75.00")),
                TenantDailyMetric(tenantId, LocalDate.parse("2025-01-02"), "ORDER_CANCELLED", 2, BigDecimal("20.00")),
                TenantDailyMetric(otherTenantId, LocalDate.parse("2025-01-02"), "ORDER_CREATED", 10, BigDecimal("500.00"))
            )
        )

        mockMvc.perform(
            get("/api/metrics/daily")
                .param("tenantId", tenantId)
                .param("fromDate", "2025-01-01")
                .param("toDate", "2025-01-02")
                .param("eventType", "ORDER_CREATED")
                .param("sort", "eventDate,asc")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", hasSize<Any>(2)))
            .andExpect(jsonPath("$.content[0].tenantId").value(tenantId))
            .andExpect(jsonPath("$.content[0].eventType").value("ORDER_CREATED"))
            .andExpect(jsonPath("$.content[0].eventDate").value("2025-01-01"))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `존재하지 않는 테넌트 조회 시 404를 반환한다`() {
        mockMvc.perform(
            get("/api/metrics/daily")
                .param("tenantId", "999")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"))
    }

    @Test
    fun `fromDate가 toDate보다 늦으면 400을 반환한다`() {
        val tenant = tenantRepository.save(activeTenant("range"))

        mockMvc.perform(
            get("/api/metrics/daily")
                .param("tenantId", tenant.id!!.toString())
                .param("fromDate", "2025-02-01")
                .param("toDate", "2025-01-01")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    /**
     * 테스트용 활성 테넌트 생성 헬퍼
     */
    private fun activeTenant(suffix: String) = Tenant(
        name = "tenant-$suffix",
        status = TenantStatus.ACTIVE,
        externalApiBaseUrl = "https://api.test/$suffix",
        apiKey = "api-key-$suffix"
    )
}

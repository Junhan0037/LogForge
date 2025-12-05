package com.logforge.domain.service

import com.logforge.domain.entity.Tenant
import com.logforge.domain.entity.TenantStatus
import com.logforge.domain.repository.TenantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class TenantServiceTest {

    private val tenantRepository: TenantRepository = Mockito.mock(TenantRepository::class.java)

    private lateinit var tenantService: TenantService

    @BeforeEach
    fun setUp() {
        Mockito.reset(tenantRepository)
        tenantService = DefaultTenantService(tenantRepository)
    }

    @Test
    fun `getActiveTenants는 활성 테넌트를 id 오름차순으로 반환한다`() {
        val tenantA = tenantStub(id = 2L)
        val tenantB = tenantStub(id = 1L)

        Mockito.`when`(tenantRepository.findByStatus(TenantStatus.ACTIVE))
            .thenReturn(listOf(tenantA, tenantB))

        val result = tenantService.getActiveTenants()

        assertEquals(listOf(tenantB, tenantA), result)
        Mockito.verify(tenantRepository).findByStatus(TenantStatus.ACTIVE)
    }

    @Test
    fun `getActiveTenant는 활성 테넌트를 반환한다`() {
        val activeTenant = tenantStub(id = 10L, status = TenantStatus.ACTIVE)

        Mockito.`when`(tenantRepository.findById(10L))
            .thenReturn(Optional.of(activeTenant))

        val result = tenantService.getActiveTenant("10")

        assertSame(activeTenant, result)
    }

    @Test
    fun `숫자가 아닌 tenantId는 InvalidTenantIdentifierException을 발생시킨다`() {
        assertThrows(InvalidTenantIdentifierException::class.java) {
            tenantService.getActiveTenant("tenant-A")
        }
        Mockito.verify(tenantRepository, Mockito.never()).findById(Mockito.anyLong())
    }

    @Test
    fun `미존재 테넌트 조회 시 TenantNotFoundException을 발생시킨다`() {
        Mockito.`when`(tenantRepository.findById(99L))
            .thenReturn(Optional.empty())

        assertThrows(TenantNotFoundException::class.java) {
            tenantService.getActiveTenant("99")
        }
    }

    @Test
    fun `비활성 테넌트 조회 시 InactiveTenantException을 발생시킨다`() {
        val inactiveTenant = tenantStub(id = 3L, status = TenantStatus.INACTIVE)

        Mockito.`when`(tenantRepository.findById(3L))
            .thenReturn(Optional.of(inactiveTenant))

        assertThrows(InactiveTenantException::class.java) {
            tenantService.getActiveTenant("3")
        }
    }

    /**
     * 테스트 가독성을 높이기 위한 테넌트 스텁 생성기.
     */
    private fun tenantStub(id: Long, status: TenantStatus = TenantStatus.ACTIVE): Tenant =
        Tenant(
            name = "tenant-$id",
            status = status,
            externalApiBaseUrl = "https://api.test/$id",
            apiKey = "api-key-$id"
        ).apply {
            this.id = id
        }
}

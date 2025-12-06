package com.logforge.batch.fetch

import com.logforge.domain.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.batch.core.partition.support.Partitioner
import org.springframework.batch.item.ExecutionContext

/**
 * 활성 테넌트를 파티션 단위로 분리하는 Partitioner
 * - 테넌트별 독립 파티션을 생성해 병렬 수집 시 테넌트 간 격리를 보장
 */
class TenantPartitioner(
    private val tenantService: TenantService
) : Partitioner {

    private val logger = LoggerFactory.getLogger(TenantPartitioner::class.java)

    override fun partition(gridSize: Int): MutableMap<String, ExecutionContext> {
        val activeTenants = tenantService.getActiveTenants()
        if (activeTenants.isEmpty()) {
            logger.warn("활성 테넌트가 없어 FetchRawLogs 파티션을 생성하지 않습니다.")
            return mutableMapOf()
        }

        return activeTenants.mapIndexed { index, tenant ->
            val tenantId = tenant.id ?: throw IllegalStateException("활성 테넌트의 식별자가 비어 있습니다. tenant=${tenant.name}")
            val context = ExecutionContext().apply {
                putString("tenantId", tenantId.toString())
            }
            "tenant-$index" to context
        }.toMap().toMutableMap()
    }
}

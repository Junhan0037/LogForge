package com.logforge.batch.normalize

import com.logforge.domain.entity.NormalizedEvent
import com.logforge.domain.repository.NormalizedEventRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

/**
 * NormalizedEvent를 일괄 저장하는 Writer
 * - Chunk 경계에 맞춰 saveAll을 호출해 JPA flush 횟수를 최소화
 */
@Component
class NormalizedEventWriter(
    private val normalizedEventRepository: NormalizedEventRepository
) : ItemWriter<NormalizedEvent> {

    private val logger = LoggerFactory.getLogger(NormalizedEventWriter::class.java)

    override fun write(chunk: Chunk<out NormalizedEvent>) {
        if (chunk.isEmpty()) {
            return
        }

        normalizedEventRepository.saveAll(chunk)
        logger.debug("정규화된 이벤트 {}건 저장 완료", chunk.size())
    }
}

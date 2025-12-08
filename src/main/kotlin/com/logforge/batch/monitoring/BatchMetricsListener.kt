package com.logforge.batch.monitoring

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.item.ExecutionContext
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Batch Job/Step 실행을 Micrometer로 계측하고 표준화된 운영 로그를 남기는 리스너
 * - 처리량/실패/소요시간을 공통 포맷으로 남겨 운영시 즉시 탐지와 알람 연동을 지원
 */
@Component
class BatchMetricsListener(
    private val meterRegistry: MeterRegistry
) : JobExecutionListener, StepExecutionListener {

    private val logger = LoggerFactory.getLogger(BatchMetricsListener::class.java)

    override fun beforeJob(jobExecution: JobExecution) {
        logger.info(
            "배치 시작 - job={}, parameters={}",
            jobExecution.jobInstance.jobName,
            jobExecution.jobParameters.parameters
        )
    }

    override fun afterJob(jobExecution: JobExecution) {
        val jobName = jobExecution.jobInstance.jobName
        val status = jobExecution.status.toString()
        val tags = Tags.of("job", jobName, "status", status)
        val jobDuration = jobExecution.duration()

        meterRegistry.counter("batch.job.runs", tags).increment()
        jobDuration?.let { meterRegistry.timer("batch.job.duration", tags).record(it) }

        val totals = jobExecution.stepExecutions.fold(StepTotals()) { acc, step -> acc.add(step) }

        logger.info(
            "배치 종료 - job={}, status={}, exitCode={}, durationMs={}, totalRead={}, totalWrite={}, totalSkip={}, failures={}",
            jobName,
            jobExecution.status,
            jobExecution.exitStatus.exitCode,
            jobDuration?.toMillis(),
            totals.read,
            totals.write,
            totals.skip,
            totals.failure
        )
    }

    /**
     * 스텝 시작 시점과 파티션된 테넌트 정보를 함께 남겨 장애 조사 시점 로깅
     */
    override fun beforeStep(stepExecution: StepExecution) {
        logger.info(
            "스텝 시작 - job={}, step={}, tenantId={}",
            stepExecution.jobExecution.jobInstance.jobName,
            stepExecution.stepName,
            stepExecution.executionContext.getStringOrNull("tenantId") ?: "n/a"
        )
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        val jobName = stepExecution.jobExecution.jobInstance.jobName
        val stepName = stepExecution.stepName
        val tags = Tags.of("job", jobName, "step", stepName)

        meterRegistry.counter("batch.step.read.count", tags).increment(stepExecution.readCount.toDouble())
        meterRegistry.counter("batch.step.write.count", tags).increment(stepExecution.writeCount.toDouble())
        meterRegistry.counter("batch.step.skip.count", tags).increment(stepExecution.skipCount.toDouble())
        stepExecution.duration()?.let { meterRegistry.timer("batch.step.duration", tags).record(it) }

        logger.info(
            "스텝 종료 - job={}, step={}, tenantId={}, readCount={}, writeCount={}, skipCount={}, commitCount={}, rollbackCount={}, exitCode={}",
            jobName,
            stepName,
            stepExecution.executionContext.getStringOrNull("tenantId") ?: "n/a",
            stepExecution.readCount,
            stepExecution.writeCount,
            stepExecution.skipCount,
            stepExecution.commitCount,
            stepExecution.rollbackCount,
            stepExecution.exitStatus.exitCode
        )

        return stepExecution.exitStatus
    }

    private fun JobExecution.duration(): Duration? =
        startTime?.let { start ->
            endTime?.let { end -> Duration.between(start, end) }
        }

    private fun StepExecution.duration(): Duration? =
        startTime?.let { start ->
            endTime?.let { end -> Duration.between(start, end) }
        }

    private fun ExecutionContext.getStringOrNull(key: String): String? =
        if (containsKey(key)) getString(key) else null

    private data class StepTotals(
        val read: Long = 0,
        val write: Long = 0,
        val skip: Long = 0,
        val failure: Int = 0
    ) {
        fun add(step: StepExecution): StepTotals =
            copy(
                read = read + step.readCount,
                write = write + step.writeCount,
                skip = skip + step.skipCount,
                failure = failure + step.failureExceptions.size
            )
    }
}

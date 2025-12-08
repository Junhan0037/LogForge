package com.logforge.api

import com.logforge.domain.service.InactiveTenantException
import com.logforge.domain.service.InvalidTenantIdentifierException
import com.logforge.domain.service.TenantNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * REST API 전역 예외 처리기
 */
@RestControllerAdvice
class ApiExceptionHandler {

    /**
     * 숫자가 아닌 tenantId 등 잘못된 입력 포맷 처리
     */
    @ExceptionHandler(InvalidTenantIdentifierException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidTenantIdentifier(ex: InvalidTenantIdentifierException): ErrorResponse =
        ErrorResponse(
            code = "INVALID_TENANT_ID",
            message = ex.message ?: "유효하지 않은 테넌트 식별자입니다."
        )

    /**
     * 입력 검증 실패 또는 잘못된 파라미터 처리
     */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException): ErrorResponse =
        ErrorResponse(
            code = "INVALID_REQUEST",
            message = ex.message ?: "잘못된 요청입니다."
        )

    /**
     * 비활성 테넌트 접근 차단
     */
    @ExceptionHandler(InactiveTenantException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInactiveTenant(ex: InactiveTenantException): ErrorResponse =
        ErrorResponse(
            code = "TENANT_INACTIVE",
            message = ex.message ?: "비활성 테넌트입니다."
        )

    /**
     * 존재하지 않는 테넌트 접근
     */
    @ExceptionHandler(TenantNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleTenantNotFound(ex: TenantNotFoundException): ErrorResponse =
        ErrorResponse(
            code = "TENANT_NOT_FOUND",
            message = ex.message ?: "테넌트를 찾을 수 없습니다."
        )
}

/**
 * API 오류 응답 표준 포맷
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)

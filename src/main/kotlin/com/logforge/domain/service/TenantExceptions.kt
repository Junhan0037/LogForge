package com.logforge.domain.service

/**
 * 테넌트 식별자가 숫자 형태(id 기반 문자열)가 아닐 때 발생하는 예외
 */
class InvalidTenantIdentifierException(tenantId: String) :
    IllegalArgumentException("유효하지 않은 테넌트 식별자입니다: $tenantId")

/**
 * 테넌트를 찾지 못했을 때 발생하는 예외
 */
class TenantNotFoundException(tenantId: String) :
    NoSuchElementException("테넌트를 찾을 수 없습니다: $tenantId")

/**
 * 비활성 테넌트를 조회하려 할 때 발생하는 예외
 */
class InactiveTenantException(tenantId: String) :
    IllegalStateException("비활성 상태의 테넌트입니다: $tenantId")

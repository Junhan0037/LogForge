-- 초기 도메인 스키마 정의: 멀티테넌트 로그 수집/정제/집계 테이블

CREATE TABLE tenants (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL,
    external_api_base_url VARCHAR(255) NOT NULL,
    api_key         VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_tenants_name ON tenants (name);

CREATE TABLE raw_logs (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(50) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_raw_logs_tenant_occurred ON raw_logs (tenant_id, occurred_at);

CREATE TABLE normalized_events (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     VARCHAR(50) NOT NULL,
    event_type    VARCHAR(50) NOT NULL,
    event_time    TIMESTAMPTZ NOT NULL,
    user_id       VARCHAR(100),
    session_id    VARCHAR(150),
    amount        NUMERIC(18,2),
    metadata_json TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_normalized_events_tenant_time ON normalized_events (tenant_id, event_time);
CREATE INDEX idx_normalized_events_event_type ON normalized_events (event_type);

CREATE TABLE tenant_daily_metrics (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(50) NOT NULL,
    event_date  DATE NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    event_count BIGINT NOT NULL,
    amount_sum  NUMERIC(18,2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_daily_metrics_tenant_date_type UNIQUE (tenant_id, event_date, event_type)
);

CREATE INDEX idx_tenant_daily_metrics_tenant_date ON tenant_daily_metrics (tenant_id, event_date);
CREATE INDEX idx_tenant_daily_metrics_event_type ON tenant_daily_metrics (event_type);

CREATE TABLE failed_logs (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(50) NOT NULL,
    raw_log_id   BIGINT,
    reason       VARCHAR(500) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_failed_logs_tenant ON failed_logs (tenant_id);

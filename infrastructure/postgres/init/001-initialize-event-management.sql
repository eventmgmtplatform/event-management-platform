\set ON_ERROR_STOP on

CREATE SCHEMA IF NOT EXISTS event_management;

SET search_path TO event_management, public;

-- ============================================================
-- PLATFORM CONFIGURATION
-- ============================================================

CREATE TABLE IF NOT EXISTS platform_configuration (
    configuration_key   VARCHAR(150) PRIMARY KEY,
    configuration_value JSONB NOT NULL,
    description         VARCHAR(500),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- INVENTORY
-- ============================================================

CREATE TABLE IF NOT EXISTS inventory_resource (
    resource_id     UUID PRIMARY KEY,
    tenant          VARCHAR(100) NOT NULL,
    resource_name   VARCHAR(255) NOT NULL,
    resource_type   VARCHAR(100),
    ip_address      INET,
    customer_code   VARCHAR(100),
    attributes      JSONB NOT NULL DEFAULT '{}'::JSONB,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_inventory_resource
        UNIQUE (tenant, resource_name)
);

-- ============================================================
-- INTEGRATION CONFIGURATION
-- ============================================================

CREATE TABLE IF NOT EXISTS integration_configuration (
    integration_id       UUID PRIMARY KEY,
    tenant               VARCHAR(100) NOT NULL,
    integration_name     VARCHAR(150) NOT NULL,
    integration_type     VARCHAR(50) NOT NULL,
    base_url             VARCHAR(1000),
    authentication_type  VARCHAR(50),
    credential_reference VARCHAR(500),
    configuration        JSONB NOT NULL DEFAULT '{}'::JSONB,
    enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_integration_configuration
        UNIQUE (tenant, integration_name)
);

-- ============================================================
-- EVENT POLICIES
-- ============================================================

CREATE TABLE IF NOT EXISTS event_policy (
    policy_id       UUID PRIMARY KEY,
    tenant          VARCHAR(100) NOT NULL,
    policy_name     VARCHAR(200) NOT NULL,
    priority        INTEGER NOT NULL DEFAULT 100,
    match_condition JSONB NOT NULL,
    actions         JSONB NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_event_policy
        UNIQUE (tenant, policy_name)
);

-- ============================================================
-- BLACKOUTS
-- ============================================================

CREATE TABLE IF NOT EXISTS blackout (
    blackout_id      UUID PRIMARY KEY,
    tenant           VARCHAR(100) NOT NULL,
    resource_pattern VARCHAR(500),
    summary_pattern  VARCHAR(1000),
    change_number    VARCHAR(100),
    description      VARCHAR(1000),
    start_at         TIMESTAMPTZ NOT NULL,
    end_at           TIMESTAMPTZ NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_blackout_dates
        CHECK (end_at > start_at)
);

CREATE INDEX IF NOT EXISTS idx_blackout_active_dates
    ON blackout (tenant, enabled, start_at, end_at);

-- ============================================================
-- INITIAL PLATFORM VALUES
-- ============================================================

INSERT INTO platform_configuration (
    configuration_key,
    configuration_value,
    description
)
VALUES (
    'platform.identity',
    '{
       "name": "Event Management Platform",
       "environment": "local-lab",
       "schemaVersion": "1.0"
     }'::JSONB,
    'Identidad básica de la plataforma'
)
ON CONFLICT (configuration_key) DO NOTHING;


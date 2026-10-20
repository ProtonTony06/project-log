-- V1__init.sql — Project_Log · PostgreSQL 17 (imagen postgres:17-alpine)

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,                 -- Argon2id (formato PHC)
    role          TEXT        NOT NULL DEFAULT 'ADMIN', -- sin uso real en v1 (P2)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_username_unique UNIQUE (username)
);

CREATE TABLE projects (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            TEXT        NOT NULL,
    description     TEXT,                                 -- opcional
    status          TEXT        NOT NULL DEFAULT 'ACTIVE',
    api_key_hash    BYTEA,                                -- SHA-256; NUNCA texto plano. NULL = revocada
    api_key_prefix  TEXT,                                 -- p. ej. "plk_Ab3xQw"
    key_created_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT projects_name_unique UNIQUE (name),
    CONSTRAINT projects_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE logs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id     BIGINT      NOT NULL,
    level          TEXT        NOT NULL,
    environment    TEXT        NOT NULL,                 -- obligatorio (decisión cerrada)
    timestamp      TIMESTAMPTZ NOT NULL,                 -- evento (app), UTC
    received_at    TIMESTAMPTZ NOT NULL,                 -- impuesto por el servidor (UTC)
    message        TEXT        NOT NULL,
    exception      TEXT,                                 -- excepción + stacktrace (opcional)
    metadata       JSONB,                                -- opcional, solo objeto
    source         TEXT,                                 -- opcional
    correlation_id TEXT,                                 -- unificada (decisión cerrada), opcional
    CONSTRAINT logs_level_check        CHECK (level IN ('TRACE','DEBUG','INFO','WARN','ERROR','FATAL')),
    CONSTRAINT logs_environment_check  CHECK (environment IN ('development','staging','production')),
    CONSTRAINT logs_project_fk         FOREIGN KEY (project_id)
                REFERENCES projects(id) ON DELETE CASCADE
);

-- Índice maestro del dashboard (filtros combinables + orden keyset sin sort)
CREATE INDEX logs_dashboard_idx
    ON logs (project_id, environment, level, received_at DESC, id DESC);
-- Rango temporal global / orden sin filtro de proyecto
CREATE INDEX logs_received_idx ON logs (received_at DESC);
-- Búsqueda por correlación (parcial: no carga filas nulas)
CREATE INDEX logs_correlation_idx ON logs (correlation_id)
    WHERE correlation_id IS NOT NULL;
-- Localización del proyecto por hash de API key en O(1) (hot-path de ingesta)
CREATE UNIQUE INDEX projects_api_key_hash_idx
    ON projects (api_key_hash) WHERE api_key_hash IS NOT NULL;
--
-- OPCIONAL en v1 (documentado, no ejecutado en V1): trigram GIN para
-- ILIKE '%texto%' — ver V2__add_message_trigram.sql al activarlo cuando
-- el volumen lo justifique.

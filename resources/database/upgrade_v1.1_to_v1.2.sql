-- ragent v1.1 -> v1.2 升级脚本
-- t_message 表：新增深度思考内容及耗时字段

ALTER TABLE t_message ADD COLUMN thinking_content TEXT DEFAULT NULL;
ALTER TABLE t_message ADD COLUMN thinking_duration INT DEFAULT NULL;

CREATE TABLE IF NOT EXISTS t_career_task_attempt (
    id                VARCHAR(20)  NOT NULL PRIMARY KEY,
    user_id           VARCHAR(20),
    business_id       VARCHAR(64),
    scene             VARCHAR(64)  NOT NULL,
    idempotency_key   VARCHAR(512),
    single_flight_key VARCHAR(512),
    trace_id          VARCHAR(64),
    model_name        VARCHAR(128),
    prompt_summary    TEXT,
    status            VARCHAR(32)  NOT NULL,
    replayed          BOOLEAN      NOT NULL DEFAULT FALSE,
    latency_ms        BIGINT       NOT NULL DEFAULT 0,
    error_type        VARCHAR(64),
    error_message     TEXT,
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_career_attempt_user ON t_career_task_attempt (user_id);
CREATE INDEX IF NOT EXISTS idx_career_attempt_business ON t_career_task_attempt (business_id);
CREATE INDEX IF NOT EXISTS idx_career_attempt_scene ON t_career_task_attempt (scene);
CREATE INDEX IF NOT EXISTS idx_career_attempt_status ON t_career_task_attempt (status);
CREATE INDEX IF NOT EXISTS idx_career_attempt_trace ON t_career_task_attempt (trace_id);
CREATE INDEX IF NOT EXISTS idx_career_attempt_single_flight ON t_career_task_attempt (single_flight_key);

ALTER TABLE t_career_interview_session_snapshot
    ADD COLUMN IF NOT EXISTS material_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS last_mutation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS last_turn_seq INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS archive_watermark INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS score_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_committed_turn_digest VARCHAR(64);

WITH duplicated_snapshot AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY session_id, user_id, version
               ORDER BY create_time DESC, id DESC
           ) AS rn
    FROM t_career_interview_session_snapshot
    WHERE deleted = 0
)
UPDATE t_career_interview_session_snapshot snapshot
SET deleted = 1
WHERE snapshot.id IN (
    SELECT id FROM duplicated_snapshot WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_career_interview_snapshot_version
    ON t_career_interview_session_snapshot (session_id, user_id, version) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS t_career_interview_turn_archive (
    id                   VARCHAR(20) NOT NULL PRIMARY KEY,
    session_id           VARCHAR(20) NOT NULL,
    user_id              VARCHAR(20) NOT NULL,
    request_id           VARCHAR(128),
    seq                  INTEGER     NOT NULL,
    snapshot_version     INTEGER     NOT NULL,
    turn_payload_json    JSONB       NOT NULL,
    turn_digest          VARCHAR(64),
    create_time          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT    NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_career_turn_archive_seq
    ON t_career_interview_turn_archive (session_id, user_id, seq, snapshot_version) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_career_turn_archive_request
    ON t_career_interview_turn_archive (request_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_career_turn_archive_session
    ON t_career_interview_turn_archive (session_id, seq);

CREATE TABLE IF NOT EXISTS t_career_agent_execution_trace (
    id             VARCHAR(20)  NOT NULL PRIMARY KEY,
    agent_type     VARCHAR(64)  NOT NULL,
    scene          VARCHAR(64)  NOT NULL,
    session_id     VARCHAR(64),
    user_id        VARCHAR(20),
    trace_id       VARCHAR(64),
    model_name     VARCHAR(128),
    status         VARCHAR(32)  NOT NULL,
    input_summary  TEXT,
    output_summary TEXT,
    latency_ms     BIGINT       NOT NULL DEFAULT 0,
    error_type     VARCHAR(64),
    error_message  TEXT,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_career_agent_trace_agent ON t_career_agent_execution_trace (agent_type);
CREATE INDEX IF NOT EXISTS idx_career_agent_trace_scene ON t_career_agent_execution_trace (scene);
CREATE INDEX IF NOT EXISTS idx_career_agent_trace_session ON t_career_agent_execution_trace (session_id);
CREATE INDEX IF NOT EXISTS idx_career_agent_trace_status ON t_career_agent_execution_trace (status);
CREATE INDEX IF NOT EXISTS idx_career_agent_trace_trace ON t_career_agent_execution_trace (trace_id);

CREATE TABLE IF NOT EXISTS t_career_agent_tool_invocation (
    id                 VARCHAR(20)  NOT NULL PRIMARY KEY,
    execution_trace_id VARCHAR(20),
    trace_id           VARCHAR(64),
    tool_type          VARCHAR(64)  NOT NULL,
    tool_name          VARCHAR(128) NOT NULL,
    input_summary      TEXT,
    output_summary     TEXT,
    status             VARCHAR(32)  NOT NULL,
    latency_ms         BIGINT       NOT NULL DEFAULT 0,
    error_message      TEXT,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_career_agent_tool_trace_id ON t_career_agent_tool_invocation (execution_trace_id);
CREATE INDEX IF NOT EXISTS idx_career_agent_tool_trace ON t_career_agent_tool_invocation (trace_id);
CREATE INDEX IF NOT EXISTS idx_career_agent_tool_name ON t_career_agent_tool_invocation (tool_name);

CREATE TABLE IF NOT EXISTS t_career_agent_session_stats (
    id               VARCHAR(20) NOT NULL PRIMARY KEY,
    session_id       VARCHAR(64) NOT NULL,
    user_id          VARCHAR(20),
    scene            VARCHAR(64) NOT NULL,
    total_calls      BIGINT      NOT NULL DEFAULT 0,
    success_calls    BIGINT      NOT NULL DEFAULT 0,
    failed_calls     BIGINT      NOT NULL DEFAULT 0,
    total_latency_ms BIGINT      NOT NULL DEFAULT 0,
    last_agent_type  VARCHAR(64),
    last_status      VARCHAR(32),
    last_trace_id    VARCHAR(64),
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT    NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_career_agent_session_stats
    ON t_career_agent_session_stats (session_id, scene) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_career_agent_session_user ON t_career_agent_session_stats (user_id);

ALTER TABLE t_career_interview_turn
    ADD COLUMN IF NOT EXISTS answer_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS answer_source_meta_json JSONB;

COMMENT ON COLUMN t_career_interview_turn.answer_source IS '回答来源：TEXT/ASR';
COMMENT ON COLUMN t_career_interview_turn.answer_source_meta_json IS '回答来源元数据JSON';

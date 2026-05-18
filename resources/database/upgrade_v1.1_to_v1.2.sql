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

-- Add cross-agent decision index for Career agents.

CREATE TABLE IF NOT EXISTS t_career_decision_index (
    id                VARCHAR(20)  NOT NULL PRIMARY KEY,
    trace_id          VARCHAR(64),
    user_id           VARCHAR(20),
    business_scene    VARCHAR(64),
    business_id       VARCHAR(64),
    agent_type        VARCHAR(64),
    decision_type     VARCHAR(64),
    decision_key      VARCHAR(128) NOT NULL,
    decision_summary  VARCHAR(512),
    input_ref_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    output_ref_json   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_career_decision_trace ON t_career_decision_index (trace_id);
CREATE INDEX IF NOT EXISTS idx_career_decision_user ON t_career_decision_index (user_id);
CREATE INDEX IF NOT EXISTS idx_career_decision_scene ON t_career_decision_index (business_scene);
CREATE INDEX IF NOT EXISTS idx_career_decision_business ON t_career_decision_index (business_id);
CREATE INDEX IF NOT EXISTS idx_career_decision_agent ON t_career_decision_index (agent_type);
CREATE INDEX IF NOT EXISTS idx_career_decision_type ON t_career_decision_index (decision_type);
CREATE INDEX IF NOT EXISTS idx_career_decision_key ON t_career_decision_index (decision_key);

COMMENT ON TABLE t_career_decision_index IS 'Career 跨 Agent 决策索引表';

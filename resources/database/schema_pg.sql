-- PostgreSQL Schema for Ragent
-- Converted from MySQL schema_table.sql

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- User & Conversation Tables
-- ============================================

CREATE TABLE t_user (
    id           VARCHAR(20)  NOT NULL PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    avatar       VARCHAR(128),
    create_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username)
);
COMMENT ON TABLE t_user IS '系统用户表';
COMMENT ON COLUMN t_user.id IS '主键ID';
COMMENT ON COLUMN t_user.username IS '用户名，唯一';
COMMENT ON COLUMN t_user.password IS '密码';
COMMENT ON COLUMN t_user.role IS '角色：admin/user';
COMMENT ON COLUMN t_user.avatar IS '用户头像';
COMMENT ON COLUMN t_user.create_time IS '创建时间';
COMMENT ON COLUMN t_user.update_time IS '更新时间';
COMMENT ON COLUMN t_user.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_conversation (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    title           VARCHAR(128) NOT NULL,
    last_time       TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    DEFAULT 0,
    CONSTRAINT uk_conversation_user UNIQUE (conversation_id, user_id)
);
CREATE INDEX idx_user_time ON t_conversation (user_id, last_time);
COMMENT ON TABLE t_conversation IS '会话列表';
COMMENT ON COLUMN t_conversation.id IS '主键ID';
COMMENT ON COLUMN t_conversation.conversation_id IS '会话ID';
COMMENT ON COLUMN t_conversation.user_id IS '用户ID';
COMMENT ON COLUMN t_conversation.title IS '会话名称';
COMMENT ON COLUMN t_conversation.last_time IS '最近消息时间';
COMMENT ON COLUMN t_conversation.create_time IS '创建时间';
COMMENT ON COLUMN t_conversation.update_time IS '更新时间';
COMMENT ON COLUMN t_conversation.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_conversation_summary (
    id              VARCHAR(20)      NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    last_message_id VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    DEFAULT 0
);
CREATE INDEX idx_conv_user ON t_conversation_summary (conversation_id, user_id);
COMMENT ON TABLE t_conversation_summary IS '会话摘要表（与消息表分离存储）';

CREATE TABLE t_message (
    id                VARCHAR(20)      NOT NULL PRIMARY KEY,
    conversation_id   VARCHAR(20) NOT NULL,
    user_id           VARCHAR(20) NOT NULL,
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    thinking_content  TEXT,
    thinking_duration INTEGER,
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    DEFAULT 0
);
CREATE INDEX idx_conversation_user_time ON t_message (conversation_id, user_id, create_time);
CREATE INDEX idx_conversation_summary ON t_message (conversation_id, user_id, create_time);
COMMENT ON TABLE t_message IS '会话消息记录表';

CREATE TABLE t_message_feedback (
    id              VARCHAR(20)       NOT NULL PRIMARY KEY,
    message_id      VARCHAR(20)       NOT NULL,
    conversation_id VARCHAR(20)  NOT NULL,
    user_id         VARCHAR(20)  NOT NULL,
    vote            SMALLINT     NOT NULL,
    reason          VARCHAR(255),
    comment         VARCHAR(1024),
    create_time     TIMESTAMP  NOT NULL,
    update_time     TIMESTAMP  NOT NULL,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_msg_user UNIQUE (message_id, user_id)
);
CREATE INDEX idx_conversation_id ON t_message_feedback (conversation_id);
CREATE INDEX idx_user_id ON t_message_feedback (user_id);
COMMENT ON TABLE t_message_feedback IS '会话消息反馈表';

CREATE TABLE t_sample_question (
    id          VARCHAR(20)        NOT NULL PRIMARY KEY,
    title       VARCHAR(64),
    description VARCHAR(255),
    question    VARCHAR(255) NOT NULL,
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT      DEFAULT 0
);
CREATE INDEX idx_sample_question_deleted ON t_sample_question (deleted);
COMMENT ON TABLE t_sample_question IS '示例问题表';

-- ============================================
-- Knowledge Base Tables
-- ============================================

CREATE TABLE t_knowledge_base (
    id              VARCHAR(20)       NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(64)  NOT NULL,
    collection_name VARCHAR(64) NOT NULL,
    created_by      VARCHAR(20)  NOT NULL,
    updated_by      VARCHAR(20),
    create_time     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_collection_name UNIQUE (collection_name)
);
CREATE INDEX idx_kb_name ON t_knowledge_base (name);
COMMENT ON TABLE t_knowledge_base IS '知识库表';

CREATE TABLE t_knowledge_document (
    id               VARCHAR(20)        NOT NULL PRIMARY KEY,
    kb_id            VARCHAR(20)        NOT NULL,
    doc_name         VARCHAR(256)  NOT NULL,
    enabled          SMALLINT      NOT NULL DEFAULT 1,
    chunk_count      INTEGER       DEFAULT 0,
    file_url         VARCHAR(1024) NOT NULL,
    file_type        VARCHAR(16)   NOT NULL,
    file_size        BIGINT,
    process_mode     VARCHAR(16)   DEFAULT 'chunk',
    status           VARCHAR(16)   NOT NULL DEFAULT 'pending',
    source_type      VARCHAR(16),
    source_location  VARCHAR(1024),
    schedule_enabled SMALLINT,
    schedule_cron    VARCHAR(64),
    chunk_strategy   VARCHAR(32),
    chunk_config     JSONB,
    pipeline_id      VARCHAR(20),
    created_by       VARCHAR(20)   NOT NULL,
    updated_by       VARCHAR(20),
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_kb_id ON t_knowledge_document (kb_id);
COMMENT ON TABLE t_knowledge_document IS '知识库文档表';

CREATE TABLE t_knowledge_chunk (
    id           VARCHAR(20)      NOT NULL PRIMARY KEY,
    kb_id        VARCHAR(20)      NOT NULL,
    doc_id       VARCHAR(20)      NOT NULL,
    chunk_index  INTEGER     NOT NULL,
    content      TEXT        NOT NULL,
    content_hash VARCHAR(64),
    char_count   INTEGER,
    token_count  INTEGER,
    enabled      SMALLINT    NOT NULL DEFAULT 1,
    created_by   VARCHAR(20) NOT NULL,
    updated_by   VARCHAR(20),
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_doc_id ON t_knowledge_chunk (doc_id);
COMMENT ON TABLE t_knowledge_chunk IS '知识库文档分块表';

CREATE TABLE t_knowledge_document_chunk_log (
    id                 VARCHAR(20)      NOT NULL PRIMARY KEY,
    doc_id             VARCHAR(20)      NOT NULL,
    status             VARCHAR(16)      NOT NULL,
    process_mode       VARCHAR(16),
    chunk_strategy     VARCHAR(16),
    pipeline_id        VARCHAR(20),
    extract_duration   BIGINT,
    chunk_duration     BIGINT,
    embed_duration     BIGINT,
    persist_duration   BIGINT,
    total_duration     BIGINT,
    chunk_count        INTEGER,
    error_message      TEXT,
    start_time         TIMESTAMP,
    end_time           TIMESTAMP,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_doc_id_log ON t_knowledge_document_chunk_log (doc_id);
COMMENT ON TABLE t_knowledge_document_chunk_log IS '知识库文档分块日志表';

CREATE TABLE t_knowledge_document_schedule (
    id                VARCHAR(20)       NOT NULL PRIMARY KEY,
    doc_id            VARCHAR(20)       NOT NULL,
    kb_id             VARCHAR(20)       NOT NULL,
    cron_expr         VARCHAR(64),
    enabled           SMALLINT     DEFAULT 0,
    next_run_time     TIMESTAMP,
    last_run_time     TIMESTAMP,
    last_success_time TIMESTAMP,
    last_status       VARCHAR(16),
    last_error        VARCHAR(512),
    last_etag         VARCHAR(256),
    last_modified     VARCHAR(256),
    last_content_hash VARCHAR(128),
    lock_owner        VARCHAR(128),
    lock_until        TIMESTAMP,
    create_time       TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doc_id UNIQUE (doc_id)
);
CREATE INDEX idx_next_run ON t_knowledge_document_schedule (next_run_time);
CREATE INDEX idx_lock_until ON t_knowledge_document_schedule (lock_until);
COMMENT ON TABLE t_knowledge_document_schedule IS '知识库文档定时刷新任务表';

CREATE TABLE t_knowledge_document_schedule_exec (
    id            VARCHAR(20)       NOT NULL PRIMARY KEY,
    schedule_id   VARCHAR(20)       NOT NULL,
    doc_id        VARCHAR(20)       NOT NULL,
    kb_id         VARCHAR(20)       NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    message       VARCHAR(512),
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    file_name     VARCHAR(512),
    file_size     BIGINT,
    content_hash  VARCHAR(128),
    etag          VARCHAR(256),
    last_modified VARCHAR(256),
    create_time   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_schedule_time ON t_knowledge_document_schedule_exec (schedule_id, start_time);
CREATE INDEX idx_doc_id_exec ON t_knowledge_document_schedule_exec (doc_id);
COMMENT ON TABLE t_knowledge_document_schedule_exec IS '知识库文档定时刷新执行记录';

-- ============================================
-- RAG Intent & Query Tables
-- ============================================

CREATE TABLE t_intent_node (
    id                    VARCHAR(20)       NOT NULL PRIMARY KEY,
    kb_id                 VARCHAR(20),
    intent_code           VARCHAR(64)  NOT NULL,
    name                  VARCHAR(64)  NOT NULL,
    level                 SMALLINT     NOT NULL,
    parent_code           VARCHAR(64),
    description           VARCHAR(512),
    examples              TEXT,
    collection_name       VARCHAR(128),
    top_k                 INTEGER,
    mcp_tool_id           VARCHAR(128),
    kind                  SMALLINT     NOT NULL DEFAULT 0,
    prompt_snippet        TEXT,
    prompt_template       TEXT,
    param_prompt_template TEXT,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    enabled               SMALLINT     NOT NULL DEFAULT 1,
    create_by             VARCHAR(20),
    update_by             VARCHAR(20),
    create_time           TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE t_intent_node IS '意图树节点配置表';

CREATE TABLE t_query_term_mapping (
    id          VARCHAR(20)       NOT NULL PRIMARY KEY,
    domain      VARCHAR(64),
    source_term VARCHAR(128) NOT NULL,
    target_term VARCHAR(128) NOT NULL,
    match_type  SMALLINT     NOT NULL DEFAULT 1,
    priority    INTEGER      NOT NULL DEFAULT 100,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    remark      VARCHAR(255),
    create_by   VARCHAR(20),
    update_by   VARCHAR(20),
    create_time TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_domain ON t_query_term_mapping (domain);
CREATE INDEX idx_source ON t_query_term_mapping (source_term);
COMMENT ON TABLE t_query_term_mapping IS '关键词归一化映射表';

CREATE TABLE t_rag_trace_run (
    id              VARCHAR(20)           NOT NULL PRIMARY KEY,
    trace_id        VARCHAR(64)      NOT NULL,
    trace_name      VARCHAR(128),
    entry_method    VARCHAR(256),
    conversation_id VARCHAR(20),
    task_id         VARCHAR(20),
    user_id         VARCHAR(20),
    status          VARCHAR(16)      NOT NULL DEFAULT 'RUNNING',
    error_message   VARCHAR(1000),
    start_time      TIMESTAMP(3),
    end_time        TIMESTAMP(3),
    duration_ms     BIGINT,
    extra_data      TEXT,
    create_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT         DEFAULT 0,
    CONSTRAINT uk_run_id UNIQUE (trace_id)
);
CREATE INDEX idx_task_id ON t_rag_trace_run (task_id);
CREATE INDEX idx_user_id_trace ON t_rag_trace_run (user_id);
COMMENT ON TABLE t_rag_trace_run IS 'Trace 运行记录表';

CREATE TABLE t_rag_trace_node (
    id             VARCHAR(20)           NOT NULL PRIMARY KEY,
    trace_id       VARCHAR(20)      NOT NULL,
    node_id        VARCHAR(20)      NOT NULL,
    parent_node_id VARCHAR(20),
    depth          INTEGER          DEFAULT 0,
    node_type      VARCHAR(16),
    node_name      VARCHAR(128),
    class_name     VARCHAR(256),
    method_name    VARCHAR(128),
    status         VARCHAR(16)      NOT NULL DEFAULT 'RUNNING',
    error_message  VARCHAR(1000),
    start_time     TIMESTAMP(3),
    end_time       TIMESTAMP(3),
    duration_ms    BIGINT,
    extra_data     TEXT,
    create_time    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT         DEFAULT 0,
    CONSTRAINT uk_run_node UNIQUE (trace_id, node_id)
);
COMMENT ON TABLE t_rag_trace_node IS 'Trace 节点记录表';

-- ============================================
-- Ingestion Pipeline Tables
-- ============================================

CREATE TABLE t_ingestion_pipeline (
    id          VARCHAR(20)      NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_by  VARCHAR(20) DEFAULT '',
    updated_by  VARCHAR(20) DEFAULT '',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_name UNIQUE (name, deleted)
);
COMMENT ON TABLE t_ingestion_pipeline IS '摄取流水线表';

CREATE TABLE t_ingestion_pipeline_node (
    id             VARCHAR(20)      NOT NULL PRIMARY KEY,
    pipeline_id    VARCHAR(20)      NOT NULL,
    node_id        VARCHAR(20) NOT NULL,
    node_type      VARCHAR(16) NOT NULL,
    next_node_id   VARCHAR(20),
    settings_json  JSONB,
    condition_json JSONB,
    created_by     VARCHAR(20) DEFAULT '',
    updated_by     VARCHAR(20) DEFAULT '',
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_node UNIQUE (pipeline_id, node_id, deleted)
);
CREATE INDEX idx_ingestion_pipeline_node_pipeline ON t_ingestion_pipeline_node (pipeline_id);
COMMENT ON TABLE t_ingestion_pipeline_node IS '摄取流水线节点表';

CREATE TABLE t_ingestion_task (
    id               VARCHAR(20)      NOT NULL PRIMARY KEY,
    pipeline_id      VARCHAR(20)      NOT NULL,
    source_type      VARCHAR(20) NOT NULL,
    source_location  TEXT,
    source_file_name VARCHAR(255),
    status           VARCHAR(16) NOT NULL,
    chunk_count      INTEGER     DEFAULT 0,
    error_message    TEXT,
    logs_json        JSONB,
    metadata_json    JSONB,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_by       VARCHAR(20) DEFAULT '',
    updated_by       VARCHAR(20) DEFAULT '',
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_ingestion_task_pipeline ON t_ingestion_task (pipeline_id);
CREATE INDEX idx_ingestion_task_status ON t_ingestion_task (status);
COMMENT ON TABLE t_ingestion_task IS '摄取任务表';

CREATE TABLE t_ingestion_task_node (
    id            VARCHAR(20)      NOT NULL PRIMARY KEY,
    task_id       VARCHAR(20)      NOT NULL,
    pipeline_id   VARCHAR(20)      NOT NULL,
    node_id       VARCHAR(20) NOT NULL,
    node_type     VARCHAR(16) NOT NULL,
    node_order    INTEGER     NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL,
    duration_ms   BIGINT      NOT NULL DEFAULT 0,
    message       TEXT,
    error_message TEXT,
    output_json   TEXT,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_ingestion_task_node_task ON t_ingestion_task_node (task_id);
CREATE INDEX idx_ingestion_task_node_pipeline ON t_ingestion_task_node (pipeline_id);
CREATE INDEX idx_ingestion_task_node_status ON t_ingestion_task_node (status);
COMMENT ON TABLE t_ingestion_task_node IS '摄取任务节点表';

-- ============================================
-- Vector Storage Table (pgvector)
-- ============================================

CREATE TABLE t_knowledge_vector (
    id          VARCHAR(20) PRIMARY KEY,
    content     TEXT,
    metadata    JSONB,
    embedding   vector(1536)
);

CREATE INDEX idx_kv_metadata ON t_knowledge_vector USING gin(metadata);
CREATE INDEX idx_kv_embedding ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops);
COMMENT ON TABLE t_knowledge_vector IS '知识库向量存储表';
COMMENT ON COLUMN t_knowledge_vector.id IS '分块ID';
COMMENT ON COLUMN t_knowledge_vector.content IS '分块文本内容';
COMMENT ON COLUMN t_knowledge_vector.metadata IS '元数据';
COMMENT ON COLUMN t_knowledge_vector.embedding IS '向量';

-- ============================================
-- Career Agent Platform Tables
-- ============================================

CREATE TABLE t_career_candidate_profile (
    id           VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id      VARCHAR(20) NOT NULL,
    display_name VARCHAR(128),
    headline     VARCHAR(255),
    summary      TEXT,
    profile_json JSONB,
    created_by   VARCHAR(20),
    updated_by   VARCHAR(20),
    create_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_profile_user ON t_career_candidate_profile (user_id);
CREATE INDEX idx_career_profile_deleted ON t_career_candidate_profile (deleted);
COMMENT ON TABLE t_career_candidate_profile IS '求职者画像表';
COMMENT ON COLUMN t_career_candidate_profile.id IS '主键ID';
COMMENT ON COLUMN t_career_candidate_profile.user_id IS '用户ID';
COMMENT ON COLUMN t_career_candidate_profile.display_name IS '展示姓名';
COMMENT ON COLUMN t_career_candidate_profile.headline IS '职业标题';
COMMENT ON COLUMN t_career_candidate_profile.summary IS '画像摘要';
COMMENT ON COLUMN t_career_candidate_profile.profile_json IS '结构化画像JSON';
COMMENT ON COLUMN t_career_candidate_profile.created_by IS '创建人';
COMMENT ON COLUMN t_career_candidate_profile.updated_by IS '修改人';
COMMENT ON COLUMN t_career_candidate_profile.create_time IS '创建时间';
COMMENT ON COLUMN t_career_candidate_profile.update_time IS '更新时间';
COMMENT ON COLUMN t_career_candidate_profile.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_resume_document (
    id            VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id       VARCHAR(20) NOT NULL,
    profile_id    VARCHAR(20),
    original_name VARCHAR(255),
    file_url      VARCHAR(512),
    file_type     VARCHAR(32),
    file_size     BIGINT,
    parse_status  VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    raw_text      TEXT,
    parse_error   TEXT,
    trace_id      VARCHAR(64),
    created_by    VARCHAR(20),
    updated_by    VARCHAR(20),
    create_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_resume_doc_user ON t_career_resume_document (user_id);
CREATE INDEX idx_career_resume_doc_profile ON t_career_resume_document (profile_id);
CREATE INDEX idx_career_resume_doc_status ON t_career_resume_document (parse_status);
CREATE INDEX idx_career_resume_doc_trace ON t_career_resume_document (trace_id);
COMMENT ON TABLE t_career_resume_document IS '简历原始文档表';
COMMENT ON COLUMN t_career_resume_document.id IS '主键ID';
COMMENT ON COLUMN t_career_resume_document.user_id IS '用户ID';
COMMENT ON COLUMN t_career_resume_document.profile_id IS '求职者画像ID';
COMMENT ON COLUMN t_career_resume_document.original_name IS '原始文件名';
COMMENT ON COLUMN t_career_resume_document.file_url IS '文件地址';
COMMENT ON COLUMN t_career_resume_document.file_type IS '文件类型';
COMMENT ON COLUMN t_career_resume_document.file_size IS '文件大小';
COMMENT ON COLUMN t_career_resume_document.parse_status IS '解析状态';
COMMENT ON COLUMN t_career_resume_document.raw_text IS '提取后的原始文本';
COMMENT ON COLUMN t_career_resume_document.parse_error IS '解析失败原因';
COMMENT ON COLUMN t_career_resume_document.trace_id IS 'Trace ID';
COMMENT ON COLUMN t_career_resume_document.created_by IS '创建人';
COMMENT ON COLUMN t_career_resume_document.updated_by IS '修改人';
COMMENT ON COLUMN t_career_resume_document.create_time IS '创建时间';
COMMENT ON COLUMN t_career_resume_document.update_time IS '更新时间';
COMMENT ON COLUMN t_career_resume_document.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_resume_version (
    id               VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id          VARCHAR(20) NOT NULL,
    profile_id       VARCHAR(20),
    document_id      VARCHAR(20),
    title            VARCHAR(128) NOT NULL,
    version_no       INTEGER     NOT NULL DEFAULT 1,
    source_type      VARCHAR(32) NOT NULL,
    content_json     JSONB,
    markdown_content TEXT,
    active           SMALLINT    NOT NULL DEFAULT 1,
    created_by       VARCHAR(20),
    updated_by       VARCHAR(20),
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_resume_version_user ON t_career_resume_version (user_id);
CREATE INDEX idx_career_resume_version_profile ON t_career_resume_version (profile_id);
CREATE INDEX idx_career_resume_version_doc ON t_career_resume_version (document_id);
COMMENT ON TABLE t_career_resume_version IS '简历版本表';
COMMENT ON COLUMN t_career_resume_version.id IS '主键ID';
COMMENT ON COLUMN t_career_resume_version.user_id IS '用户ID';
COMMENT ON COLUMN t_career_resume_version.profile_id IS '求职者画像ID';
COMMENT ON COLUMN t_career_resume_version.document_id IS '来源简历文档ID';
COMMENT ON COLUMN t_career_resume_version.title IS '版本标题';
COMMENT ON COLUMN t_career_resume_version.version_no IS '版本号';
COMMENT ON COLUMN t_career_resume_version.source_type IS '版本来源类型';
COMMENT ON COLUMN t_career_resume_version.content_json IS '结构化简历内容JSON';
COMMENT ON COLUMN t_career_resume_version.markdown_content IS 'Markdown简历正文';
COMMENT ON COLUMN t_career_resume_version.active IS '是否启用 1：启用 0：停用';
COMMENT ON COLUMN t_career_resume_version.created_by IS '创建人';
COMMENT ON COLUMN t_career_resume_version.updated_by IS '修改人';
COMMENT ON COLUMN t_career_resume_version.create_time IS '创建时间';
COMMENT ON COLUMN t_career_resume_version.update_time IS '更新时间';
COMMENT ON COLUMN t_career_resume_version.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_job_description (
    id              VARCHAR(20)  NOT NULL PRIMARY KEY,
    user_id         VARCHAR(20)  NOT NULL,
    title           VARCHAR(128) NOT NULL,
    company         VARCHAR(128),
    source_type     VARCHAR(32),
    source_location VARCHAR(512),
    raw_text        TEXT,
    parsed_json     JSONB,
    created_by      VARCHAR(20),
    updated_by      VARCHAR(20),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_jd_user ON t_career_job_description (user_id);
CREATE INDEX idx_career_jd_title ON t_career_job_description (title);
COMMENT ON TABLE t_career_job_description IS '目标岗位JD表';
COMMENT ON COLUMN t_career_job_description.id IS '主键ID';
COMMENT ON COLUMN t_career_job_description.user_id IS '用户ID';
COMMENT ON COLUMN t_career_job_description.title IS '岗位标题';
COMMENT ON COLUMN t_career_job_description.company IS '公司名称';
COMMENT ON COLUMN t_career_job_description.source_type IS '来源类型';
COMMENT ON COLUMN t_career_job_description.source_location IS '来源位置';
COMMENT ON COLUMN t_career_job_description.raw_text IS 'JD原文';
COMMENT ON COLUMN t_career_job_description.parsed_json IS '结构化JD解析JSON';
COMMENT ON COLUMN t_career_job_description.created_by IS '创建人';
COMMENT ON COLUMN t_career_job_description.updated_by IS '修改人';
COMMENT ON COLUMN t_career_job_description.create_time IS '创建时间';
COMMENT ON COLUMN t_career_job_description.update_time IS '更新时间';
COMMENT ON COLUMN t_career_job_description.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_job_alignment_report (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id           VARCHAR(20) NOT NULL,
    resume_version_id VARCHAR(20) NOT NULL,
    jd_id             VARCHAR(20) NOT NULL,
    score             INTEGER,
    summary           TEXT,
    evidence_json     JSONB,
    gaps_json         JSONB,
    risks_json        JSONB,
    trace_id          VARCHAR(64),
    created_by        VARCHAR(20),
    updated_by        VARCHAR(20),
    create_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_alignment_user ON t_career_job_alignment_report (user_id);
CREATE INDEX idx_career_alignment_resume ON t_career_job_alignment_report (resume_version_id);
CREATE INDEX idx_career_alignment_jd ON t_career_job_alignment_report (jd_id);
CREATE INDEX idx_career_alignment_resume_jd ON t_career_job_alignment_report (resume_version_id, jd_id);
CREATE INDEX idx_career_alignment_trace ON t_career_job_alignment_report (trace_id);
COMMENT ON TABLE t_career_job_alignment_report IS '简历JD匹配分析报告表';
COMMENT ON COLUMN t_career_job_alignment_report.id IS '主键ID';
COMMENT ON COLUMN t_career_job_alignment_report.user_id IS '用户ID';
COMMENT ON COLUMN t_career_job_alignment_report.resume_version_id IS '简历版本ID';
COMMENT ON COLUMN t_career_job_alignment_report.jd_id IS 'JD ID';
COMMENT ON COLUMN t_career_job_alignment_report.score IS '匹配总分';
COMMENT ON COLUMN t_career_job_alignment_report.summary IS '报告摘要';
COMMENT ON COLUMN t_career_job_alignment_report.evidence_json IS '证据JSON';
COMMENT ON COLUMN t_career_job_alignment_report.gaps_json IS '差距JSON';
COMMENT ON COLUMN t_career_job_alignment_report.risks_json IS '风险JSON';
COMMENT ON COLUMN t_career_job_alignment_report.trace_id IS 'Trace ID';
COMMENT ON COLUMN t_career_job_alignment_report.created_by IS '创建人';
COMMENT ON COLUMN t_career_job_alignment_report.updated_by IS '修改人';
COMMENT ON COLUMN t_career_job_alignment_report.create_time IS '创建时间';
COMMENT ON COLUMN t_career_job_alignment_report.update_time IS '更新时间';
COMMENT ON COLUMN t_career_job_alignment_report.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_resume_optimization_task (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id           VARCHAR(20) NOT NULL,
    resume_version_id VARCHAR(20) NOT NULL,
    jd_id             VARCHAR(20),
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    input_json        JSONB,
    output_json       JSONB,
    trace_id          VARCHAR(64),
    error_message     TEXT,
    created_by        VARCHAR(20),
    updated_by        VARCHAR(20),
    create_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_opt_task_user ON t_career_resume_optimization_task (user_id);
CREATE INDEX idx_career_opt_task_resume ON t_career_resume_optimization_task (resume_version_id);
CREATE INDEX idx_career_opt_task_status ON t_career_resume_optimization_task (status);
CREATE INDEX idx_career_opt_task_trace ON t_career_resume_optimization_task (trace_id);
COMMENT ON TABLE t_career_resume_optimization_task IS '简历优化任务表';
COMMENT ON COLUMN t_career_resume_optimization_task.id IS '主键ID';
COMMENT ON COLUMN t_career_resume_optimization_task.user_id IS '用户ID';
COMMENT ON COLUMN t_career_resume_optimization_task.resume_version_id IS '简历版本ID';
COMMENT ON COLUMN t_career_resume_optimization_task.jd_id IS 'JD ID';
COMMENT ON COLUMN t_career_resume_optimization_task.status IS '任务状态';
COMMENT ON COLUMN t_career_resume_optimization_task.input_json IS '任务输入JSON';
COMMENT ON COLUMN t_career_resume_optimization_task.output_json IS '任务输出JSON';
COMMENT ON COLUMN t_career_resume_optimization_task.trace_id IS 'Trace ID';
COMMENT ON COLUMN t_career_resume_optimization_task.error_message IS '失败原因';
COMMENT ON COLUMN t_career_resume_optimization_task.created_by IS '创建人';
COMMENT ON COLUMN t_career_resume_optimization_task.updated_by IS '修改人';
COMMENT ON COLUMN t_career_resume_optimization_task.create_time IS '创建时间';
COMMENT ON COLUMN t_career_resume_optimization_task.update_time IS '更新时间';
COMMENT ON COLUMN t_career_resume_optimization_task.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_resume_optimization_suggestion (
    id             VARCHAR(20) NOT NULL PRIMARY KEY,
    task_id        VARCHAR(20) NOT NULL,
    user_id        VARCHAR(20) NOT NULL,
    category       VARCHAR(64),
    title          VARCHAR(128),
    original_text  TEXT,
    suggested_text TEXT,
    reason         TEXT,
    risk_level     VARCHAR(32),
    status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_opt_suggestion_task ON t_career_resume_optimization_suggestion (task_id);
CREATE INDEX idx_career_opt_suggestion_user ON t_career_resume_optimization_suggestion (user_id);
CREATE INDEX idx_career_opt_suggestion_status ON t_career_resume_optimization_suggestion (status);
COMMENT ON TABLE t_career_resume_optimization_suggestion IS '简历优化建议表';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.id IS '主键ID';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.task_id IS '优化任务ID';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.user_id IS '用户ID';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.category IS '建议分类';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.title IS '建议标题';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.original_text IS '原文';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.suggested_text IS '建议文本';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.reason IS '建议理由';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.risk_level IS '真实性风险等级';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.status IS '建议状态';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.create_time IS '创建时间';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.update_time IS '更新时间';
COMMENT ON COLUMN t_career_resume_optimization_suggestion.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_resume_optimization_review (
    id                   VARCHAR(20) NOT NULL PRIMARY KEY,
    task_id              VARCHAR(20) NOT NULL,
    user_id              VARCHAR(20) NOT NULL,
    iteration_no         INTEGER     NOT NULL DEFAULT 1,
    executor_output_json JSONB,
    reviewer_output_json JSONB,
    quality_score        NUMERIC(5, 4),
    truthfulness_risk    BOOLEAN     NOT NULL DEFAULT FALSE,
    status               VARCHAR(32) NOT NULL,
    trace_id             VARCHAR(64),
    error_message        TEXT,
    created_by           VARCHAR(20),
    updated_by           VARCHAR(20),
    create_time          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_opt_review_task ON t_career_resume_optimization_review (task_id);
CREATE INDEX idx_career_opt_review_user ON t_career_resume_optimization_review (user_id);
CREATE INDEX idx_career_opt_review_status ON t_career_resume_optimization_review (status);
CREATE INDEX idx_career_opt_review_trace ON t_career_resume_optimization_review (trace_id);
COMMENT ON TABLE t_career_resume_optimization_review IS 'Resume optimization quality review table';

CREATE TABLE t_career_progress_event (
    id           VARCHAR(20) NOT NULL PRIMARY KEY,
    task_id      VARCHAR(20) NOT NULL,
    user_id      VARCHAR(20) NOT NULL,
    event_type   VARCHAR(64) NOT NULL,
    message      VARCHAR(512),
    payload_json JSONB,
    create_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_progress_event_task ON t_career_progress_event (task_id, create_time);
CREATE INDEX idx_career_progress_event_user ON t_career_progress_event (user_id);
CREATE INDEX idx_career_progress_event_type ON t_career_progress_event (event_type);
COMMENT ON TABLE t_career_progress_event IS 'Career task progress event table';

CREATE TABLE t_career_single_flight_record (
    id                VARCHAR(20)  NOT NULL PRIMARY KEY,
    single_flight_key VARCHAR(256) NOT NULL,
    scene             VARCHAR(64)  NOT NULL,
    owner_id          VARCHAR(128),
    fencing_token     BIGINT       NOT NULL DEFAULT 1,
    status            VARCHAR(32)  NOT NULL,
    heartbeat_time    TIMESTAMP,
    request_count     INTEGER      NOT NULL DEFAULT 1,
    result_json       JSONB,
    error_type        VARCHAR(64),
    trace_id          VARCHAR(64),
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_career_single_flight_key ON t_career_single_flight_record (single_flight_key) WHERE deleted = 0;
CREATE INDEX idx_career_single_flight_scene ON t_career_single_flight_record (scene);
CREATE INDEX idx_career_single_flight_status ON t_career_single_flight_record (status);
CREATE INDEX idx_career_single_flight_owner ON t_career_single_flight_record (owner_id, fencing_token);
COMMENT ON TABLE t_career_single_flight_record IS 'Career AI single-flight governance table';

CREATE TABLE t_career_task_attempt (
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
CREATE INDEX idx_career_attempt_user ON t_career_task_attempt (user_id);
CREATE INDEX idx_career_attempt_business ON t_career_task_attempt (business_id);
CREATE INDEX idx_career_attempt_scene ON t_career_task_attempt (scene);
CREATE INDEX idx_career_attempt_status ON t_career_task_attempt (status);
CREATE INDEX idx_career_attempt_trace ON t_career_task_attempt (trace_id);
CREATE INDEX idx_career_attempt_single_flight ON t_career_task_attempt (single_flight_key);
COMMENT ON TABLE t_career_task_attempt IS 'Career AI task attempt audit table';

CREATE TABLE t_career_resume_export_record (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id           VARCHAR(20) NOT NULL,
    resume_version_id VARCHAR(20) NOT NULL,
    export_type       VARCHAR(32) NOT NULL,
    file_url          VARCHAR(512),
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message     TEXT,
    template_version  VARCHAR(64),
    validation_result_json JSONB,
    trace_id          VARCHAR(64),
    create_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_export_user ON t_career_resume_export_record (user_id);
CREATE INDEX idx_career_export_resume ON t_career_resume_export_record (resume_version_id);
COMMENT ON TABLE t_career_resume_export_record IS '简历导出记录表';
COMMENT ON COLUMN t_career_resume_export_record.id IS '主键ID';
COMMENT ON COLUMN t_career_resume_export_record.user_id IS '用户ID';
COMMENT ON COLUMN t_career_resume_export_record.resume_version_id IS '简历版本ID';
COMMENT ON COLUMN t_career_resume_export_record.export_type IS '导出类型';
COMMENT ON COLUMN t_career_resume_export_record.file_url IS '导出文件地址';
COMMENT ON COLUMN t_career_resume_export_record.status IS '导出状态';
COMMENT ON COLUMN t_career_resume_export_record.error_message IS '失败原因';
COMMENT ON COLUMN t_career_resume_export_record.template_version IS '渲染模板版本';
COMMENT ON COLUMN t_career_resume_export_record.validation_result_json IS '渲染校验结果';
COMMENT ON COLUMN t_career_resume_export_record.trace_id IS '渲染 Trace ID';
COMMENT ON COLUMN t_career_resume_export_record.create_time IS '创建时间';
COMMENT ON COLUMN t_career_resume_export_record.update_time IS '更新时间';
COMMENT ON COLUMN t_career_resume_export_record.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_interview_session (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id           VARCHAR(20) NOT NULL,
    resume_version_id VARCHAR(20) NOT NULL,
    jd_id             VARCHAR(20),
    status            VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    plan_json         JSONB,
    current_turn_no   INTEGER     NOT NULL DEFAULT 0,
    trace_id          VARCHAR(64),
    created_by        VARCHAR(20),
    updated_by        VARCHAR(20),
    create_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_interview_session_user ON t_career_interview_session (user_id);
CREATE INDEX idx_career_interview_session_resume ON t_career_interview_session (resume_version_id);
CREATE INDEX idx_career_interview_session_status ON t_career_interview_session (status);
CREATE INDEX idx_career_interview_session_trace ON t_career_interview_session (trace_id);
COMMENT ON TABLE t_career_interview_session IS '模拟面试会话表';
COMMENT ON COLUMN t_career_interview_session.id IS '主键ID';
COMMENT ON COLUMN t_career_interview_session.user_id IS '用户ID';
COMMENT ON COLUMN t_career_interview_session.resume_version_id IS '简历版本ID';
COMMENT ON COLUMN t_career_interview_session.jd_id IS 'JD ID';
COMMENT ON COLUMN t_career_interview_session.status IS '会话状态';
COMMENT ON COLUMN t_career_interview_session.plan_json IS '面试计划JSON';
COMMENT ON COLUMN t_career_interview_session.current_turn_no IS '当前轮次';
COMMENT ON COLUMN t_career_interview_session.trace_id IS 'Trace ID';
COMMENT ON COLUMN t_career_interview_session.created_by IS '创建人';
COMMENT ON COLUMN t_career_interview_session.updated_by IS '修改人';
COMMENT ON COLUMN t_career_interview_session.create_time IS '创建时间';
COMMENT ON COLUMN t_career_interview_session.update_time IS '更新时间';
COMMENT ON COLUMN t_career_interview_session.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_interview_turn (
    id                         VARCHAR(20) NOT NULL PRIMARY KEY,
    session_id                 VARCHAR(20) NOT NULL,
    user_id                    VARCHAR(20) NOT NULL,
    turn_no                    INTEGER     NOT NULL,
    turn_type                  VARCHAR(32),
    question                   TEXT,
    answer                     TEXT,
    feedback_json              JSONB,
    status                     VARCHAR(32),
    score                      INTEGER,
    step_idempotency_key       VARCHAR(128),
    answer_status              VARCHAR(32),
    evaluation_status          VARCHAR(32),
    follow_up_decision_status  VARCHAR(32),
    compensation_status        VARCHAR(32),
    attempt_count              INTEGER     NOT NULL DEFAULT 0,
    last_error                 TEXT,
    create_time                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                    SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_interview_turn_session ON t_career_interview_turn (session_id);
CREATE INDEX idx_career_interview_turn_user ON t_career_interview_turn (user_id);
CREATE INDEX idx_career_interview_turn_step ON t_career_interview_turn (step_idempotency_key) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_career_interview_turn_no ON t_career_interview_turn (session_id, turn_no) WHERE deleted = 0;
COMMENT ON TABLE t_career_interview_turn IS '模拟面试轮次表';
COMMENT ON COLUMN t_career_interview_turn.id IS '主键ID';
COMMENT ON COLUMN t_career_interview_turn.session_id IS '面试会话ID';
COMMENT ON COLUMN t_career_interview_turn.user_id IS '用户ID';
COMMENT ON COLUMN t_career_interview_turn.turn_no IS '轮次序号';
COMMENT ON COLUMN t_career_interview_turn.turn_type IS '轮次类型';
COMMENT ON COLUMN t_career_interview_turn.question IS '面试问题';
COMMENT ON COLUMN t_career_interview_turn.answer IS '用户回答';
COMMENT ON COLUMN t_career_interview_turn.feedback_json IS '反馈JSON';
COMMENT ON COLUMN t_career_interview_turn.status IS '轮次状态';
COMMENT ON COLUMN t_career_interview_turn.score IS '轮次评分';
COMMENT ON COLUMN t_career_interview_turn.create_time IS '创建时间';
COMMENT ON COLUMN t_career_interview_turn.update_time IS '更新时间';
COMMENT ON COLUMN t_career_interview_turn.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE t_career_interview_session_snapshot (
    id                    VARCHAR(20) NOT NULL PRIMARY KEY,
    session_id            VARCHAR(20) NOT NULL,
    user_id               VARCHAR(20) NOT NULL,
    version               INTEGER     NOT NULL DEFAULT 1,
    snapshot_json         JSONB       NOT NULL,
    last_applied_step_key VARCHAR(128),
    status                VARCHAR(32),
    created_by            VARCHAR(20),
    updated_by            VARCHAR(20),
    create_time           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_interview_snapshot_session ON t_career_interview_session_snapshot (session_id, version DESC);
CREATE INDEX idx_career_interview_snapshot_user ON t_career_interview_session_snapshot (user_id);
CREATE INDEX idx_career_interview_snapshot_step ON t_career_interview_session_snapshot (last_applied_step_key) WHERE deleted = 0;
COMMENT ON TABLE t_career_interview_session_snapshot IS 'Interview session recovery snapshot table';
COMMENT ON COLUMN t_career_interview_session_snapshot.session_id IS 'Interview session id';
COMMENT ON COLUMN t_career_interview_session_snapshot.version IS 'Snapshot version';
COMMENT ON COLUMN t_career_interview_session_snapshot.snapshot_json IS 'Recoverable session runtime JSON';
COMMENT ON COLUMN t_career_interview_session_snapshot.last_applied_step_key IS 'Last applied turn idempotency key';

CREATE TABLE t_career_interview_report (
    id               VARCHAR(20) NOT NULL PRIMARY KEY,
    session_id       VARCHAR(20) NOT NULL,
    user_id          VARCHAR(20) NOT NULL,
    overall_score    INTEGER,
    radar_json       JSONB,
    playback_json    JSONB,
    suggestions_json JSONB,
    summary          TEXT,
    trace_id         VARCHAR(64),
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_interview_report_session ON t_career_interview_report (session_id);
CREATE INDEX idx_career_interview_report_user ON t_career_interview_report (user_id);
CREATE INDEX idx_career_interview_report_trace ON t_career_interview_report (trace_id);
COMMENT ON TABLE t_career_interview_report IS '模拟面试复盘报告表';
COMMENT ON COLUMN t_career_interview_report.id IS '主键ID';
COMMENT ON COLUMN t_career_interview_report.session_id IS '面试会话ID';
COMMENT ON COLUMN t_career_interview_report.user_id IS '用户ID';
COMMENT ON COLUMN t_career_interview_report.overall_score IS '综合评分';
COMMENT ON COLUMN t_career_interview_report.radar_json IS '雷达图JSON';
COMMENT ON COLUMN t_career_interview_report.playback_json IS '逐题复盘JSON';
COMMENT ON COLUMN t_career_interview_report.suggestions_json IS '改进建议JSON';
COMMENT ON COLUMN t_career_interview_report.summary IS '报告摘要';
COMMENT ON COLUMN t_career_interview_report.trace_id IS 'Trace ID';
COMMENT ON COLUMN t_career_interview_report.create_time IS '创建时间';
COMMENT ON COLUMN t_career_interview_report.update_time IS '更新时间';
COMMENT ON COLUMN t_career_interview_report.deleted IS '是否删除 0：正常 1：删除';

-- ============================================
-- Column Comments
-- ============================================

-- t_conversation_summary
COMMENT ON COLUMN t_conversation_summary.id IS '主键ID';
COMMENT ON COLUMN t_conversation_summary.conversation_id IS '会话ID';
COMMENT ON COLUMN t_conversation_summary.user_id IS '用户ID';
COMMENT ON COLUMN t_conversation_summary.last_message_id IS '摘要最后消息ID';
COMMENT ON COLUMN t_conversation_summary.content IS '会话摘要内容';
COMMENT ON COLUMN t_conversation_summary.create_time IS '创建时间';
COMMENT ON COLUMN t_conversation_summary.update_time IS '更新时间';
COMMENT ON COLUMN t_conversation_summary.deleted IS '是否删除 0：正常 1：删除';

-- t_message
COMMENT ON COLUMN t_message.id IS '主键ID';
COMMENT ON COLUMN t_message.conversation_id IS '会话ID';
COMMENT ON COLUMN t_message.user_id IS '用户ID';
COMMENT ON COLUMN t_message.role IS '角色：user/assistant';
COMMENT ON COLUMN t_message.content IS '消息内容';
COMMENT ON COLUMN t_message.thinking_content IS '深度思考内容';
COMMENT ON COLUMN t_message.thinking_duration IS '深度思考耗时（秒）';
COMMENT ON COLUMN t_message.create_time IS '创建时间';
COMMENT ON COLUMN t_message.update_time IS '更新时间';
COMMENT ON COLUMN t_message.deleted IS '是否删除 0：正常 1：删除';

-- t_message_feedback
COMMENT ON COLUMN t_message_feedback.id IS '主键ID';
COMMENT ON COLUMN t_message_feedback.message_id IS '消息ID';
COMMENT ON COLUMN t_message_feedback.conversation_id IS '会话ID';
COMMENT ON COLUMN t_message_feedback.user_id IS '用户ID';
COMMENT ON COLUMN t_message_feedback.vote IS '投票 1：赞 -1：踩';
COMMENT ON COLUMN t_message_feedback.reason IS '反馈原因';
COMMENT ON COLUMN t_message_feedback.comment IS '反馈评论';
COMMENT ON COLUMN t_message_feedback.create_time IS '创建时间';
COMMENT ON COLUMN t_message_feedback.update_time IS '更新时间';
COMMENT ON COLUMN t_message_feedback.deleted IS '是否删除 0：正常 1：删除';

-- t_sample_question
COMMENT ON COLUMN t_sample_question.id IS 'ID';
COMMENT ON COLUMN t_sample_question.title IS '展示标题';
COMMENT ON COLUMN t_sample_question.description IS '描述或提示';
COMMENT ON COLUMN t_sample_question.question IS '示例问题内容';
COMMENT ON COLUMN t_sample_question.create_time IS '创建时间';
COMMENT ON COLUMN t_sample_question.update_time IS '更新时间';
COMMENT ON COLUMN t_sample_question.deleted IS '是否删除 0：正常 1：删除';

-- t_knowledge_base
COMMENT ON COLUMN t_knowledge_base.id IS '主键 ID';
COMMENT ON COLUMN t_knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN t_knowledge_base.embedding_model IS '嵌入模型标识';
COMMENT ON COLUMN t_knowledge_base.collection_name IS 'Collection名称';
COMMENT ON COLUMN t_knowledge_base.created_by IS '创建人';
COMMENT ON COLUMN t_knowledge_base.updated_by IS '修改人';
COMMENT ON COLUMN t_knowledge_base.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_base.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_base.deleted IS '是否删除 0：正常 1：删除';

-- t_knowledge_document
COMMENT ON COLUMN t_knowledge_document.id IS 'ID';
COMMENT ON COLUMN t_knowledge_document.kb_id IS '知识库ID';
COMMENT ON COLUMN t_knowledge_document.doc_name IS '文档名称';
COMMENT ON COLUMN t_knowledge_document.enabled IS '是否启用 1：启用 0：禁用';
COMMENT ON COLUMN t_knowledge_document.chunk_count IS '分块数量';
COMMENT ON COLUMN t_knowledge_document.file_url IS '文件存储路径';
COMMENT ON COLUMN t_knowledge_document.file_type IS '文件类型';
COMMENT ON COLUMN t_knowledge_document.file_size IS '文件大小（字节）';
COMMENT ON COLUMN t_knowledge_document.process_mode IS '处理模式：chunk/pipeline';
COMMENT ON COLUMN t_knowledge_document.status IS '状态：pending/running/success/failed';
COMMENT ON COLUMN t_knowledge_document.source_type IS '来源类型：file/url';
COMMENT ON COLUMN t_knowledge_document.source_location IS '来源地址';
COMMENT ON COLUMN t_knowledge_document.schedule_enabled IS '是否启用定时刷新';
COMMENT ON COLUMN t_knowledge_document.schedule_cron IS '定时表达式';
COMMENT ON COLUMN t_knowledge_document.chunk_strategy IS '分块策略';
COMMENT ON COLUMN t_knowledge_document.chunk_config IS '分块配置JSON';
COMMENT ON COLUMN t_knowledge_document.pipeline_id IS 'Pipeline ID';
COMMENT ON COLUMN t_knowledge_document.created_by IS '创建人';
COMMENT ON COLUMN t_knowledge_document.updated_by IS '修改人';
COMMENT ON COLUMN t_knowledge_document.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_document.deleted IS '是否删除 0：正常 1：删除';

-- t_knowledge_chunk
COMMENT ON COLUMN t_knowledge_chunk.id IS 'ID';
COMMENT ON COLUMN t_knowledge_chunk.kb_id IS '知识库ID';
COMMENT ON COLUMN t_knowledge_chunk.doc_id IS '文档ID';
COMMENT ON COLUMN t_knowledge_chunk.chunk_index IS '分块序号';
COMMENT ON COLUMN t_knowledge_chunk.content IS '分块内容';
COMMENT ON COLUMN t_knowledge_chunk.content_hash IS '内容哈希';
COMMENT ON COLUMN t_knowledge_chunk.char_count IS '字符数';
COMMENT ON COLUMN t_knowledge_chunk.token_count IS 'Token数';
COMMENT ON COLUMN t_knowledge_chunk.enabled IS '是否启用';
COMMENT ON COLUMN t_knowledge_chunk.created_by IS '创建人';
COMMENT ON COLUMN t_knowledge_chunk.updated_by IS '修改人';
COMMENT ON COLUMN t_knowledge_chunk.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_chunk.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_chunk.deleted IS '是否删除 0：正常 1：删除';

-- t_knowledge_document_chunk_log
COMMENT ON COLUMN t_knowledge_document_chunk_log.id IS 'ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.doc_id IS '文档ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.status IS '状态';
COMMENT ON COLUMN t_knowledge_document_chunk_log.process_mode IS '处理模式';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_strategy IS '分块策略';
COMMENT ON COLUMN t_knowledge_document_chunk_log.pipeline_id IS 'Pipeline ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.extract_duration IS '提取耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_duration IS '分块耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.embed_duration IS '向量化耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.persist_duration IS 'DB持久化耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.total_duration IS '总耗时（毫秒）';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_count IS '分块数量';
COMMENT ON COLUMN t_knowledge_document_chunk_log.error_message IS '错误信息';
COMMENT ON COLUMN t_knowledge_document_chunk_log.start_time IS '开始时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.end_time IS '结束时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.update_time IS '更新时间';

-- t_knowledge_document_schedule
COMMENT ON COLUMN t_knowledge_document_schedule.id IS 'ID';
COMMENT ON COLUMN t_knowledge_document_schedule.doc_id IS '文档ID';
COMMENT ON COLUMN t_knowledge_document_schedule.kb_id IS '知识库ID';
COMMENT ON COLUMN t_knowledge_document_schedule.cron_expr IS 'Cron表达式';
COMMENT ON COLUMN t_knowledge_document_schedule.enabled IS '是否启用';
COMMENT ON COLUMN t_knowledge_document_schedule.next_run_time IS '下次执行时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_run_time IS '上次执行时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_success_time IS '上次成功时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_status IS '上次状态';
COMMENT ON COLUMN t_knowledge_document_schedule.last_error IS '上次错误';
COMMENT ON COLUMN t_knowledge_document_schedule.last_etag IS '上次ETag';
COMMENT ON COLUMN t_knowledge_document_schedule.last_modified IS '上次修改时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_content_hash IS '上次内容哈希';
COMMENT ON COLUMN t_knowledge_document_schedule.lock_owner IS '锁持有者';
COMMENT ON COLUMN t_knowledge_document_schedule.lock_until IS '锁过期时间';
COMMENT ON COLUMN t_knowledge_document_schedule.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_schedule.update_time IS '更新时间';

-- t_knowledge_document_schedule_exec
COMMENT ON COLUMN t_knowledge_document_schedule_exec.id IS 'ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.schedule_id IS '调度ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.doc_id IS '文档ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.kb_id IS '知识库ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.status IS '状态';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.message IS '消息';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.start_time IS '开始时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.end_time IS '结束时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.file_name IS '文件名';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.file_size IS '文件大小';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.content_hash IS '内容哈希';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.etag IS 'ETag';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.last_modified IS '最后修改时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.update_time IS '更新时间';

-- t_intent_node
COMMENT ON COLUMN t_intent_node.id IS '自增主键';
COMMENT ON COLUMN t_intent_node.kb_id IS '知识库ID';
COMMENT ON COLUMN t_intent_node.intent_code IS '业务唯一标识';
COMMENT ON COLUMN t_intent_node.name IS '展示名称';
COMMENT ON COLUMN t_intent_node.level IS '层级 0：DOMAIN 1：CATEGORY 2：TOPIC';
COMMENT ON COLUMN t_intent_node.parent_code IS '父节点标识';
COMMENT ON COLUMN t_intent_node.description IS '语义描述';
COMMENT ON COLUMN t_intent_node.examples IS '示例问题';
COMMENT ON COLUMN t_intent_node.collection_name IS '关联的Collection名称';
COMMENT ON COLUMN t_intent_node.top_k IS '知识库检索TopK';
COMMENT ON COLUMN t_intent_node.mcp_tool_id IS 'MCP工具ID';
COMMENT ON COLUMN t_intent_node.kind IS '类型 0：RAG知识库类 1：SYSTEM系统交互类';
COMMENT ON COLUMN t_intent_node.prompt_snippet IS '提示词片段';
COMMENT ON COLUMN t_intent_node.prompt_template IS '提示词模板';
COMMENT ON COLUMN t_intent_node.param_prompt_template IS '参数提取提示词模板（MCP模式专属）';
COMMENT ON COLUMN t_intent_node.sort_order IS '排序字段';
COMMENT ON COLUMN t_intent_node.enabled IS '是否启用 1：启用 0：禁用';
COMMENT ON COLUMN t_intent_node.create_by IS '创建人';
COMMENT ON COLUMN t_intent_node.update_by IS '修改人';
COMMENT ON COLUMN t_intent_node.create_time IS '创建时间';
COMMENT ON COLUMN t_intent_node.update_time IS '修改时间';
COMMENT ON COLUMN t_intent_node.deleted IS '是否删除 0：正常 1：删除';

-- t_query_term_mapping
COMMENT ON COLUMN t_query_term_mapping.id IS 'ID';
COMMENT ON COLUMN t_query_term_mapping.domain IS '领域';
COMMENT ON COLUMN t_query_term_mapping.source_term IS '源词';
COMMENT ON COLUMN t_query_term_mapping.target_term IS '目标词';
COMMENT ON COLUMN t_query_term_mapping.match_type IS '匹配类型 1：精确 2：模糊';
COMMENT ON COLUMN t_query_term_mapping.priority IS '优先级';
COMMENT ON COLUMN t_query_term_mapping.enabled IS '是否启用';
COMMENT ON COLUMN t_query_term_mapping.remark IS '备注';
COMMENT ON COLUMN t_query_term_mapping.create_by IS '创建人';
COMMENT ON COLUMN t_query_term_mapping.update_by IS '修改人';
COMMENT ON COLUMN t_query_term_mapping.create_time IS '创建时间';
COMMENT ON COLUMN t_query_term_mapping.update_time IS '修改时间';
COMMENT ON COLUMN t_query_term_mapping.deleted IS '是否删除 0：正常 1：删除';

-- t_rag_trace_run
COMMENT ON COLUMN t_rag_trace_run.id IS 'ID';
COMMENT ON COLUMN t_rag_trace_run.trace_id IS '全局链路ID';
COMMENT ON COLUMN t_rag_trace_run.trace_name IS '链路名称';
COMMENT ON COLUMN t_rag_trace_run.entry_method IS '入口方法';
COMMENT ON COLUMN t_rag_trace_run.conversation_id IS '会话ID';
COMMENT ON COLUMN t_rag_trace_run.task_id IS '任务ID';
COMMENT ON COLUMN t_rag_trace_run.user_id IS '用户ID';
COMMENT ON COLUMN t_rag_trace_run.status IS 'RUNNING/SUCCESS/ERROR';
COMMENT ON COLUMN t_rag_trace_run.error_message IS '错误信息';
COMMENT ON COLUMN t_rag_trace_run.start_time IS '开始时间';
COMMENT ON COLUMN t_rag_trace_run.end_time IS '结束时间';
COMMENT ON COLUMN t_rag_trace_run.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN t_rag_trace_run.extra_data IS '扩展字段(JSON)';
COMMENT ON COLUMN t_rag_trace_run.create_time IS '创建时间';
COMMENT ON COLUMN t_rag_trace_run.update_time IS '更新时间';
COMMENT ON COLUMN t_rag_trace_run.deleted IS '是否删除';

-- t_rag_trace_node
COMMENT ON COLUMN t_rag_trace_node.id IS 'ID';
COMMENT ON COLUMN t_rag_trace_node.trace_id IS '所属链路ID';
COMMENT ON COLUMN t_rag_trace_node.node_id IS '节点ID';
COMMENT ON COLUMN t_rag_trace_node.parent_node_id IS '父节点ID';
COMMENT ON COLUMN t_rag_trace_node.depth IS '节点深度';
COMMENT ON COLUMN t_rag_trace_node.node_type IS '节点类型';
COMMENT ON COLUMN t_rag_trace_node.node_name IS '节点名称';
COMMENT ON COLUMN t_rag_trace_node.class_name IS '类名';
COMMENT ON COLUMN t_rag_trace_node.method_name IS '方法名';
COMMENT ON COLUMN t_rag_trace_node.status IS 'RUNNING/SUCCESS/ERROR';
COMMENT ON COLUMN t_rag_trace_node.error_message IS '错误信息';
COMMENT ON COLUMN t_rag_trace_node.start_time IS '开始时间';
COMMENT ON COLUMN t_rag_trace_node.end_time IS '结束时间';
COMMENT ON COLUMN t_rag_trace_node.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN t_rag_trace_node.extra_data IS '扩展字段(JSON)';
COMMENT ON COLUMN t_rag_trace_node.create_time IS '创建时间';
COMMENT ON COLUMN t_rag_trace_node.update_time IS '更新时间';
COMMENT ON COLUMN t_rag_trace_node.deleted IS '是否删除';

-- t_ingestion_pipeline
COMMENT ON COLUMN t_ingestion_pipeline.id IS 'ID';
COMMENT ON COLUMN t_ingestion_pipeline.name IS '流水线名称';
COMMENT ON COLUMN t_ingestion_pipeline.description IS '流水线描述';
COMMENT ON COLUMN t_ingestion_pipeline.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_pipeline.updated_by IS '更新人';
COMMENT ON COLUMN t_ingestion_pipeline.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_pipeline.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_pipeline.deleted IS '是否删除 0：正常 1：删除';

-- t_ingestion_pipeline_node
COMMENT ON COLUMN t_ingestion_pipeline_node.id IS 'ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.pipeline_id IS '流水线ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_id IS '节点标识(同一流水线内唯一)';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_pipeline_node.next_node_id IS '下一个节点ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.settings_json IS '节点配置JSON';
COMMENT ON COLUMN t_ingestion_pipeline_node.condition_json IS '条件JSON';
COMMENT ON COLUMN t_ingestion_pipeline_node.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_pipeline_node.updated_by IS '更新人';
COMMENT ON COLUMN t_ingestion_pipeline_node.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_pipeline_node.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_pipeline_node.deleted IS '是否删除 0：正常 1：删除';

-- t_ingestion_task
COMMENT ON COLUMN t_ingestion_task.id IS 'ID';
COMMENT ON COLUMN t_ingestion_task.pipeline_id IS '流水线ID';
COMMENT ON COLUMN t_ingestion_task.source_type IS '来源类型';
COMMENT ON COLUMN t_ingestion_task.source_location IS '来源地址或URL';
COMMENT ON COLUMN t_ingestion_task.source_file_name IS '原始文件名';
COMMENT ON COLUMN t_ingestion_task.status IS '任务状态';
COMMENT ON COLUMN t_ingestion_task.chunk_count IS '分块数量';
COMMENT ON COLUMN t_ingestion_task.error_message IS '错误信息';
COMMENT ON COLUMN t_ingestion_task.logs_json IS '节点日志JSON';
COMMENT ON COLUMN t_ingestion_task.metadata_json IS '扩展元数据JSON';
COMMENT ON COLUMN t_ingestion_task.started_at IS '开始时间';
COMMENT ON COLUMN t_ingestion_task.completed_at IS '完成时间';
COMMENT ON COLUMN t_ingestion_task.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_task.updated_by IS '更新人';
COMMENT ON COLUMN t_ingestion_task.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_task.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_task.deleted IS '是否删除 0：正常 1：删除';

-- t_ingestion_task_node
COMMENT ON COLUMN t_ingestion_task_node.id IS 'ID';
COMMENT ON COLUMN t_ingestion_task_node.task_id IS '任务ID';
COMMENT ON COLUMN t_ingestion_task_node.pipeline_id IS '流水线ID';
COMMENT ON COLUMN t_ingestion_task_node.node_id IS '节点标识';
COMMENT ON COLUMN t_ingestion_task_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_task_node.node_order IS '节点顺序';
COMMENT ON COLUMN t_ingestion_task_node.status IS '节点状态';
COMMENT ON COLUMN t_ingestion_task_node.duration_ms IS '执行耗时(毫秒)';
COMMENT ON COLUMN t_ingestion_task_node.message IS '节点消息';
COMMENT ON COLUMN t_ingestion_task_node.error_message IS '错误信息';
COMMENT ON COLUMN t_ingestion_task_node.output_json IS '节点输出JSON(全量)';
COMMENT ON COLUMN t_ingestion_task_node.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_task_node.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_task_node.deleted IS '是否删除 0：正常 1：删除';

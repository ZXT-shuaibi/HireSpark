-- PostgreSQL Initial Data for Ragent

INSERT INTO t_user (id, username, password, role, avatar, create_time, update_time, deleted)
VALUES (2001523723396308993, 'admin', 'pbkdf2$185000$cmFnZW50LWFkbWluLXNhbHQ=$qJiH36D2Y409J76h6KJ7jjgLx+p/aR4LIPqN5NCCJOs=', 'admin', 'https://static.deepseek.com/user-avatar/G_6cuD8GbD53VwGRwisvCsZ6', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO t_sample_question (id, title, description, question, deleted)
VALUES
('930000000000000001', '简历诊断', '分析简历与目标岗位的匹配度', '请帮我分析这份简历和Java后端岗位JD的匹配度', 0),
('930000000000000002', '模拟面试', '基于简历和JD生成模拟面试', '请基于我的简历和目标JD开始一次后端开发模拟面试', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO t_intent_node (
    id, intent_code, name, level, parent_code, description, examples, kind, sort_order, enabled, deleted
)
VALUES
('930000000000000101', 'career', '求职成长', 0, NULL, '简历诊断、JD对齐、简历优化、模拟面试与复盘', '简历怎么优化|帮我模拟面试|分析JD匹配度', 1, 30, 1, 0),
('930000000000000102', 'career.resume', '简历优化', 1, 'career', '围绕目标岗位优化简历表达', '帮我优化简历|这段项目经历怎么写', 1, 31, 1, 0),
('930000000000000103', 'career.interview', '模拟面试', 1, 'career', '基于简历和JD进行模拟面试训练', '开始模拟面试|追问我的项目经历', 1, 32, 1, 0)
ON CONFLICT (id) DO NOTHING;

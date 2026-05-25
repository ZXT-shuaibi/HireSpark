# 面试恢复与快照

## 适用场景

修改面试缓存、快照、CAS、轮次归档、恢复范围、补偿重建时先读本文件。

## 当前实现路径

- 恢复入口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSessionRecoveryServiceImpl.java`
- 热快照接口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSessionHotSnapshotService.java`
- Redis 热快照：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/RedisInterviewSessionHotSnapshotService.java`
- 去抖刷新：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewHotSnapshotRefreshCoordinator.java`
- 单调性保护：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSnapshotMonotonicGuard.java`
- 恢复范围：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewRecoveryScope.java`
- 冷快照表对象：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/InterviewSessionSnapshotDO.java`
- 轮次归档：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/InterviewTurnArchiveDO.java`
- 测试：`bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionRecoveryTest.java`

## 分层语义

| 层 | 存储 | 职责 |
| --- | --- | --- |
| 热快照 | Redis | 当前题号、轮次状态、评分进度、短期运行态 |
| 冷快照 | PostgreSQL JSONB | 会话计划、已答轮次、恢复材料、版本 |
| 轮次归档 | PostgreSQL | 不可变 turn 日志，用于审计和回放 |

## 恢复范围

- `FLOW_ONLY`：只恢复当前流程状态。
- `SCORE_ONLY`：只恢复评分相关状态。
- `PLAYBACK_ONLY`：只恢复回放需要的轮次材料。
- `HOT_RUNTIME`：优先恢复 Redis 热运行态。
- `FULL_RUNTIME`：组合热态、冷快照和轮次归档重建。

## 并发与单调性

- 冷快照写入必须带版本 CAS，旧版本不能覆盖新版本。
- `lastTurnSeq`、`archiveWatermark`、`scoreCount` 不允许倒退。
- 同一轮次高频刷新应由去抖协调器合并，避免频繁写 Redis。
- 恢复失败不能吞掉原始答案，必须保留可重试边界。

## 修改检查

- 新增快照字段时，同步冷热快照、归档、恢复服务和测试。
- 修改恢复逻辑时，覆盖 Redis 丢失、冷快照旧版本、归档缺失、重复恢复四类场景。
- 不要引入 MongoDB，当前恢复体系落在 Redis + PostgreSQL JSONB。

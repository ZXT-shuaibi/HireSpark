# Single-flight 与 AI Guard

## 适用场景

修改 Career LLM 调用、重复请求去重、跨节点等待、结果回放、超时、降级、Trace 时先读本文件。

## 当前实现路径

- LLM 统一入口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightLlmService.java`
- LLM 入口实现：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightLlmServiceImpl.java`
- Single-flight 服务：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightServiceImpl.java`
- Redis Lua 协调：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightRedisCoordinator.java`
- 心跳管理：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightHeartbeatManager.java`
- 本地回放缓存：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightLocalReplayCache.java`
- AI Guard：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/guard/CareerAiGuardService.java`
- 记录表：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/CareerSingleFlightRecordDO.java`
- 测试：`bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerSingleFlightTest.java`
- 测试：`bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerAiGuardServiceTest.java`

## 调用原则

- Career 域内不要直接调用底层模型服务，应统一走 `CareerSingleFlightLlmService`。
- `scene` 要稳定，影响 AI Guard 配置、Single-flight key、Agent Trace 和统计口径。
- key 由场景、业务对象、输入摘要等稳定字段组成，不要加入随机值。
- Owner 负责执行 AI 调用并持续心跳，Follower 在等待窗口内拿回放结果。
- HYBRID 模式下 Redis 异常应降级到本地模式，不能阻断主链路。

## 状态语义

| 状态 | 语义 |
| --- | --- |
| `PENDING` | 请求已登记但尚未开始执行 |
| `RUNNING` | Owner 正在执行 AI 调用 |
| `SUCCESS` | 有可回放结果 |
| `FAILED` | 调用失败，按失败类型和 TTL 管理 |
| `REPLAY` | Follower 或重复请求复用已有结果 |

## AI Guard 要点

- 按评分、追问、出题、优化、HyDE 等 scene 做隔离配置。
- 超时、重试、熔断和并发保护属于运行时契约，业务服务不能绕过。
- Guard 失败要保留明确错误类型，便于补偿或人工重试。

## 修改检查

- 新增 LLM 场景时，同步 scene 常量、prompt、Single-flight key、Trace 和测试。
- 修改等待时间或 TTL 时，覆盖 Owner 成功、Owner 失败、Follower 等待超时、Redis 降级四类测试。
- 失败结果不应长期缓存，成功结果可进入本地 L1 回放缓存。

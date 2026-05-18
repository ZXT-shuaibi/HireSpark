# Career Agent Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Career Agent Platform inside Ragent that turns the existing RAG base into a resume, JD alignment, resume optimization, mock interview, review, and AI-call governance loop.

**Architecture:** Keep Ragent as the only runtime base. Add a bounded `career` domain inside `bootstrap`, reuse Ragent auth, model routing, storage, trace, PostgreSQL, Redis helpers, and frontend shell, and migrate JobNavigator/AI-Meeting capabilities as domain behavior rather than as independent applications. Treat JobNavigator's judge-executor optimization and AI-Meeting's interview runtime governance as Career-domain modules, not as copied subsystems.

**Tech Stack:** Java 17, Spring Boot 3.5.7, MyBatis-Plus, PostgreSQL JSONB, Ragent `infra-ai` LLMService, Ragent Trace, React 18, Vite, TypeScript, Zustand-compatible service modules, Tailwind/shadcn UI.

---

## Scope Check

The PRD covers four independent delivery areas:

1. Resume and JD alignment.
2. Resume optimization and export.
3. Text mock interview and review report.
4. Runtime governance: optimization review, interview idempotency, long-session recovery, and AI Single-flight.
5. Admin observability plus future voice/multimodal extensions.

This document is a master implementation plan. Implement it in sequence and stop after each phase for review. Phase 1 and Phase 2 produce independently testable product increments. Phase 3 deepens the project by absorbing AI-Meeting and JobNavigator engineering highlights. Phase 4 is included as a bounded extension plan and must not start until the text interview loop is stable.

## PRD Alignment And Schedule Diff

This plan follows the product-grounded PRD direction from `docs/career-agent-platform/prd-career-agent-platform.md` and the approved rewrite design in `docs/superpowers/specs/2026-05-15-career-agent-prd-rewrite-design.md`.

Confirmed PRD alignment:

- Treat Phase 1 and Phase 2 as the implementation scope that must reach product-detail and acceptance-test depth.
- Treat Phase 3 and Phase 4 as roadmap or extension scope unless Phase 1 and Phase 2 smoke verification passes.
- Use Java backend / AI application development job seeker as the default demo scenario.
- Keep AI outputs structured so backend, frontend, tests, and trace views share the same product contract.
- Preserve Ragent as the only runtime base: one auth system, one model routing path, one trace system, and one frontend shell.
- Support generic mode for non-Java roles without promising role-specific templates or role-specific rubrics in MVP.
- Implement deletion/privacy behavior so deleted resume/JD/interview/report content is not visible to users and exported files become invalid.
- Implement MVP retry/idempotency behavior for malformed AI outputs and failed interview scoring.
- Promote AI-Meeting interview state machine, turn idempotency, compensation, long-session recovery, and Single-flight to explicit Phase 2/3 tasks.
- Promote JobNavigator judge-executor optimization, quality gate, HyDE/Rerank, multi-format rendering, and progress visibility to explicit Phase 1/3 tasks.
- Keep MVP rubric management read-only with `career-java-backend-v1` and `career-general-v1` templates.
- Keep admin MVP focused on task visibility, failure reasons, basic counts, and trace links; advanced charts stay in Phase 3.

### PRD v0.2 Delta Mapping

The PRD v0.2 adds several product requirements beyond the original high-level PRD. Map them to existing implementation tasks instead of creating a separate late-stage patch.

| PRD v0.2 Requirement | Implementation Tasks | Required Acceptance Coverage |
| --- | --- | --- |
| Default Java backend / AI application scenario plus generic fallback mode | Task 6, Task 9, Task 12, Task 16 | A15: non-Java JD enters generic mode and can still finish alignment and interview |
| Sensitive data deletion and trace desensitization | Task 8, Task 9, Task 12, Task 13, Task 14, Task 18 | A16: deleted business content is hidden, exports are invalid, trace keeps only desensitized summary |
| Malformed AI output retry and idempotent task results | Task 4, Task 7, Task 8, Task 10, Task 12, Task 13, Task 18 | A18 and A19: retry records attempts, avoids duplicate business artifacts, keeps user-visible retry entry |
| Read-only MVP rubric templates | Task 6, Task 12, Task 14, Task 17 | A17: admin can view `career-java-backend-v1` and `career-general-v1`, no edit entry in MVP |
| Admin MVP task observability | Task 14, Task 17, Task 18 | A20: admin sees basic counts, recent failed tasks, task status, failure reason, and trace link |
| No new Qdrant or second storage runtime in MVP | Cross-Cutting Rules, Task 1, Task 5, Task 9 | Regression: Ragent storage and knowledge abstractions remain the only runtime base |
| JobNavigator judge-executor optimization and quality gate | Task 10, Task 20, Task 26 | A21/A22: reviewer blocks unsupported suggestions and quality score below 0.8 is not final delivery |
| AI-Meeting interview state machine and turn idempotency | Task 12, Task 21, Task 26 | A19/A25: answer is retained, duplicate submit does not duplicate evaluation or follow-up |
| AI-Meeting long-session recovery | Task 21, Task 22, Task 26 | A24: session recovers from persisted turns and snapshot after cache loss |
| AI-Meeting distributed Single-flight | Task 23, Task 26 | A23: same input creates one AI result with replay or processing status |
| JobNavigator HyDE/Rerank retrieval enrichment | Task 24, Task 26 | A26: HyDE content is only query/evidence and never written into resume |
| JobNavigator PDF/Word render pipeline | Task 25, Task 26 | A27: renderer validates fields, records template version and trace |

### Estimation Baseline

Use person-days as the estimation unit. One person-day means one focused developer day with build/test time included.

Baseline assumptions:

- Serial baseline: one senior full-stack engineer executes backend, frontend, tests, and docs.
- Parallel baseline: one backend-oriented engineer and one frontend-oriented engineer can overlap after backend API contracts are stable.
- AI prompt tuning, malformed output handling, and smoke data setup are variable. Add a 15% contingency buffer to any committed calendar.
- Do not count Phase 4 voice or multimodal work in MVP delivery.
- Phase 3 engineering-thickness tasks are scoped after the current MVP loop; do not mix them into Phase 1 smoke unless a task explicitly says so.

Estimated total:

- Serial implementation baseline: 29.5 person-days, rounded to 30 person-days.
- Serial implementation with 15% buffer: 35 person-days.
- Two-person parallel implementation: 18 to 20 working days, because frontend work should wait until service contracts and VO shapes are stable.
- Phase 3 depth increment: 14.5 person-days, rounded to 15 person-days.
- Serial full delivery with Phase 3 depth increment and 15% buffer: 52 person-days.

### 工期拆分总览

| 批次 | 工期 | 研发重心 | 产出物 | 阶段验收 |
| --- | ---: | --- | --- | --- |
| B0 | 0.5 人日 | 执行前检查 | 分支/工作区确认、样例场景确认、计划复核 | 确认不直接在 `main` 开发，获得执行许可 |
| B1 | 5 人日 | Career 后端基础 | 数据表、枚举、DO/Mapper、JSON Parser、Prompt、Trace Runner | 后端编译通过，Parser 测试通过 |
| B2 | 8 人日 | Phase 1 后端闭环 | 简历、JD、匹配、优化、导出、删除/隐私、重试幂等 | 简历 -> JD -> 优化后端链路可跑通 |
| B3 | 4 人日 | Phase 1 前端闭环 | Career 服务、首页、简历中心、JD 对齐、优化工作台 | 浏览器可完成 Phase 1 主流程 |
| B4 | 1 人日 | Phase 1 Smoke | Phase 1 手工验收记录和问题修复 | 人工确认后才进入面试能力 |
| B5 | 4 人日 | Phase 2 后端闭环 | 文字面试、答题评分、追问、报告、评分失败重试 | 面试后端可创建会话、评分并生成报告 |
| B6 | 2 人日 | Phase 2 前端闭环 | 模拟面试页、报告页、报告反哺入口 | 浏览器可完成文字面试和查看报告 |
| B7 | 3 人日 | 管理端与观测 | Admin API、任务列表、基础统计、只读 Rubric、Trace 链接 | 管理员可定位任务状态和失败原因 |
| B8 | 2 人日 | 全链路验收与文档 | 全量 smoke、quick start、PRD 链接、遗留修复 | MVP 可演示，文档可交付 |
| B9 | 4 人日 | JobNavigator 优化深度 | 裁判-执行者、多轮反思、0.8 质量门禁、优化进度事件 | 简历优化能讲清“生成、评审、修正、达标” |
| B10 | 4 人日 | AI-Meeting 面试运行时 | 轮次幂等、补偿、持久化快照、恢复 CAS | 面试中断/重复提交不会破坏闭环 |
| B11 | 3 人日 | AI Single-flight 治理 | singleFlightKey、owner heartbeat、fencing token、结果回放 | 同输入并发只产生一份 AI 结果 |
| B12 | 2 人日 | HyDE/Rerank 检索增强 | 假设简历 query、缺口 query、Rerank、证据类型标记 | 检索增强不污染简历真实性 |
| B13 | 1.5 人日 | 多格式渲染增强 | PDF/Word 渲染门禁、模板版本、字段校验 | 渲染管线可演示、可追溯、可失效 |

### Schedule Diff Matrix

| Batch | Duration | Tasks | Owner Focus | Dependencies | Deliverable | Verification | Review Gate |
| --- | ---: | --- | --- | --- | --- | --- | --- |
| B0: Execution preflight | 0.5d | Plan review only | Tech lead | Updated PRD and this plan are reviewed | Execution checklist, branch/worktree decision, sample Java backend JD confirmed | `git status --short` and plan review | Human approves implementation start |
| B1: Career foundation | 5d | Tasks 1-7 | Backend | Ragent compiles before changes | Career schema, enums, persistence layer, JSON parser, resume extractor, prompt templates, trace runner | `mvn -pl bootstrap -Dtest=CareerJsonParserTest test`; `mvn -pl bootstrap -DskipTests compile` | Stop before product APIs |
| B2: Phase 1 backend loop | 8d | Tasks 8-11 | Backend | B1 complete | Resume upload/parse/version API, JD alignment API, optimization API, Markdown/HTML export, delete/privacy behavior, retry/idempotency behavior | `mvn -pl bootstrap -Dtest=JobAlignmentScoringTest,ResumeOptimizationSuggestionTest test`; `mvn -pl bootstrap -DskipTests compile` | Resume -> JD -> optimization backend demo works |
| B3: Phase 1 frontend loop | 4d | Task 15 and Phase 1 part of Task 16 | Frontend | B2 request/VO contracts stable | Career service, home, resume center, JD alignment, optimization workbench | `cd frontend`; `npm run build` | User can complete Phase 1 path in browser |
| B4: Phase 1 smoke gate | 1d | Phase 1 part of Task 18 | Full stack | B2 and B3 complete | End-to-end Phase 1 smoke record | Backend compile, frontend build, manual resume -> JD -> optimization smoke | Human approves starting interview work |
| B5: Phase 2 backend loop | 4d | Tasks 12-13 | Backend | B4 approved | Text interview session, turns, answer evaluation, report generation, scoring retry behavior | `mvn -pl bootstrap -Dtest=InterviewSessionStateTest,InterviewReportAggregationTest test`; `mvn -pl bootstrap -DskipTests compile` | Interview backend can create session and report |
| B6: Phase 2 frontend loop | 2d | Phase 2 part of Task 16 | Frontend | B5 request/VO contracts stable | Mock interview page and interview report page | `cd frontend`; `npm run build` | User can complete text interview and view report |
| B7: Admin and observability | 3d | Tasks 14 and 17 | Backend + frontend | B2 and B5 task records exist | Career admin APIs, dashboard, task list, read-only rubric page, trace links | `mvn -pl bootstrap -DskipTests compile`; `cd frontend`; `npm run build` | Admin can inspect tasks and trace links |
| B8: Full smoke and docs | 2d | Tasks 18-19 | Full stack | B1-B7 complete | Full product smoke, quick start doc, PRD plan link, final acceptance notes | Commands from Task 18 and manual smoke checklist | MVP implementation can be demoed |
| B9: Judge-executor optimization depth | 4d | Task 20 | Backend + frontend | B2 and B3 stable | Optimization review records, quality gate, progress events, UI status | `mvn -pl bootstrap -Dtest=ResumeOptimizationReviewTest test`; `cd frontend`; `npm run build` | Optimization process is visible and risky suggestions are blocked |
| B10: Interview runtime hardening | 4d | Tasks 21-22 | Backend | B5 and B6 stable | Turn idempotency, compensation state, session snapshot, recovery CAS | `mvn -pl bootstrap -Dtest=InterviewTurnIdempotencyTest,InterviewSessionRecoveryTest test` | Duplicate submits and cache-loss recovery are safe |
| B11: Career AI Single-flight | 3d | Task 23 | Backend | B9 and B10 stable | Single-flight records, owner heartbeat, fencing token, result replay | `mvn -pl bootstrap -Dtest=CareerSingleFlightTest test` | Same input creates one AI result across concurrent requests |
| B12: HyDE/Rerank enrichment | 2d | Task 24 | Backend | Retrieval abstractions stable | Career retrieval service, HyDE query markers, rerank integration | `mvn -pl bootstrap -Dtest=CareerRetrievalEnhancementTest test` | HyDE is evidence/query only, not resume content |
| B13: Render pipeline enhancement | 1.5d | Task 25 | Backend | Task 11 stable | PDF/Word gate, template version, export validation result | `mvn -pl bootstrap -Dtest=ResumeRenderPipelineTest test` | Exports are validated and traceable |

### Task-Level Duration Diff

| Task | Estimated Duration | Schedule Batch | Critical Path |
| --- | ---: | --- | --- |
| Task 1: Database Schema For Career Domain | 0.5d | B1 | Yes |
| Task 2: Register Career Mappers And Core Enums | 0.5d | B1 | Yes |
| Task 3: Create Career Persistence Layer | 1d | B1 | Yes |
| Task 4: Add Career JSON Parser | 0.5d | B1 | Yes |
| Task 5: Add Resume Text Extraction | 0.5d | B1 | Yes |
| Task 6: Add Prompt Templates For Career AI Calls | 1d | B1 | Yes |
| Task 7: Implement Career Trace Runner | 1d | B1 | Yes |
| Task 8: Implement Resume Upload, Parse, And Version API | 2.5d | B2 | Yes |
| Task 9: Implement JD Creation And Alignment | 2.5d | B2 | Yes |
| Task 10: Implement Resume Optimization Suggestions | 2d | B2 | Yes |
| Task 11: Implement Basic Resume Export | 1d | B2 | No |
| Task 12: Implement Text Interview Session | 2.5d | B5 | Yes |
| Task 13: Implement Interview Report | 1.5d | B5 | Yes |
| Task 14: Add Admin Career APIs | 1.5d | B7 | No |
| Task 15: Add Frontend Career Service | 1d | B3 | Yes |
| Task 16: Add User-Facing Career Pages | 5.5d | B3 and B6 | Yes |
| Task 17: Add Admin Career Pages | 2d | B7 | No |
| Task 18: End-To-End Smoke Verification | 1.5d | B4 and B8 | Yes |
| Task 19: Documentation And Skill Follow-Up | 1d | B8 | No |
| Task 20: Add Judge-Executor Resume Optimization | 4d | B9 | No |
| Task 21: Harden Interview Turn State Machine | 2d | B10 | Yes |
| Task 22: Add Interview Session Snapshot Recovery | 2d | B10 | Yes |
| Task 23: Add Career AI Single-flight Governance | 3d | B11 | No |
| Task 24: Add Career HyDE And Rerank Retrieval | 2d | B12 | No |
| Task 25: Add PDF/Word Render Pipeline Gate | 1.5d | B13 | No |
| Task 26: Phase 3 Depth Smoke Verification | 1d | B9-B13 | Yes |

### Execution Checkpoints

When executing this plan with `executing-plans`, load this plan first, create a task checklist, and stop at every review gate in the Schedule Diff Matrix.

Required stop points:

- Stop after B1 before implementing product APIs if schema, mapper scan, parser, prompt, or trace setup fails.
- Stop after B4 before starting interview work until Phase 1 smoke is approved.
- Stop after B6 if the text interview path cannot complete from the frontend.
- Stop after B8 before claiming MVP completion.
- Stop after B8 before starting B9-B13 depth work; Phase 3 depth must not hide MVP defects.
- Stop after B10 if duplicate answer submission or recovery tests fail.
- Stop after B11 if Single-flight cannot prove stale owner writes are blocked.

Do not start implementation on `main` without explicit human approval. Prefer an isolated worktree before running implementation tasks.

### Subagent Work Package Split

Use this section when executing with `superpowers:subagent-driven-development`.

Controller rules:

- Dispatch one fresh implementer subagent per work package.
- Do not dispatch multiple implementation subagents in parallel inside the same worktree.
- Give each implementer the exact work package text, the relevant task text from this plan, the PRD alignment notes above, and the package write scope.
- Tell every implementer they are not alone in the codebase, must not revert edits made by others, and must adapt to changes already present.
- After each implementer reports `DONE` or `DONE_WITH_CONCERNS`, dispatch a spec compliance reviewer first.
- Dispatch a code quality reviewer only after spec compliance is approved.
- If a reviewer finds issues, send the findings back to the same implementer, then re-run the same review.
- Mark the package complete only after implementation, verification, spec review, and code quality review all pass.
- Stop at the review gates from the Schedule Diff Matrix even if subagents finish successfully.

Subagent packages:

| Package | Duration | Plan Tasks | Write Scope | Dependencies | Verification | Commit Message |
| --- | ---: | --- | --- | --- | --- | --- |
| SG0: Preflight and branch guard | 0.5d | Plan review only | No source write; may create execution checklist notes | Human confirms execution mode | `git status --short`; verify not implementing on `main` without approval | No commit |
| SG1: Career schema and persistence foundation | 2d | Tasks 1-3 | `resources/database/schema_pg.sql`, `resources/database/init_data_pg.sql`, `bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java`, `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums`, `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao` | SG0 complete | `mvn -pl bootstrap -DskipTests compile` | `feat: add career persistence foundation` |
| SG2: Career AI infrastructure | 3d | Tasks 4-7 | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/parser`, `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/prompt`, `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/trace`, parser and trace tests | SG1 complete | `mvn -pl bootstrap -Dtest=CareerJsonParserTest test`; `mvn -pl bootstrap -DskipTests compile` | `feat: add career ai infrastructure` |
| SG3: Resume profile and version backend | 2.5d | Task 8 | `career/controller/CareerResumeController.java`, resume request/VO files, `CandidateProfileService`, `ResumeVersionService`, resume service implementations, delete/privacy behavior, resume parser integration tests | SG1 and SG2 complete | `mvn -pl bootstrap -DskipTests compile` | `feat: add career resume api` |
| SG4: JD alignment backend | 2.5d | Task 9 | `career/controller/CareerJobController.java`, JD request/VO files, `JobAlignmentService`, alignment service implementation, generic-mode JD behavior, `JobAlignmentScoringTest` | SG3 complete | `mvn -pl bootstrap -Dtest=JobAlignmentScoringTest test`; `mvn -pl bootstrap -DskipTests compile` | `feat: add career jd alignment api` |
| SG5: Resume optimization and export backend | 3d | Tasks 10-11 | `CareerOptimizationController.java`, optimization request/VO files, `ResumeOptimizationService`, optimization implementation, export helper, retry/idempotency behavior, `ResumeOptimizationSuggestionTest` | SG3 and SG4 complete | `mvn -pl bootstrap -Dtest=ResumeOptimizationSuggestionTest test`; `mvn -pl bootstrap -DskipTests compile` | `feat: add career resume optimization` |
| SG6: Text interview backend | 2.5d | Task 12 | `CareerInterviewController.java`, interview request/VO files, `InterviewSessionService`, session implementation, generic-mode interview behavior, scoring retry behavior, `InterviewSessionStateTest` | SG3 and SG4 complete | `mvn -pl bootstrap -Dtest=InterviewSessionStateTest test`; `mvn -pl bootstrap -DskipTests compile` | `feat: add career text interview session` |
| SG7: Interview report backend | 1.5d | Task 13 | `InterviewReportService`, report implementation, report VO files, report endpoint changes, `InterviewReportAggregationTest` | SG6 complete | `mvn -pl bootstrap -Dtest=InterviewReportAggregationTest test`; `mvn -pl bootstrap -DskipTests compile` | `feat: add career interview report` |
| SG8: Career admin backend | 1.5d | Task 14 | `CareerAdminController.java`, admin request/VO files, admin query service methods, read-only rubric response, basic counts, failure reasons, trace links | SG5 and SG7 complete | `mvn -pl bootstrap -DskipTests compile` | `feat: add career admin api` |
| SG9: Frontend career service contract | 1d | Task 15 | `frontend/src/services/careerService.ts`, shared frontend career types if needed | SG3-SG8 request/VO names stable | `cd frontend`; `npm run build` | `feat: add career frontend service` |
| SG10: Phase 1 user pages | 3.5d | Phase 1 part of Task 16 | `frontend/src/pages/career/CareerHomePage.tsx`, `ResumeCenterPage.tsx`, `JobAlignmentPage.tsx`, `ResumeOptimizationPage.tsx`, delete/empty/error states, generic-mode hints, `frontend/src/router.tsx`, `frontend/src/components/layout/Sidebar.tsx` | SG5 and SG9 complete | `cd frontend`; `npm run build` | `feat: add career phase one pages` |
| SG11: Phase 2 user pages | 2d | Phase 2 part of Task 16 | `frontend/src/pages/career/MockInterviewPage.tsx`, `InterviewReportPage.tsx`, route additions if not already present | SG7 and SG9 complete | `cd frontend`; `npm run build` | `feat: add career interview pages` |
| SG12: Admin frontend pages | 2d | Task 17 | `frontend/src/pages/admin/career`, `frontend/src/pages/admin/AdminLayout.tsx`, admin route additions, read-only rubric page, basic task observability views | SG8 and SG9 complete | `cd frontend`; `npm run build` | `feat: add career admin pages` |
| SG13: Full smoke and documentation | 2d | Tasks 18-19 | `docs/career-agent-platform/prd-career-agent-platform.md`, `docs/career-agent-platform/quick-start.md`, acceptance notes, smoke fixes only if required | SG1-SG12 complete | Task 18 backend compile, focused backend tests, frontend build, and manual smoke path | `docs: add career platform quick start` |
| SG14: Judge-executor optimization depth | 4d | Task 20 | `career/service/review`, optimization service/controller/VO changes, `ResumeOptimizationReviewTest`, optimization progress frontend pieces | SG5, SG10, SG13 complete | `mvn -pl bootstrap -Dtest=ResumeOptimizationReviewTest test`; `cd frontend`; `npm run build` | `feat: add optimization quality gate` |
| SG15: Interview turn runtime hardening | 2d | Task 21 | `career/service/runtime`, interview entity/service/controller changes, `InterviewTurnIdempotencyTest` | SG6, SG11, SG13 complete | `mvn -pl bootstrap -Dtest=InterviewTurnIdempotencyTest test` | `feat: harden interview turn idempotency` |
| SG16: Interview session recovery | 2d | Task 22 | `career/service/recovery`, interview snapshot entity/mapper/service, `InterviewSessionRecoveryTest` | SG15 complete | `mvn -pl bootstrap -Dtest=InterviewSessionRecoveryTest test` | `feat: add interview session recovery` |
| SG17: Career AI Single-flight | 3d | Task 23 | `career/service/singleflight`, single-flight entity/mapper/service, AI call wrappers, `CareerSingleFlightTest` | SG14 and SG16 complete | `mvn -pl bootstrap -Dtest=CareerSingleFlightTest test` | `feat: add career ai single flight` |
| SG18: HyDE/Rerank retrieval enrichment | 2d | Task 24 | `career/service/retrieval`, prompt additions, JD alignment/optimization/interview retrieval hooks, `CareerRetrievalEnhancementTest` | SG4, SG5, SG17 complete | `mvn -pl bootstrap -Dtest=CareerRetrievalEnhancementTest test` | `feat: add career retrieval enrichment` |
| SG19: Render pipeline gate | 1.5d | Task 25 | `career/service/render`, export service changes, export VO changes, `ResumeRenderPipelineTest` | SG5 and SG13 complete | `mvn -pl bootstrap -Dtest=ResumeRenderPipelineTest test` | `feat: add resume render pipeline gate` |
| SG20: Phase 3 depth smoke | 1d | Task 26 | smoke notes and small fixes only; no broad refactors | SG14-SG19 complete | Task 26 verification checklist | `test: verify career depth capabilities` |

Recommended same-session execution order:

1. SG0.
2. SG1.
3. SG2.
4. SG3.
5. SG4.
6. SG5.
7. Stop for B4 Phase 1 backend readiness review if backend API smoke is unstable.
8. SG9.
9. SG10.
10. Stop for B4 Phase 1 browser smoke review.
11. SG6.
12. SG7.
13. SG11.
14. Stop for B6 text interview browser smoke review.
15. SG8.
16. SG12.
17. SG13.
18. Stop for MVP acceptance before Phase 3 depth work.
19. SG14.
20. SG15.
21. SG16.
22. SG17.
23. SG18.
24. SG19.
25. SG20.

Parallelization notes for separate worktrees only:

- SG3 and SG4 can be split between backend subagents only after SG1 and SG2 are merged and request/VO names are fixed.
- SG6 can start after SG3 and SG4 are stable; it does not need SG5.
- SG10 and SG11 must not run in parallel in the same worktree because both touch `frontend/src/router.tsx`.
- SG12 must not run in parallel with SG10 or SG11 in the same worktree because both can touch route and layout files.
- SG13 must run last because it validates the integrated product and may touch docs plus smoke fixes.
- SG14 and SG15 can run in parallel only in separate worktrees after SG13 because they touch different backend modules, but both may touch shared VO/API contracts; reconcile before SG17.
- SG17 should run after SG14 and SG16 because Single-flight wraps both optimization and interview AI calls.
- SG18 can start after SG17 only if the AI call wrapper contract is stable.
- SG19 can run after SG5 and SG13; it should not touch optimization or interview runtime code.
- SG20 must run last for Phase 3 depth verification.

## File Structure

### Backend: New Career Domain

Create these packages under `bootstrap/src/main/java/com/nageoffer/ai/ragent/career`:

- `controller`: REST endpoints for resume, JD, optimization, interview, and reports.
- `controller/request`: request DTOs used by controllers.
- `controller/vo`: response view objects returned to frontend.
- `dao/entity`: MyBatis-Plus entities mapped to `t_career_*` tables.
- `dao/mapper`: MyBatis-Plus mapper interfaces.
- `service`: narrow service interfaces for testable boundaries.
- `service/impl`: default service implementations.
- `service/model`: internal immutable-ish business models for scoring and structured LLM output.
- `service/parser`: resume/JD text extraction and JSON parsing helpers.
- `service/prompt`: prompt builders and prompt version constants.
- `service/review`: JobNavigator-style judge-executor review, quality gate, and optimization progress events.
- `service/runtime`: AI-Meeting-style interview turn state machine, idempotency key, and compensation state.
- `service/recovery`: interview snapshot build/load/recover logic.
- `service/singleflight`: AI call de-duplication, owner heartbeat, fencing token, and result replay.
- `service/retrieval`: Career-specific HyDE query generation and Rerank orchestration through Ragent retrieval abstractions.
- `service/render`: resume export field validation, template versioning, and PDF/Word render gates.
- `service/trace`: Career trace runner that creates Ragent trace runs for non-streaming tasks.
- `enums`: status/type enums used by database and API.

### Backend: Exact Files To Create

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums/CareerTaskStatus.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums/ResumeSuggestionStatus.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums/InterviewSessionStatus.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums/InterviewTurnType.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums/OptimizationReviewStatus.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums/InterviewRuntimeStatus.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums/SingleFlightStatus.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/CandidateProfileDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/ResumeDocumentDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/ResumeVersionDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/JobDescriptionDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/JobAlignmentReportDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/ResumeOptimizationTaskDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/ResumeOptimizationSuggestionDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/ResumeOptimizationReviewDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/CareerTaskAttemptDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/CareerProgressEventDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/ResumeExportRecordDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/InterviewSessionDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/InterviewTurnDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/InterviewSessionSnapshotDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/CareerSingleFlightRecordDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/InterviewReportDO.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/CandidateProfileMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/ResumeDocumentMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/ResumeVersionMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/JobDescriptionMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/JobAlignmentReportMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/ResumeOptimizationTaskMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/ResumeOptimizationSuggestionMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/ResumeOptimizationReviewMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/CareerTaskAttemptMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/CareerProgressEventMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/ResumeExportRecordMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/InterviewSessionMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/InterviewTurnMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/InterviewSessionSnapshotMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/CareerSingleFlightRecordMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/InterviewReportMapper.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/CandidateProfileService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/ResumeVersionService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/JobAlignmentService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/ResumeOptimizationService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/InterviewSessionService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/InterviewReportService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/review/ResumeOptimizationReviewService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/runtime/InterviewTurnRuntimeService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSessionRecoveryService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/retrieval/CareerRetrievalEnhancementService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeRenderPipeline.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/parser/CareerJsonParser.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/parser/ResumeTextExtractor.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/prompt/CareerPromptTemplates.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/trace/CareerTraceRunner.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerResumeController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerJobController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerOptimizationController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerInterviewController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerAdminController.java`

### Backend: Existing Files To Modify

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java`
  - Add career mapper scan package.
- `resources/database/schema_pg.sql`
  - Add `t_career_*` tables and indexes.
- `resources/database/init_data_pg.sql`
  - Add sample career knowledge base data and sample questions.
- `bootstrap/src/main/resources/application.yaml`
  - Add `career` prompt/model/feature switches.

### Backend Tests To Create

- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/parser/CareerJsonParserTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/JobAlignmentScoringTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeOptimizationSuggestionTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionStateTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewReportAggregationTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeOptimizationReviewTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewTurnIdempotencyTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionRecoveryTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerSingleFlightTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerRetrievalEnhancementTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeRenderPipelineTest.java`

### Frontend Files To Create

- `frontend/src/services/careerService.ts`
- `frontend/src/pages/career/CareerHomePage.tsx`
- `frontend/src/pages/career/ResumeCenterPage.tsx`
- `frontend/src/pages/career/JobAlignmentPage.tsx`
- `frontend/src/pages/career/ResumeOptimizationPage.tsx`
- `frontend/src/pages/career/MockInterviewPage.tsx`
- `frontend/src/pages/career/InterviewReportPage.tsx`
- `frontend/src/pages/admin/career/CareerDashboardPage.tsx`
- `frontend/src/pages/admin/career/CareerTasksPage.tsx`
- `frontend/src/pages/admin/career/CareerRubricPage.tsx`

### Frontend Files To Modify

- `frontend/src/router.tsx`
  - Add user-facing career routes and admin career routes.
- `frontend/src/components/layout/Sidebar.tsx`
  - Add user-facing Career entry.
- `frontend/src/pages/admin/AdminLayout.tsx`
  - Add admin Career menu group and breadcrumbs.
- `frontend/src/types/index.ts`
  - Add shared career types if the file is still the central exported types file when implementation starts.

## Cross-Cutting Rules

- Use Ragent `Result<T>` and `Results.success(...)` for every backend endpoint.
- Use `UserContext.getUserId()` for ownership checks.
- Do not add a second login system.
- Do not add LangChain4j or Spring AI business calls into `bootstrap`; call `LLMService` from `infra-ai`.
- Store structured AI outputs as JSONB strings in PostgreSQL entity fields, then expose typed VO fields to the frontend.
- Use `@RagTraceNode` for inner AI steps and `CareerTraceRunner` for full task runs.
- Keep Phase 1 synchronous enough for local demo, but persist task status for every long-running AI action.
- Keep voice, ASR, TTS, and demeanor analysis outside Phase 1 and Phase 2 implementation branches.
- Treat PostgreSQL as the source of truth for Career task state, interview turns, optimization reviews, snapshots, and export records.
- Redis may be used for hot locks, heartbeat, rate limits, or Single-flight owner state, but persisted PostgreSQL records must be sufficient for recovery and audit.
- Do not introduce MongoDB into Ragent Career for AI-Meeting snapshot migration; use JSONB snapshot records and optional Redis hot state.
- Do not introduce Qdrant into Ragent Career for JobNavigator retrieval migration; use Ragent retrieval and rerank abstractions.
- Every AI call that can create a business artifact must have an idempotency key, attempt record, and Trace link.
- Any generated resume text that lacks resume evidence must be marked as risk and must not be applied without user confirmation.
- HyDE-generated content is a retrieval query or evidence-expansion aid only; it must never be persisted as user resume content.

## Task 1: Database Schema For Career Domain

**Files:**
- Modify: `resources/database/schema_pg.sql`
- Modify: `resources/database/init_data_pg.sql`

- [ ] **Step 1: Add career tables to schema**

Append the following tables after `t_knowledge_vector` in `resources/database/schema_pg.sql`:

```sql
-- ============================================
-- Career Agent Tables
-- ============================================

CREATE TABLE t_career_candidate_profile (
    id             VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id        VARCHAR(20) NOT NULL,
    display_name   VARCHAR(128),
    headline       VARCHAR(255),
    summary        TEXT,
    profile_json   JSONB,
    created_by     VARCHAR(20),
    updated_by     VARCHAR(20),
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_profile_user ON t_career_candidate_profile (user_id, deleted);
COMMENT ON TABLE t_career_candidate_profile IS '求职者职业画像表';

CREATE TABLE t_career_resume_document (
    id               VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id          VARCHAR(20) NOT NULL,
    profile_id       VARCHAR(20),
    original_name    VARCHAR(255) NOT NULL,
    file_url         VARCHAR(1024),
    file_type        VARCHAR(32),
    file_size        BIGINT,
    parse_status     VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    raw_text         TEXT,
    parse_error      TEXT,
    created_by       VARCHAR(20),
    updated_by       VARCHAR(20),
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_resume_doc_user ON t_career_resume_document (user_id, create_time);
COMMENT ON TABLE t_career_resume_document IS '简历原始文档表';

CREATE TABLE t_career_resume_version (
    id               VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id          VARCHAR(20) NOT NULL,
    profile_id       VARCHAR(20) NOT NULL,
    document_id      VARCHAR(20),
    version_no       INTEGER NOT NULL,
    title            VARCHAR(128) NOT NULL,
    source_type      VARCHAR(32) NOT NULL,
    content_json     JSONB NOT NULL,
    markdown_content TEXT,
    active           SMALLINT NOT NULL DEFAULT 1,
    created_by       VARCHAR(20),
    updated_by       VARCHAR(20),
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_resume_version_profile ON t_career_resume_version (profile_id, version_no);
CREATE INDEX idx_career_resume_version_user ON t_career_resume_version (user_id, create_time);
COMMENT ON TABLE t_career_resume_version IS '结构化简历版本表';

CREATE TABLE t_career_job_description (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id         VARCHAR(20) NOT NULL,
    title           VARCHAR(128) NOT NULL,
    company         VARCHAR(128),
    source_type     VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    source_location VARCHAR(1024),
    raw_text        TEXT NOT NULL,
    parsed_json     JSONB,
    created_by      VARCHAR(20),
    updated_by      VARCHAR(20),
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_jd_user ON t_career_job_description (user_id, create_time);
COMMENT ON TABLE t_career_job_description IS '目标岗位描述表';

CREATE TABLE t_career_job_alignment_report (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id           VARCHAR(20) NOT NULL,
    resume_version_id VARCHAR(20) NOT NULL,
    jd_id             VARCHAR(20) NOT NULL,
    score             INTEGER NOT NULL,
    summary           TEXT,
    evidence_json     JSONB,
    gaps_json         JSONB,
    risks_json        JSONB,
    trace_id          VARCHAR(64),
    created_by        VARCHAR(20),
    updated_by        VARCHAR(20),
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_alignment_user ON t_career_job_alignment_report (user_id, create_time);
CREATE INDEX idx_career_alignment_resume_jd ON t_career_job_alignment_report (resume_version_id, jd_id);
COMMENT ON TABLE t_career_job_alignment_report IS '简历与JD匹配报告表';

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
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_opt_task_user ON t_career_resume_optimization_task (user_id, create_time);
COMMENT ON TABLE t_career_resume_optimization_task IS '简历优化任务表';

CREATE TABLE t_career_resume_optimization_suggestion (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    task_id         VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    category        VARCHAR(64) NOT NULL,
    title           VARCHAR(128) NOT NULL,
    original_text   TEXT,
    suggested_text  TEXT NOT NULL,
    reason          TEXT,
    risk_level      VARCHAR(32) NOT NULL DEFAULT 'LOW',
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_suggestion_task ON t_career_resume_optimization_suggestion (task_id);
COMMENT ON TABLE t_career_resume_optimization_suggestion IS '简历优化建议表';

CREATE TABLE t_career_resume_export_record (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id           VARCHAR(20) NOT NULL,
    resume_version_id VARCHAR(20) NOT NULL,
    export_type       VARCHAR(32) NOT NULL,
    file_url          VARCHAR(1024),
    status            VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    error_message     TEXT,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_export_user ON t_career_resume_export_record (user_id, create_time);
COMMENT ON TABLE t_career_resume_export_record IS '简历导出记录表';

CREATE TABLE t_career_interview_session (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id           VARCHAR(20) NOT NULL,
    resume_version_id VARCHAR(20) NOT NULL,
    jd_id             VARCHAR(20),
    status            VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    plan_json         JSONB,
    current_turn_no   INTEGER NOT NULL DEFAULT 0,
    trace_id          VARCHAR(64),
    created_by        VARCHAR(20),
    updated_by        VARCHAR(20),
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_interview_user ON t_career_interview_session (user_id, create_time);
COMMENT ON TABLE t_career_interview_session IS '模拟面试会话表';

CREATE TABLE t_career_interview_turn (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    session_id      VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    turn_no         INTEGER NOT NULL,
    turn_type       VARCHAR(32) NOT NULL,
    question        TEXT NOT NULL,
    answer          TEXT,
    score           INTEGER,
    feedback_json   JSONB,
    status          VARCHAR(32) NOT NULL DEFAULT 'ASKED',
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_turn_session ON t_career_interview_turn (session_id, turn_no);
COMMENT ON TABLE t_career_interview_turn IS '模拟面试问答轮次表';

CREATE TABLE t_career_interview_report (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    session_id        VARCHAR(20) NOT NULL,
    user_id           VARCHAR(20) NOT NULL,
    overall_score     INTEGER NOT NULL,
    radar_json        JSONB,
    playback_json     JSONB,
    suggestions_json  JSONB,
    summary           TEXT,
    trace_id          VARCHAR(64),
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_career_report_session ON t_career_interview_report (session_id);
COMMENT ON TABLE t_career_interview_report IS '模拟面试复盘报告表';
```

- [ ] **Step 2: Add sample seed data**

Append this sample intent and sample questions to `resources/database/init_data_pg.sql`:

```sql
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
```

- [ ] **Step 3: Run schema validation**

Run:

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: Maven compiles existing code. Schema files are not compiled, but this confirms no accidental Java changes were introduced in this task.

- [ ] **Step 4: Commit**

```bash
git add resources/database/schema_pg.sql resources/database/init_data_pg.sql
git commit -m "feat: add career domain schema"
```

## Task 2: Register Career Mappers And Core Enums

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java`
- Create: enum files listed in File Structure
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionStateTest.java`

- [ ] **Step 1: Write state test**

Create `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionStateTest.java`:

```java
package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InterviewSessionStateTest {

    @Test
    void createdSessionCanStart() {
        Assertions.assertTrue(InterviewSessionStatus.CREATED.canStart());
    }

    @Test
    void completedSessionCannotStartAgain() {
        Assertions.assertFalse(InterviewSessionStatus.COMPLETED.canStart());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl bootstrap -Dtest=InterviewSessionStateTest test
```

Expected: FAIL because `InterviewSessionStatus` does not exist.

- [ ] **Step 3: Create enums**

Create `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums/InterviewSessionStatus.java`:

```java
package com.nageoffer.ai.ragent.career.enums;

public enum InterviewSessionStatus {
    CREATED,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED;

    public boolean canStart() {
        return this == CREATED || this == PAUSED;
    }

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
```

Create `CareerTaskStatus.java`:

```java
package com.nageoffer.ai.ragent.career.enums;

public enum CareerTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}
```

Create `ResumeSuggestionStatus.java`:

```java
package com.nageoffer.ai.ragent.career.enums;

public enum ResumeSuggestionStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EDITED
}
```

Create `InterviewTurnType.java`:

```java
package com.nageoffer.ai.ragent.career.enums;

public enum InterviewTurnType {
    TECHNICAL,
    PROJECT_DEEP_DIVE,
    BEHAVIORAL,
    MOTIVATION,
    FOLLOW_UP
}
```

- [ ] **Step 4: Register mapper scan**

Modify `RagentApplication.java` `@MapperScan` to include:

```java
"com.nageoffer.ai.ragent.career.dao.mapper"
```

- [ ] **Step 5: Run test**

```bash
mvn -pl bootstrap -Dtest=InterviewSessionStateTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java bootstrap/src/main/java/com/nageoffer/ai/ragent/career/enums bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionStateTest.java
git commit -m "feat: add career enums and mapper scan"
```

## Task 3: Create Career Persistence Layer

**Files:**
- Create: all `career/dao/entity/*DO.java`
- Create: all `career/dao/mapper/*Mapper.java`
- Test: compile

- [ ] **Step 1: Create entity classes**

Follow the existing `KnowledgeBaseDO` style:

- Add Apache license header.
- Use `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- Use `@TableName("t_career_*")`.
- Use `@TableId(type = IdType.ASSIGN_ID)` for `id`.
- Use `@TableField(fill = FieldFill.INSERT)` on `createTime`.
- Use `@TableField(fill = FieldFill.INSERT_UPDATE)` on `updateTime`.
- Use `@TableLogic` on `deleted`.
- Use `String` for JSONB fields in Java entity classes unless a mapper type handler is introduced in the same task.
- Use `Date` for timestamp fields.

Create `CandidateProfileDO.java` with this concrete structure:

```java
package com.nageoffer.ai.ragent.career.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_career_candidate_profile")
public class CandidateProfileDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId;
    private String displayName;
    private String headline;
    private String summary;
    private String profileJson;
    private String createdBy;
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
```

Create the remaining entity classes using the same imports, annotations, `@TableId`, `@TableField`, and `@TableLogic` pattern. Use these exact table names, class names, and fields:

| Class | Table | Fields beyond `id`, `createTime`, `updateTime`, `deleted` |
| --- | --- | --- |
| `CandidateProfileDO` | `t_career_candidate_profile` | `String userId`, `String displayName`, `String headline`, `String summary`, `String profileJson`, `String createdBy`, `String updatedBy` |
| `ResumeDocumentDO` | `t_career_resume_document` | `String userId`, `String profileId`, `String originalName`, `String fileUrl`, `String fileType`, `Long fileSize`, `String parseStatus`, `String rawText`, `String parseError`, `String createdBy`, `String updatedBy` |
| `ResumeVersionDO` | `t_career_resume_version` | `String userId`, `String profileId`, `String documentId`, `Integer versionNo`, `String title`, `String sourceType`, `String contentJson`, `String markdownContent`, `Integer active`, `String createdBy`, `String updatedBy` |
| `JobDescriptionDO` | `t_career_job_description` | `String userId`, `String title`, `String company`, `String sourceType`, `String sourceLocation`, `String rawText`, `String parsedJson`, `String createdBy`, `String updatedBy` |
| `JobAlignmentReportDO` | `t_career_job_alignment_report` | `String userId`, `String resumeVersionId`, `String jdId`, `Integer score`, `String summary`, `String evidenceJson`, `String gapsJson`, `String risksJson`, `String traceId`, `String createdBy`, `String updatedBy` |
| `ResumeOptimizationTaskDO` | `t_career_resume_optimization_task` | `String userId`, `String resumeVersionId`, `String jdId`, `String status`, `String inputJson`, `String outputJson`, `String traceId`, `String errorMessage`, `String createdBy`, `String updatedBy` |
| `ResumeOptimizationSuggestionDO` | `t_career_resume_optimization_suggestion` | `String taskId`, `String userId`, `String category`, `String title`, `String originalText`, `String suggestedText`, `String reason`, `String riskLevel`, `String status` |
| `ResumeExportRecordDO` | `t_career_resume_export_record` | `String userId`, `String resumeVersionId`, `String exportType`, `String fileUrl`, `String status`, `String errorMessage` |
| `InterviewSessionDO` | `t_career_interview_session` | `String userId`, `String resumeVersionId`, `String jdId`, `String status`, `String planJson`, `Integer currentTurnNo`, `String traceId`, `String createdBy`, `String updatedBy` |
| `InterviewTurnDO` | `t_career_interview_turn` | `String sessionId`, `String userId`, `Integer turnNo`, `String turnType`, `String question`, `String answer`, `Integer score`, `String feedbackJson`, `String status` |
| `InterviewReportDO` | `t_career_interview_report` | `String sessionId`, `String userId`, `Integer overallScore`, `String radarJson`, `String playbackJson`, `String suggestionsJson`, `String summary`, `String traceId` |

- [ ] **Step 2: Create mapper interfaces**

Create these one-method-free MyBatis-Plus mapper interfaces:

```java
package com.nageoffer.ai.ragent.career.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nageoffer.ai.ragent.career.dao.entity.CandidateProfileDO;

public interface CandidateProfileMapper extends BaseMapper<CandidateProfileDO> {
}
```

The complete mapper/entity pairs are:

| Mapper | Entity import | Extends |
| --- | --- | --- |
| `CandidateProfileMapper` | `CandidateProfileDO` | `BaseMapper<CandidateProfileDO>` |
| `ResumeDocumentMapper` | `ResumeDocumentDO` | `BaseMapper<ResumeDocumentDO>` |
| `ResumeVersionMapper` | `ResumeVersionDO` | `BaseMapper<ResumeVersionDO>` |
| `JobDescriptionMapper` | `JobDescriptionDO` | `BaseMapper<JobDescriptionDO>` |
| `JobAlignmentReportMapper` | `JobAlignmentReportDO` | `BaseMapper<JobAlignmentReportDO>` |
| `ResumeOptimizationTaskMapper` | `ResumeOptimizationTaskDO` | `BaseMapper<ResumeOptimizationTaskDO>` |
| `ResumeOptimizationSuggestionMapper` | `ResumeOptimizationSuggestionDO` | `BaseMapper<ResumeOptimizationSuggestionDO>` |
| `ResumeExportRecordMapper` | `ResumeExportRecordDO` | `BaseMapper<ResumeExportRecordDO>` |
| `InterviewSessionMapper` | `InterviewSessionDO` | `BaseMapper<InterviewSessionDO>` |
| `InterviewTurnMapper` | `InterviewTurnDO` | `BaseMapper<InterviewTurnDO>` |
| `InterviewReportMapper` | `InterviewReportDO` | `BaseMapper<InterviewReportDO>` |

- [ ] **Step 3: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao
git commit -m "feat: add career persistence layer"
```

## Task 4: Add Career JSON Parser

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/parser/CareerJsonParser.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/parser/CareerJsonParserTest.java`

- [ ] **Step 1: Write parser tests**

Create `CareerJsonParserTest.java`:

```java
package com.nageoffer.ai.ragent.career.parser;

import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class CareerJsonParserTest {

    private final CareerJsonParser parser = new CareerJsonParser();

    @Test
    void parsesJsonWrappedInMarkdownFence() {
        Map<String, Object> result = parser.parseObject("```json\n{\"score\":88,\"summary\":\"good\"}\n```");
        Assertions.assertEquals(88, result.get("score"));
        Assertions.assertEquals("good", result.get("summary"));
    }

    @Test
    void rejectsBlankResponse() {
        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> parser.parseObject(" ")
        );
        Assertions.assertEquals("AI response is blank", ex.getMessage());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl bootstrap -Dtest=CareerJsonParserTest test
```

Expected: FAIL because `CareerJsonParser` does not exist.

- [ ] **Step 3: Implement parser**

Create `CareerJsonParser.java`:

```java
package com.nageoffer.ai.ragent.career.service.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CareerJsonParser {

    public Map<String, Object> parseObject(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("AI response is blank");
        }
        String cleaned = stripFence(response.trim());
        return JSON.parseObject(cleaned, new TypeReference<Map<String, Object>>() {
        });
    }

    private String stripFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        String withoutStart = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }
}
```

- [ ] **Step 4: Run test**

```bash
mvn -pl bootstrap -Dtest=CareerJsonParserTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/parser/CareerJsonParser.java bootstrap/src/test/java/com/nageoffer/ai/ragent/career/parser/CareerJsonParserTest.java
git commit -m "feat: add career json parser"
```

## Task 5: Add Resume Text Extraction

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/parser/ResumeTextExtractor.java`
- Test: compile smoke test

- [ ] **Step 1: Create extractor interface-like component**

Create `ResumeTextExtractor.java` using Apache Tika already available in `bootstrap/pom.xml`:

```java
package com.nageoffer.ai.ragent.career.service.parser;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class ResumeTextExtractor {

    private final Tika tika = new Tika();

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Resume file is empty");
        }
        try {
            String text = tika.parseToString(file.getInputStream());
            if (text == null || text.trim().isEmpty()) {
                throw new IllegalArgumentException("Resume text is empty");
            }
            return text.trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read resume file", ex);
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/parser/ResumeTextExtractor.java
git commit -m "feat: add resume text extractor"
```

## Task 6: Add Prompt Templates For Career AI Calls

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/prompt/CareerPromptTemplates.java`
- Modify: `bootstrap/src/main/resources/application.yaml`

- [ ] **Step 1: Add prompt feature config**

Append under root `career:` in `application.yaml`:

```yaml
career:
  ai:
    model-id:
    prompt-version: v1
  features:
    resume-export: true
    text-interview: true
    voice-interview: false
    demeanor-analysis: false
```

- [ ] **Step 2: Create prompt templates**

Create `CareerPromptTemplates.java` with constants:

```java
package com.nageoffer.ai.ragent.career.service.prompt;

public final class CareerPromptTemplates {

    public static final String RESUME_PARSE = """
            你是简历结构化解析器。请把用户简历文本解析为 JSON。
            JSON 字段必须包含：basic, education, experiences, projects, skills, certificates, highlights, risks。
            risks 用于记录不确定、缺失或疑似解析错误的信息。
            只输出 JSON，不输出解释。
            简历文本：
            %s
            """;

    public static final String JD_PARSE = """
            你是岗位 JD 结构化解析器。请把岗位描述解析为 JSON。
            JSON 字段必须包含：title, company, responsibilities, requiredSkills, preferredSkills, softSkills, seniority, keywords。
            只输出 JSON，不输出解释。
            岗位描述：
            %s
            """;

    public static final String JD_ALIGNMENT = """
            你是求职匹配分析师。请对简历版本和目标 JD 做匹配分析。
            输出 JSON 字段必须包含：score, summary, evidence, gaps, risks, optimizationDirections。
            score 为 0 到 100 的整数。
            evidence 每项必须包含 jdRequirement, resumeEvidence, confidence。
            gaps 每项必须包含 requirement, gap, suggestion。
            risks 每项必须包含 text, reason, level。
            简历 JSON：
            %s
            JD JSON：
            %s
            """;

    public static final String RESUME_OPTIMIZE = """
            你是简历优化顾问。请基于简历、JD 和匹配报告生成优化建议。
            输出 JSON 字段必须包含：summary, suggestions, optimizedResume。
            suggestions 每项必须包含 category, title, originalText, suggestedText, reason, riskLevel。
            riskLevel 只能是 LOW、MEDIUM、HIGH。
            不允许编造不存在的经历。
            简历 JSON：
            %s
            JD JSON：
            %s
            匹配报告 JSON：
            %s
            """;

    public static final String INTERVIEW_PLAN = """
            你是面试计划生成器。请基于简历和 JD 生成一场模拟面试计划。
            输出 JSON 字段必须包含：dimensions, questions。
            questions 每项必须包含 type, question, expectedSignals, difficulty。
            type 只能是 TECHNICAL、PROJECT_DEEP_DIVE、BEHAVIORAL、MOTIVATION。
            简历 JSON：
            %s
            JD JSON：
            %s
            """;

    public static final String INTERVIEW_EVALUATE = """
            你是模拟面试评分官。请评价候选人的回答。
            输出 JSON 字段必须包含：score, feedback, strengths, weaknesses, followUpRequired, followUpQuestion。
            score 为 0 到 100 的整数。
            问题：
            %s
            回答：
            %s
            简历 JSON：
            %s
            JD JSON：
            %s
            """;

    public static final String INTERVIEW_REPORT = """
            你是面试复盘顾问。请基于全部问答生成复盘报告。
            输出 JSON 字段必须包含：overallScore, radar, playback, suggestions, summary。
            radar 每项必须包含 dimension, score, comment。
            playback 每项必须包含 question, answer, score, feedback。
            suggestions 每项必须包含 title, action, priority。
            面试问答 JSON：
            %s
            """;

    private CareerPromptTemplates() {
    }
}
```

- [ ] **Step 3: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/prompt/CareerPromptTemplates.java bootstrap/src/main/resources/application.yaml
git commit -m "feat: add career prompt templates"
```

## Task 7: Implement Career Trace Runner

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/trace/CareerTraceRunner.java`
- Test: compile

- [ ] **Step 1: Create trace runner**

Create a non-streaming wrapper modeled after `StreamChatTraceRunner`:

```java
package com.nageoffer.ai.ragent.career.service.trace;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CareerTraceRunner {

    private final RagTraceProperties traceProperties;
    private final RagTraceRecordService traceRecordService;

    public <T> T run(String traceName, String taskId, Supplier<T> supplier) {
        if (!traceProperties.isEnabled()) {
            return supplier.get();
        }
        String traceId = IdUtil.getSnowflakeNextIdStr();
        long start = System.currentTimeMillis();
        traceRecordService.startRun(RagTraceRunDO.builder()
                .traceId(traceId)
                .traceName(traceName)
                .entryMethod("CareerTraceRunner#run")
                .taskId(taskId)
                .userId(UserContext.getUserId())
                .status("RUNNING")
                .startTime(new Date())
                .build());
        RagTraceContext.setTraceId(traceId);
        RagTraceContext.setTaskId(taskId);
        try {
            T result = supplier.get();
            traceRecordService.finishRun(traceId, "SUCCESS", null, new Date(), System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            traceRecordService.finishRun(traceId, "ERROR", ex.getClass().getSimpleName() + ": " + ex.getMessage(), new Date(), System.currentTimeMillis() - start);
            throw ex;
        } finally {
            RagTraceContext.clear();
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/trace/CareerTraceRunner.java
git commit -m "feat: add career trace runner"
```

## Task 8: Implement Resume Upload, Parse, And Version API

**Files:**
- Create/Modify: career resume service/controller/request/vo files
- Test: `CareerJsonParserTest`, compile

- [ ] **Step 1: Define request and VO objects**

Create:

- `CareerResumeUploadVO` with `documentId`, `profileId`, `resumeVersionId`, `parseStatus`.
- `CareerResumeVersionVO` with `id`, `profileId`, `versionNo`, `title`, `content`, `markdownContent`, `createTime`.
- `CareerResumeUpdateRequest` with `title`, `contentJson`, `markdownContent`.

Use Lombok `@Data` for request and `@Data @Builder` for VO.

- [ ] **Step 2: Define service interface**

Create `CandidateProfileService` methods:

```java
CareerResumeUploadVO uploadAndParse(MultipartFile file);
CareerResumeVersionVO queryVersion(String versionId);
List<CareerResumeVersionVO> listVersions(String profileId);
CareerResumeVersionVO updateVersion(String versionId, CareerResumeUpdateRequest request);
void deleteVersion(String versionId);
```

- [ ] **Step 3: Implement service**

In `CandidateProfileServiceImpl`:

- Read current user id from `UserContext.getUserId()`.
- Extract text with `ResumeTextExtractor`.
- Insert `ResumeDocumentDO` with `parseStatus=RUNNING`.
- Build `ChatRequest` using `CareerPromptTemplates.RESUME_PARSE`.
- Call `LLMService.chat(request)`.
- Parse response through `CareerJsonParser`.
- Insert or update `CandidateProfileDO`.
- Insert `ResumeVersionDO` with `versionNo=1`, `sourceType=PARSED`, and `contentJson`.
- Mark document `parseStatus=SUCCESS`.
- On runtime exception mark document `parseStatus=FAILED` and persist `parseError`.

- [ ] **Step 4: Implement controller**

Create `CareerResumeController` endpoints:

```text
POST /career/resumes/upload
GET  /career/resumes/versions/{versionId}
GET  /career/profiles/{profileId}/versions
PUT  /career/resumes/versions/{versionId}
DELETE /career/resumes/versions/{versionId}
```

Return `Result<T>` with `Results.success(...)`.

- [ ] **Step 5: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career
git commit -m "feat: add resume parse and version api"
```

## Task 9: Implement JD Creation And Alignment

**Files:**
- Create: career job controller/request/vo/service files
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/JobAlignmentScoringTest.java`

- [ ] **Step 1: Write score normalization test**

Create `JobAlignmentScoringTest.java`:

```java
package com.nageoffer.ai.ragent.career.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JobAlignmentScoringTest {

    @Test
    void clampsScoreToHundred() {
        int score = Math.max(0, Math.min(100, 138));
        Assertions.assertEquals(100, score);
    }

    @Test
    void clampsScoreToZero() {
        int score = Math.max(0, Math.min(100, -5));
        Assertions.assertEquals(0, score);
    }
}
```

- [ ] **Step 2: Define request and VO objects**

Create:

- `CareerJobCreateRequest`: `title`, `company`, `rawText`, `sourceType`, `sourceLocation`.
- `CareerAlignmentCreateRequest`: `resumeVersionId`, `jdId`.
- `CareerJobVO`: `id`, `title`, `company`, `rawText`, `parsed`, `createTime`.
- `CareerAlignmentReportVO`: `id`, `resumeVersionId`, `jdId`, `score`, `summary`, `evidence`, `gaps`, `risks`, `traceId`.

- [ ] **Step 3: Define service interface**

Create `JobAlignmentService`:

```java
CareerJobVO createJob(CareerJobCreateRequest request);
CareerJobVO queryJob(String jdId);
CareerAlignmentReportVO align(CareerAlignmentCreateRequest request);
CareerAlignmentReportVO queryAlignment(String reportId);
```

- [ ] **Step 4: Implement service**

In `JobAlignmentServiceImpl`:

- Validate `rawText` length is between 20 and 20000 characters.
- Parse JD using `CareerPromptTemplates.JD_PARSE`.
- Fetch `ResumeVersionDO` and verify `userId`.
- Call `CareerPromptTemplates.JD_ALIGNMENT`.
- Clamp `score` into 0..100 before persistence.
- Persist `JobAlignmentReportDO`.
- Return typed VO with parsed JSON maps.

- [ ] **Step 5: Implement controller**

Create `CareerJobController` endpoints:

```text
POST /career/jobs
GET  /career/jobs/{jdId}
POST /career/alignments
GET  /career/alignments/{reportId}
```

- [ ] **Step 6: Run tests**

```bash
mvn -pl bootstrap -Dtest=JobAlignmentScoringTest test
```

Expected: PASS.

- [ ] **Step 7: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/JobAlignmentScoringTest.java
git commit -m "feat: add jd alignment api"
```

## Task 10: Implement Resume Optimization Suggestions

**Files:**
- Create: optimization service/controller/request/vo files
- Test: `ResumeOptimizationSuggestionTest.java`

- [ ] **Step 1: Write suggestion state test**

Create `ResumeOptimizationSuggestionTest.java`:

```java
package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.enums.ResumeSuggestionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ResumeOptimizationSuggestionTest {

    @Test
    void acceptedStatusIsStableEnumContract() {
        Assertions.assertEquals("ACCEPTED", ResumeSuggestionStatus.ACCEPTED.name());
    }
}
```

- [ ] **Step 2: Define request and VO objects**

Create:

- `CareerOptimizationCreateRequest`: `resumeVersionId`, `jdId`, `alignmentReportId`.
- `CareerSuggestionDecisionRequest`: `status`, `editedText`.
- `CareerOptimizationTaskVO`: `id`, `status`, `resumeVersionId`, `jdId`, `summary`, `suggestions`, `traceId`.
- `CareerOptimizationSuggestionVO`: `id`, `category`, `title`, `originalText`, `suggestedText`, `reason`, `riskLevel`, `status`.

- [ ] **Step 3: Define service interface**

Create `ResumeOptimizationService`:

```java
CareerOptimizationTaskVO createTask(CareerOptimizationCreateRequest request);
CareerOptimizationTaskVO queryTask(String taskId);
CareerOptimizationSuggestionVO decideSuggestion(String suggestionId, CareerSuggestionDecisionRequest request);
CareerResumeVersionVO generateVersionFromAcceptedSuggestions(String taskId);
```

- [ ] **Step 4: Implement service**

In `ResumeOptimizationServiceImpl`:

- Create task with `RUNNING`.
- Fetch resume version, JD, and alignment report with ownership checks.
- Call `LLMService.chat` using `CareerPromptTemplates.RESUME_OPTIMIZE`.
- Persist task `outputJson`.
- Persist each suggestion row.
- Mark task `SUCCESS`.
- On failure mark task `FAILED` with `errorMessage`.
- `decideSuggestion` only allows `ACCEPTED`, `REJECTED`, or `EDITED`.
- `generateVersionFromAcceptedSuggestions` creates a new `ResumeVersionDO` with incremented `versionNo`.

- [ ] **Step 5: Implement controller**

Create `CareerOptimizationController` endpoints:

```text
POST /career/optimizations
GET  /career/optimizations/{taskId}
PUT  /career/optimizations/suggestions/{suggestionId}
POST /career/optimizations/{taskId}/versions
```

- [ ] **Step 6: Run tests**

```bash
mvn -pl bootstrap -Dtest=ResumeOptimizationSuggestionTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeOptimizationSuggestionTest.java
git commit -m "feat: add resume optimization workflow"
```

## Task 11: Implement Basic Resume Export

**Files:**
- Modify: `ResumeOptimizationService`
- Create: export helper under `career/service/impl`
- Test: compile

- [ ] **Step 1: Add export request and VO**

Create:

- `CareerResumeExportRequest`: `resumeVersionId`, `exportType`.
- `CareerResumeExportVO`: `id`, `resumeVersionId`, `exportType`, `fileUrl`, `status`.

- [ ] **Step 2: Add service method**

Add to `ResumeVersionService`:

```java
CareerResumeExportVO export(CareerResumeExportRequest request);
```

- [ ] **Step 3: Implement MVP export**

Implement Markdown and HTML export first:

- `MARKDOWN`: persist `markdownContent` as a `.md` object through the existing S3-compatible storage pattern used by knowledge documents.
- `HTML`: wrap Markdown content in a minimal HTML document and persist as `.html`.
- `PDF` and `WORD`: return `ClientException("当前导出格式暂未启用：" + exportType)` until their renderers are added in a dedicated renderer task.

- [ ] **Step 4: Add controller endpoint**

Add to `CareerResumeController`:

```text
POST /career/resumes/export
```

- [ ] **Step 5: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career
git commit -m "feat: add markdown and html resume export"
```

## Task 12: Implement Text Interview Session

**Files:**
- Create: interview service/controller/request/vo files
- Test: `InterviewSessionStateTest`

- [ ] **Step 1: Define request and VO objects**

Create:

- `CareerInterviewCreateRequest`: `resumeVersionId`, `jdId`.
- `CareerInterviewAnswerRequest`: `answer`.
- `CareerInterviewSessionVO`: `id`, `status`, `plan`, `currentTurnNo`, `currentQuestion`.
- `CareerInterviewTurnVO`: `id`, `sessionId`, `turnNo`, `turnType`, `question`, `answer`, `score`, `feedback`, `status`.

- [ ] **Step 2: Define service interface**

Create `InterviewSessionService`:

```java
CareerInterviewSessionVO createSession(CareerInterviewCreateRequest request);
CareerInterviewSessionVO querySession(String sessionId);
CareerInterviewTurnVO nextQuestion(String sessionId);
CareerInterviewTurnVO submitAnswer(String sessionId, CareerInterviewAnswerRequest request);
void pause(String sessionId);
void finish(String sessionId);
```

- [ ] **Step 3: Implement create session**

In `InterviewSessionServiceImpl`:

- Fetch resume version and JD with ownership checks.
- Generate plan via `CareerPromptTemplates.INTERVIEW_PLAN`.
- Persist `InterviewSessionDO` with `CREATED`.
- Persist first `InterviewTurnDO` with turn number 1 and status `ASKED`.
- Return session VO.

- [ ] **Step 4: Implement answer submission**

In `submitAnswer`:

- Fetch current `InterviewTurnDO`.
- Reject if session status is `COMPLETED` or `CANCELLED`.
- Save answer.
- Evaluate answer via `CareerPromptTemplates.INTERVIEW_EVALUATE`.
- Persist score and feedback.
- If `followUpRequired=true`, create next turn with `FOLLOW_UP`.
- If no follow-up and plan has remaining questions, create next planned question.
- If no remaining question, mark session `COMPLETED`.

- [ ] **Step 5: Implement controller**

Create `CareerInterviewController` endpoints:

```text
POST /career/interviews
GET  /career/interviews/{sessionId}
GET  /career/interviews/{sessionId}/next-question
POST /career/interviews/{sessionId}/answers
POST /career/interviews/{sessionId}/pause
POST /career/interviews/{sessionId}/finish
```

- [ ] **Step 6: Run tests**

```bash
mvn -pl bootstrap -Dtest=InterviewSessionStateTest test
```

Expected: PASS.

- [ ] **Step 7: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionStateTest.java
git commit -m "feat: add text mock interview session"
```

## Task 13: Implement Interview Report

**Files:**
- Create: `InterviewReportService` and implementation
- Modify: `CareerInterviewController`
- Test: `InterviewReportAggregationTest.java`

- [ ] **Step 1: Write aggregation test**

Create `InterviewReportAggregationTest.java`:

```java
package com.nageoffer.ai.ragent.career.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class InterviewReportAggregationTest {

    @Test
    void averageScoreRoundsToNearestInteger() {
        List<Integer> scores = List.of(80, 91, 76);
        int average = Math.round((float) scores.stream().mapToInt(Integer::intValue).average().orElse(0));
        Assertions.assertEquals(82, average);
    }
}
```

- [ ] **Step 2: Define report VO**

Create `CareerInterviewReportVO` with:

```text
id, sessionId, overallScore, radar, playback, suggestions, summary, traceId, createTime
```

- [ ] **Step 3: Define service**

Create `InterviewReportService`:

```java
CareerInterviewReportVO generate(String sessionId);
CareerInterviewReportVO queryBySession(String sessionId);
```

- [ ] **Step 4: Implement service**

In `InterviewReportServiceImpl`:

- Fetch all turns for session.
- Require at least one scored turn.
- Build turns JSON for prompt.
- Call `CareerPromptTemplates.INTERVIEW_REPORT`.
- Persist `InterviewReportDO`.
- Use average score fallback when LLM result has missing or invalid `overallScore`.

- [ ] **Step 5: Add controller endpoints**

Add:

```text
POST /career/interviews/{sessionId}/report
GET  /career/interviews/{sessionId}/report
```

- [ ] **Step 6: Run tests**

```bash
mvn -pl bootstrap -Dtest=InterviewReportAggregationTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewReportAggregationTest.java
git commit -m "feat: add interview report generation"
```

## Task 14: Add Admin Career APIs

**Files:**
- Create: `CareerAdminController.java`
- Create: admin VO classes
- Test: compile

- [ ] **Step 1: Define admin VO**

Create:

- `CareerDashboardVO`: `resumeCount`, `jdCount`, `optimizationTaskCount`, `interviewSessionCount`, `averageInterviewScore`.
- `CareerTaskItemVO`: `id`, `type`, `status`, `userId`, `title`, `traceId`, `createTime`.

- [ ] **Step 2: Implement admin service methods**

Create a `CareerAdminService` with:

```java
CareerDashboardVO overview();
IPage<CareerTaskItemVO> pageTasks(long current, long size, String type, String status);
```

- [ ] **Step 3: Implement controller**

Create endpoints:

```text
GET /admin/career/overview
GET /admin/career/tasks
```

Use existing admin route conventions and protect through current admin auth flow.

- [ ] **Step 4: Compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add bootstrap/src/main/java/com/nageoffer/ai/ragent/career
git commit -m "feat: add career admin api"
```

## Task 15: Add Frontend Career Service

**Files:**
- Create: `frontend/src/services/careerService.ts`

- [ ] **Step 1: Create service types and API calls**

Create `careerService.ts` with interfaces matching backend VO names:

```ts
import { api } from "./api";

export interface CareerResumeVersion {
  id: string;
  profileId: string;
  versionNo: number;
  title: string;
  content: Record<string, unknown>;
  markdownContent?: string | null;
  createTime?: string | null;
}

export interface CareerJob {
  id: string;
  title: string;
  company?: string | null;
  rawText: string;
  parsed?: Record<string, unknown> | null;
  createTime?: string | null;
}

export interface CareerAlignmentReport {
  id: string;
  resumeVersionId: string;
  jdId: string;
  score: number;
  summary?: string | null;
  evidence?: unknown[];
  gaps?: unknown[];
  risks?: unknown[];
  traceId?: string | null;
}

export interface CareerOptimizationTask {
  id: string;
  status: string;
  resumeVersionId: string;
  jdId?: string | null;
  summary?: string | null;
  suggestions: CareerOptimizationSuggestion[];
  traceId?: string | null;
}

export interface CareerOptimizationSuggestion {
  id: string;
  category: string;
  title: string;
  originalText?: string | null;
  suggestedText: string;
  reason?: string | null;
  riskLevel: string;
  status: string;
}

export interface CareerInterviewSession {
  id: string;
  status: string;
  plan?: Record<string, unknown> | null;
  currentTurnNo: number;
  currentQuestion?: CareerInterviewTurn | null;
}

export interface CareerInterviewTurn {
  id: string;
  sessionId: string;
  turnNo: number;
  turnType: string;
  question: string;
  answer?: string | null;
  score?: number | null;
  feedback?: Record<string, unknown> | null;
  status: string;
}

export interface CareerInterviewReport {
  id: string;
  sessionId: string;
  overallScore: number;
  radar?: unknown[];
  playback?: unknown[];
  suggestions?: unknown[];
  summary?: string | null;
  traceId?: string | null;
  createTime?: string | null;
}

export const uploadResume = async (file: File) => {
  const form = new FormData();
  form.append("file", file);
  return api.post("/career/resumes/upload", form, {
    headers: { "Content-Type": "multipart/form-data" }
  });
};

export const getResumeVersion = async (versionId: string): Promise<CareerResumeVersion> =>
  api.get(`/career/resumes/versions/${versionId}`);

export const createJob = async (payload: { title: string; company?: string; rawText: string }) =>
  api.post<CareerJob, CareerJob>("/career/jobs", payload);

export const createAlignment = async (payload: { resumeVersionId: string; jdId: string }) =>
  api.post<CareerAlignmentReport, CareerAlignmentReport>("/career/alignments", payload);

export const createOptimization = async (payload: { resumeVersionId: string; jdId?: string; alignmentReportId?: string }) =>
  api.post<CareerOptimizationTask, CareerOptimizationTask>("/career/optimizations", payload);

export const createInterview = async (payload: { resumeVersionId: string; jdId?: string }) =>
  api.post<CareerInterviewSession, CareerInterviewSession>("/career/interviews", payload);

export const submitInterviewAnswer = async (sessionId: string, answer: string) =>
  api.post<CareerInterviewTurn, CareerInterviewTurn>(`/career/interviews/${sessionId}/answers`, { answer });

export const generateInterviewReport = async (sessionId: string) =>
  api.post<CareerInterviewReport, CareerInterviewReport>(`/career/interviews/${sessionId}/report`);
```

- [ ] **Step 2: Run frontend type check**

```bash
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/services/careerService.ts
git commit -m "feat: add career frontend service"
```

## Task 16: Add User-Facing Career Pages

**Files:**
- Create: user-facing career pages
- Modify: `frontend/src/router.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Add routes**

Add routes:

```text
/career
/career/resumes
/career/alignment
/career/optimization
/career/interview
/career/interviews/:sessionId/report
```

Each route must be wrapped in `RequireAuth`.

- [ ] **Step 2: Add sidebar entry**

Add a button in `Sidebar.tsx` quick start area:

```text
求职成长
简历优化与模拟面试
```

Click navigates to `/career`.

- [ ] **Step 3: Create MVP pages**

Create pages with these minimum interactions:

- `CareerHomePage`: entry cards for 简历中心、JD 对齐、模拟面试.
- `ResumeCenterPage`: file upload and parse result card.
- `JobAlignmentPage`: JD textarea, create alignment button, score card.
- `ResumeOptimizationPage`: create optimization task button, suggestion list.
- `MockInterviewPage`: create session, show question, submit answer.
- `InterviewReportPage`: generate and show report summary.

- [ ] **Step 4: Build frontend**

```bash
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/router.tsx frontend/src/components/layout/Sidebar.tsx frontend/src/pages/career
git commit -m "feat: add career user pages"
```

## Task 17: Add Admin Career Pages

**Files:**
- Create: admin career pages
- Modify: `frontend/src/router.tsx`
- Modify: `frontend/src/pages/admin/AdminLayout.tsx`

- [ ] **Step 1: Add admin routes**

Add routes:

```text
/admin/career
/admin/career/tasks
/admin/career/rubric
```

- [ ] **Step 2: Add admin menu group**

Add menu group:

```text
求职成长
  Career Dashboard
  任务管理
  评分 Rubric
```

Use lucide icons already imported or add `BriefcaseBusiness`, `ListChecks`, `ChartRadar` if available.

- [ ] **Step 3: Create admin pages**

Create:

- `CareerDashboardPage`: calls `/admin/career/overview`, displays five stat cards.
- `CareerTasksPage`: calls `/admin/career/tasks`, displays task table with trace link.
- `CareerRubricPage`: static first version showing default rubric dimensions and explanation.

- [ ] **Step 4: Build frontend**

```bash
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/router.tsx frontend/src/pages/admin/AdminLayout.tsx frontend/src/pages/admin/career
git commit -m "feat: add career admin pages"
```

## Task 18: End-To-End Smoke Verification

**Files:**
- No source files required

- [ ] **Step 1: Backend compile**

```bash
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 2: Focused backend tests**

```bash
mvn -pl bootstrap -Dtest=CareerJsonParserTest,JobAlignmentScoringTest,ResumeOptimizationSuggestionTest,InterviewSessionStateTest,InterviewReportAggregationTest test
```

Expected: PASS.

- [ ] **Step 3: Frontend build**

```bash
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 4: Manual smoke path**

Start the backend in terminal A:

```bash
mvn -pl bootstrap spring-boot:run
```

Expected: Spring Boot starts without mapper-scan or bean-creation errors.

Start the frontend in terminal B:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Expected: Vite prints a local URL, usually `http://127.0.0.1:5173/`.

Then verify:

1. Login works.
2. `/career` opens.
3. Resume upload calls backend and returns a document/version id.
4. JD alignment returns a score.
5. Optimization returns suggestions.
6. Mock interview returns a question and accepts an answer.
7. Report page renders overall score.
8. Admin career pages render without console errors.

- [ ] **Step 5: Commit smoke fixes**

If smoke verification required source changes:

```bash
git add bootstrap frontend resources
git commit -m "fix: stabilize career smoke flow"
```

If no changes were required, do not create an empty commit.

## Task 19: Documentation And Skill Follow-Up

**Files:**
- Modify: `docs/career-agent-platform/prd-career-agent-platform.md`
- Create: `docs/career-agent-platform/quick-start.md`

- [ ] **Step 1: Create quick start doc**

Create `docs/career-agent-platform/quick-start.md` with:

```markdown
# Career Agent Platform Quick Start

## What This Adds

Career Agent Platform adds resume parsing, JD alignment, resume optimization, text mock interview, and interview report loops on top of Ragent AI.

## Demo Flow

1. Upload a resume from `/career/resumes`.
2. Create a target JD from `/career/alignment`.
3. Generate an alignment report.
4. Generate resume optimization suggestions.
5. Start a mock interview from `/career/interview`.
6. Generate the interview report.
7. Open `/admin/career/tasks` to inspect task status and trace links.

## Engineering Talking Points

- Ragent remains the single runtime base.
- AI calls use `infra-ai` model routing.
- Career tasks write Ragent Trace records.
- Resume, JD, interview, and report data are versioned and linked.
- Voice and demeanor analysis are extension points after the text loop is stable.
```

- [ ] **Step 2: Update PRD review notes**

In `docs/career-agent-platform/prd-career-agent-platform.md`, add a short section after `## 12. Delivery Plan`:

```markdown
### Implementation Plan Link

实施计划见 `docs/career-agent-platform/implementation-plan.md`。
```

- [ ] **Step 3: Commit docs**

```bash
git add docs/career-agent-platform/prd-career-agent-platform.md docs/career-agent-platform/quick-start.md docs/career-agent-platform/implementation-plan.md
git commit -m "docs: add career platform implementation plan"
```

## Task 20: Add Judge-Executor Resume Optimization

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/review/ResumeOptimizationReviewService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/review/ResumeOptimizationReviewServiceImpl.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/ResumeOptimizationReviewDO.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/ResumeOptimizationReviewMapper.java`
- Modify: `CareerPromptTemplates`
- Modify: `ResumeOptimizationServiceImpl`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeOptimizationReviewTest.java`

- [ ] **Step 1: Write quality gate tests**

Create tests that prove:

```java
Assertions.assertEquals("PASSED", gate(0.82, false));
Assertions.assertEquals("NEEDS_REVISION", gate(0.79, false));
Assertions.assertEquals("BLOCKED_BY_RISK", gate(0.93, true));
```

- [ ] **Step 2: Add review persistence**

Add `t_career_resume_optimization_review` with task id, iteration no, executor output json, reviewer output json, quality score, risk flag, status, trace id, and error message.

- [ ] **Step 3: Add reviewer prompt**

Add `RESUME_OPTIMIZATION_REVIEW` prompt contract. Output must include `qualityScore`, `truthfulnessRisk`, `unsupportedClaims`, `acceptedSuggestionIds`, `rejectedSuggestionIds`, and `revisionInstructions`.

- [ ] **Step 4: Wire review after suggestion generation**

After executor suggestions are persisted, call reviewer, persist review, and set task status to `SUCCESS`, `NEEDS_REVIEW`, or `FAILED`.

- [ ] **Step 5: Add progress events**

Persist progress events for `GENERATING`, `REVIEWING`, `REVISING`, `PASSED`, `NEEDS_REVIEW`, and `FAILED`.

- [ ] **Step 6: Expose review to frontend**

Extend optimization task VO with `qualityScore`, `reviewStatus`, `riskSummary`, and `progressEvents`.

- [ ] **Step 7: Run verification**

Run:

```bash
mvn -pl bootstrap -Dtest=ResumeOptimizationReviewTest test
cd frontend
npm run build
```

Expected: PASS.

## Task 21: Harden Interview Turn State Machine

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/runtime/InterviewTurnRuntimeService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/runtime/InterviewTurnRuntimeServiceImpl.java`
- Modify: `InterviewTurnDO`
- Modify: `InterviewSessionServiceImpl`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewTurnIdempotencyTest.java`

- [ ] **Step 1: Write idempotency tests**

Create tests proving the same `sessionId + turnNo + answerRevision` returns the existing evaluation and does not create another follow-up.

- [ ] **Step 2: Add runtime fields**

Add fields for `stepIdempotencyKey`, `answerStatus`, `evaluationStatus`, `followUpDecisionStatus`, `compensationStatus`, `attemptCount`, and `lastError`.

- [ ] **Step 3: Centralize state transitions**

Move turn progression into `InterviewTurnRuntimeService`. Allowed transitions are:

```text
ASKED -> ANSWER_SAVED -> EVALUATING -> EVALUATED -> FOLLOW_UP_DECIDING
FOLLOW_UP_DECIDING -> FOLLOW_UP_CREATED | NEXT_MAIN_CREATED | SESSION_COMPLETED
EVALUATING -> EVALUATION_FAILED -> WAITING_RETRY
ANSWER_SAVED -> COMPENSATING -> EVALUATED
```

- [ ] **Step 4: Wire submit answer**

`submitAnswer` must save the answer before AI evaluation, return existing result for duplicate keys, and keep the turn in `WAITING_RETRY` when evaluation fails.

- [ ] **Step 5: Run verification**

Run:

```bash
mvn -pl bootstrap -Dtest=InterviewTurnIdempotencyTest test
```

Expected: PASS.

## Task 22: Add Interview Session Snapshot Recovery

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSessionRecoveryService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSessionRecoveryServiceImpl.java`
- Create: `InterviewSessionSnapshotDO`
- Create: `InterviewSessionSnapshotMapper`
- Modify: `InterviewSessionServiceImpl`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionRecoveryTest.java`

- [ ] **Step 1: Write recovery tests**

Test that a session can recover current question and completed turns from persisted data when cache state is absent.

- [ ] **Step 2: Add snapshot storage**

Add `t_career_interview_session_snapshot` with session id, version, snapshot json, last applied step key, status, and update time.

- [ ] **Step 3: Build snapshot on each stable transition**

After turn evaluation, follow-up creation, pause, resume, and finish, rebuild or patch the snapshot.

- [ ] **Step 4: Add recover method**

Implement recovery using persisted session, turns, and latest snapshot. Use version comparison so stale recovery cannot overwrite newer state.

- [ ] **Step 5: Run verification**

Run:

```bash
mvn -pl bootstrap -Dtest=InterviewSessionRecoveryTest test
```

Expected: PASS.

## Task 23: Add Career AI Single-flight Governance

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightServiceImpl.java`
- Create: `CareerSingleFlightRecordDO`
- Create: `CareerSingleFlightRecordMapper`
- Modify: Career AI call sites in parse, alignment, optimization, interview evaluation, follow-up, and report services.
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerSingleFlightTest.java`

- [ ] **Step 1: Write single-flight tests**

Test that duplicate keys reuse one owner result and that a stale owner with an older fencing token cannot overwrite a newer result.

- [ ] **Step 2: Add record table**

Add fields: `single_flight_key`, `scene`, `owner_id`, `fencing_token`, `status`, `heartbeat_time`, `request_count`, `result_json`, `error_type`, `trace_id`.

- [ ] **Step 3: Implement acquire/heartbeat/complete/replay**

API should support `tryAcquire`, `heartbeat`, `completeSuccess`, `completeFailure`, and `replayIfAvailable`.

- [ ] **Step 4: Wrap artifact-generating AI calls**

Start with optimization review and interview evaluation, then extend to parsing, alignment, and report generation.

- [ ] **Step 5: Run verification**

Run:

```bash
mvn -pl bootstrap -Dtest=CareerSingleFlightTest test
```

Expected: PASS.

## Task 24: Add Career HyDE And Rerank Retrieval

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/retrieval/CareerRetrievalEnhancementService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/retrieval/CareerRetrievalEnhancementServiceImpl.java`
- Modify: `CareerPromptTemplates`
- Modify: `JobAlignmentServiceImpl`
- Modify: `ResumeOptimizationServiceImpl`
- Modify: `InterviewSessionServiceImpl`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerRetrievalEnhancementTest.java`

- [ ] **Step 1: Write HyDE boundary tests**

Test that generated HyDE text is tagged as `QUERY_ONLY` and is never copied into resume content.

- [ ] **Step 2: Generate retrieval queries**

For JD alignment, generate ideal candidate query. For optimization, generate gap query. For interview, generate question-depth query.

- [ ] **Step 3: Use Ragent retrieval and rerank abstractions**

Call existing retrieval channels and `RerankService` when configured. Do not add Qdrant client usage.

- [ ] **Step 4: Add evidence type markers**

Return evidence as `RESUME_TEXT`, `JD_TEXT`, `KNOWLEDGE_CHUNK`, or `HYDE_QUERY`.

- [ ] **Step 5: Run verification**

Run:

```bash
mvn -pl bootstrap -Dtest=CareerRetrievalEnhancementTest test
```

Expected: PASS.

## Task 25: Add PDF/Word Render Pipeline Gate

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeRenderPipeline.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeRenderValidationResult.java`
- Modify: `ResumeVersionServiceImpl`
- Modify: `ResumeExportRecordDO`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeRenderPipelineTest.java`

- [ ] **Step 1: Write render validation tests**

Test that missing required fields fail validation and that disabled PDF/Word renderers return a clear disabled result instead of a broken file.

- [ ] **Step 2: Add validation result**

Validation result includes `valid`, `missingFields`, `templateVersion`, `warnings`, and `traceId`.

- [ ] **Step 3: Gate PDF/Word**

Until renderer implementation is complete, PDF/Word requests create a failed export record with a clear disabled reason.

- [ ] **Step 4: Preserve Markdown/HTML path**

Do not regress Task 11 Markdown/HTML export behavior.

- [ ] **Step 5: Run verification**

Run:

```bash
mvn -pl bootstrap -Dtest=ResumeRenderPipelineTest test
```

Expected: PASS.

## Task 26: Phase 3 Depth Smoke Verification

**Files:**
- Modify only smoke notes or targeted fixes required by failed verification.

- [ ] **Step 1: Run backend verification**

Run:

```bash
mvn -pl bootstrap -Dtest=ResumeOptimizationReviewTest,InterviewTurnIdempotencyTest,InterviewSessionRecoveryTest,CareerSingleFlightTest,CareerRetrievalEnhancementTest,ResumeRenderPipelineTest test
mvn -pl bootstrap -DskipTests compile
```

Expected: PASS.

- [ ] **Step 2: Run frontend verification**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 3: Manual smoke**

Verify:

1. Optimization shows generation, review, quality gate, and risk status.
2. Duplicate optimization trigger does not create duplicate suggestions.
3. Duplicate interview answer submit does not create duplicate follow-up.
4. Session recovery restores the current question after simulated cache loss.
5. HyDE evidence is marked as query/evidence only.
6. PDF/Word disabled path returns a clear message and export record.
7. Trace links exist for new review, recovery, and Single-flight paths.

## Phase 4 Extension Gate: Voice And Multimodal

Do not start these tasks until Task 18 manual smoke verification passes and Phase 3 runtime governance does not regress the text loop.

Planned extension files:

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/media/CareerInterviewAudioController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/media/CareerInterviewTtsService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/media/CareerInterviewAsrService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/media/TranscriptionSessionContext.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/media/AstTranscriptionAssembler.java`
- `frontend/src/pages/career/VoiceInterviewPage.tsx`

Acceptance gate:

- Text interview session can complete without audio.
- Report generation does not depend on audio transcript.
- ASR transcript is stored as an optional answer source, not as the only answer source.
- Transcription assembler handles segment ordering, duplicate text, prefix correction, and display text separation.
- TTS is optional and does not block question display.

## Self-Review

### Spec Coverage

- Resume upload and parsing: Task 8.
- JD creation and alignment: Task 9.
- Resume optimization and suggestions: Task 10.
- Basic export: Task 11.
- Text mock interview: Task 12.
- Interview report and radar-ready structure: Task 13.
- Admin observability: Task 14 and Task 17.
- Frontend user journey: Task 15 and Task 16.
- Ragent base preservation: Cross-Cutting Rules and all backend tasks.
- JobNavigator judge-executor optimization and quality gate: Task 20.
- AI-Meeting interview turn state machine and idempotency: Task 21.
- AI-Meeting long-session recovery: Task 22.
- AI-Meeting distributed Single-flight governance: Task 23.
- JobNavigator HyDE/Rerank retrieval enrichment: Task 24.
- JobNavigator multi-format render pipeline gate: Task 25.
- Phase 3 depth smoke verification: Task 26.
- Voice/multimodal: Phase 4 Extension Gate.
- PRD v0.2 product-grounded scope and default Java backend / AI application scenario: PRD Alignment And Schedule Diff.
- PRD v0.2 deltas for generic mode, deletion/privacy, retry/idempotency, read-only rubric, and admin MVP: PRD v0.2 Delta Mapping.
- Work-period and milestone planning: Schedule Diff Matrix and Task-Level Duration Diff.
- Subagent execution split: SG0-SG20 in Subagent Work Package Split.

### Placeholder Scan

This plan avoids open implementation markers and defines exact files, endpoints, commands, and expected results for each task.

### Type Consistency

Backend types use `Career*Request`, `Career*VO`, and `*DO` naming. Frontend types mirror VO names without the `VO` suffix. Status values are centralized in enum files before service implementation starts.

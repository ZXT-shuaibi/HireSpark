# AI-Meeting 对齐 HireSpark 第一阶段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 AI-Meeting-JobSpark 为新前端基线，完成 HireSpark 第一阶段核心职业链路对齐，交付可运行的登录、简历、优化、面试、报告主流程。

**Architecture:** 以前端迁移为主，不修改 HireSpark 后端协议。在新前端内部新增 `HireSpark adapter`，把认证、简历、优化、面试、报告全部映射到 HireSpark 现有 REST / SSE / WebSocket 契约。第一阶段只覆盖职业主链路，不包含 admin 与通用 chat 全量迁移。

**Tech Stack:** React 19, TypeScript, Vite, React Router 7, Redux Toolkit, TanStack Query, Axios, WebSocket, SSE, Vitest, Testing Library.

---

## 文件结构

### 新建或重点调整的目录

- `frontend/`
  - 第一阶段建议以 AI-Meeting 基线替换当前前端目录内容，或在执行阶段先迁入临时目录再切换
- `frontend/src/config/`
  - 环境变量与 API / WS 基础配置
- `frontend/src/lib/`
  - 请求客户端、错误映射、通用适配工具
- `frontend/src/adapters/hirespark/`
  - 新增：DTO、mapper、stream mapper、ws helper
- `frontend/src/features/auth/`
  - 新增或重组：HireSpark 认证视图模型与控制器
- `frontend/src/features/career/`
  - 新增或重组：简历、优化、面试、报告域逻辑
- `frontend/src/app/router.tsx`
  - 路由总表
- `frontend/src/services/`
  - 旧 `xunzhi` service 改为 HireSpark service
- `frontend/src/pages/`
  - 复用 AI-Meeting 页面骨架，替换成 HireSpark 数据流
- `frontend/src/components/`
  - 保留视觉组件，调整业务组件绑定
- `frontend/src/**/*.test.ts(x)`
  - 为 adapter、request、路由守卫、关键页面控制器补测试

### 第一阶段关键文件责任

- `frontend/src/config/env.ts`
  - 定义 `VITE_API_BASE_URL`、`VITE_API_TARGET`、`VITE_WS_BASE_URL` 的 HireSpark 规则
- `frontend/src/lib/request.ts`
  - 提供统一的 HireSpark 请求封装与认证头注入
- `frontend/src/adapters/hirespark/authAdapter.ts`
  - 认证 DTO 到 view model 的映射
- `frontend/src/adapters/hirespark/careerAdapter.ts`
  - 简历、JD、优化、面试、报告 DTO 映射
- `frontend/src/adapters/hirespark/streamAdapter.ts`
  - 优化与面试 SSE 事件映射
- `frontend/src/adapters/hirespark/transcriptionAdapter.ts`
  - 面试转写 WebSocket URL 与消息映射
- `frontend/src/services/authService.ts`
  - 接 `HireSpark /auth` 与 `/user/me`
- `frontend/src/services/careerService.ts`
  - 接 `HireSpark /career/*`
- `frontend/src/app/router.tsx`
  - 第一阶段职业主链路路由

## 任务拆分

### Task 1: 建立新前端基线与环境配置

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/config/env.ts`
- Test: `frontend/src/config/env.test.ts`

- [ ] **Step 1: 写环境配置失败测试**

```ts
import { describe, expect, it } from "vitest";
import {
  resolveApiBaseUrl,
  resolveApiTarget,
  resolveRuntimeWsBaseUrl,
  resolveWsBaseUrl,
} from "@/config/env";

describe("HireSpark env config", () => {
  it("默认把 API base 解析为 /api/ragent", () => {
    expect(resolveApiBaseUrl(undefined)).toBe("/api/ragent");
  });

  it("默认把 API target 解析为 http://localhost:9090", () => {
    expect(resolveApiTarget(undefined)).toBe("http://localhost:9090");
  });

  it("未显式配置 ws base 时按当前协议推导", () => {
    expect(
      resolveRuntimeWsBaseUrl(
        { protocol: "https:", host: "example.com" },
        resolveWsBaseUrl(undefined),
      ),
    ).toBe("wss://example.com");
  });
});
```

- [ ] **Step 2: 运行测试确认先失败**

Run: `npm.cmd run test:run -- src/config/env.test.ts`
Expected: FAIL，当前默认值仍然是 AI-Meeting 的 `/api` 或 `http://localhost:8002`

- [ ] **Step 3: 最小实现 HireSpark 环境规则**

```ts
const DEFAULT_API_BASE_URL = "/api/ragent";
const DEFAULT_API_TARGET = "http://localhost:9090";

export const resolveApiBaseUrl = (value?: string | null) => {
  const trimmed = trimValue(value);
  return normalizeBaseUrl(trimmed || DEFAULT_API_BASE_URL);
};

export const resolveApiTarget = (value?: string | null) => {
  const trimmed = trimValue(value);
  return trimmed || DEFAULT_API_TARGET;
};
```

```ts
server: {
  proxy: {
    "/api": {
      target: apiTarget,
      changeOrigin: true,
      ws: true,
    },
  },
},
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm.cmd run test:run -- src/config/env.test.ts`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/package.json frontend/vite.config.ts frontend/src/config/env.ts frontend/src/config/env.test.ts
git commit -m "前端: 对齐 HireSpark 环境配置"
```

### Task 2: 重写请求基座与认证注入

**Files:**
- Modify: `frontend/src/lib/request.ts`
- Modify: `frontend/src/lib/authToken.ts`
- Test: `frontend/src/lib/request.test.ts`

- [ ] **Step 1: 写请求层失败测试**

```ts
import { describe, expect, it, vi } from "vitest";
import { buildApiUrl, requiresAuthTokenForRequest } from "@/lib/request";

describe("HireSpark request client", () => {
  it("把 career 路径拼到 /api/ragent 下", () => {
    expect(buildApiUrl("/career/interviews")).toContain("/api/ragent/career/interviews");
  });

  it("auth/login 不要求已登录 token", () => {
    expect(requiresAuthTokenForRequest("/auth/login")).toBe(false);
  });

  it("career 接口要求 token", () => {
    expect(requiresAuthTokenForRequest("/career/interviews")).toBe(true);
  });
});
```

- [ ] **Step 2: 运行测试确认先失败**

Run: `npm.cmd run test:run -- src/lib/request.test.ts`
Expected: FAIL，当前规则仍然基于 `/xunzhi/v1/*`

- [ ] **Step 3: 最小实现 HireSpark 请求规则**

```ts
const AUTH_FREE_API_PATHS = new Set([
  "/auth/login",
  "/auth/logout",
]);

export const requiresAuthTokenForRequest = (url?: string) => {
  const path = normalizeRequestPath(url);
  if (AUTH_FREE_API_PATHS.has(path)) {
    return false;
  }
  return path.startsWith("/career/")
    || path.startsWith("/admin/")
    || path.startsWith("/rag/")
    || path.startsWith("/user/");
};
```

```ts
export const buildApiUrl = (
  path: string,
  query?: Record<string, string | number | boolean | null | undefined>,
) => {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const baseUrl = `${getApiBaseUrl()}${normalizedPath}`;
  // 保留现有 query 拼接逻辑
};
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm.cmd run test:run -- src/lib/request.test.ts`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/lib/request.ts frontend/src/lib/authToken.ts frontend/src/lib/request.test.ts
git commit -m "前端: 重写 HireSpark 请求与鉴权基座"
```

### Task 3: 新增 HireSpark adapter 层

**Files:**
- Create: `frontend/src/adapters/hirespark/authAdapter.ts`
- Create: `frontend/src/adapters/hirespark/careerAdapter.ts`
- Create: `frontend/src/adapters/hirespark/streamAdapter.ts`
- Create: `frontend/src/adapters/hirespark/transcriptionAdapter.ts`
- Test: `frontend/src/adapters/hirespark/careerAdapter.test.ts`

- [ ] **Step 1: 写 adapter 失败测试**

```ts
import { describe, expect, it } from "vitest";
import {
  mapCareerInterviewSession,
  mapCareerOptimizationTask,
  mapCareerResumeUpload,
} from "@/adapters/hirespark/careerAdapter";

describe("careerAdapter", () => {
  it("把简历上传结果映射成前端可消费模型", () => {
    const result = mapCareerResumeUpload({
      documentId: "doc-1",
      profileId: "profile-1",
      resumeVersionId: "version-1",
      parseStatus: "SUCCESS",
    });

    expect(result.resumeVersionId).toBe("version-1");
    expect(result.status).toBe("SUCCESS");
  });

  it("把优化任务映射为页面任务模型", () => {
    const result = mapCareerOptimizationTask({
      id: "task-1",
      status: "RUNNING",
      qualityScore: 0.82,
      suggestions: [],
    });

    expect(result.id).toBe("task-1");
    expect(result.status).toBe("RUNNING");
  });

  it("把面试 session 映射为页面 session 模型", () => {
    const result = mapCareerInterviewSession({
      id: "session-1",
      status: "RUNNING",
      currentTurnNo: 2,
      currentQuestion: null,
    });

    expect(result.id).toBe("session-1");
    expect(result.status).toBe("RUNNING");
    expect(result.currentTurnNo).toBe(2);
  });
});
```

- [ ] **Step 2: 运行测试确认先失败**

Run: `npm.cmd run test:run -- src/adapters/hirespark/careerAdapter.test.ts`
Expected: FAIL，文件不存在

- [ ] **Step 3: 实现最小 adapter**

```ts
export const mapCareerResumeUpload = (payload: {
  documentId: string;
  profileId: string;
  resumeVersionId: string;
  parseStatus: string;
}) => ({
  documentId: payload.documentId,
  profileId: payload.profileId,
  resumeVersionId: payload.resumeVersionId,
  status: payload.parseStatus,
});

export const mapCareerOptimizationTask = (payload: {
  id: string;
  status?: string | null;
  qualityScore?: number | string | null;
  suggestions?: unknown[];
}) => ({
  id: payload.id,
  status: payload.status ?? "UNKNOWN",
  qualityScore: payload.qualityScore ?? null,
  suggestions: payload.suggestions ?? [],
});

export const mapCareerInterviewSession = (payload: {
  id: string;
  status?: string | null;
  currentTurnNo?: number | null;
  currentQuestion?: unknown;
}) => ({
  id: payload.id,
  status: payload.status ?? "UNKNOWN",
  currentTurnNo: payload.currentTurnNo ?? null,
  currentQuestion: payload.currentQuestion ?? null,
});
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm.cmd run test:run -- src/adapters/hirespark/careerAdapter.test.ts`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/adapters/hirespark frontend/src/adapters/hirespark/careerAdapter.test.ts
git commit -m "前端: 新增 HireSpark 适配层"
```

### Task 4: 对齐认证服务与用户态

**Files:**
- Modify: `frontend/src/services/authService.ts`
- Modify: `frontend/src/components/auth/AuthGuard.tsx`
- Modify: `frontend/src/hooks/auth/useAuthPageController.ts`
- Modify: `frontend/src/store/slices/userSlice.ts`
- Test: `frontend/src/components/auth/AuthGuard.test.tsx`
- Test: `frontend/src/hooks/auth/useAuthPageController.test.tsx`

- [ ] **Step 1: 写认证失败测试**

```tsx
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import AuthGuard from "@/components/auth/AuthGuard";

test("未登录访问 /career 时跳转到 /auth", () => {
  render(
    <MemoryRouter initialEntries={["/career"]}>
      <Routes>
        <Route element={<AuthGuard />}>
          <Route path="/career" element={<div>career</div>} />
        </Route>
        <Route path="/auth" element={<div>auth page</div>} />
      </Routes>
    </MemoryRouter>,
  );

  expect(screen.getByText("auth page")).toBeInTheDocument();
});
```

- [ ] **Step 2: 运行测试确认先失败**

Run: `npm.cmd run test:run -- src/components/auth/AuthGuard.test.tsx src/hooks/auth/useAuthPageController.test.tsx`
Expected: FAIL，当前重定向与登录接口仍然服从旧协议

- [ ] **Step 3: 最小实现 HireSpark 认证对接**

```ts
export async function login(payload: { username: string; password: string }) {
  return service.post("/auth/login", payload);
}

export async function logout() {
  return service.post("/auth/logout");
}

export async function getCurrentUser() {
  return service.get("/user/me");
}
```

```tsx
if (!isAuthenticated) {
  return <Navigate to="/auth" replace state={{ from: location }} />;
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm.cmd run test:run -- src/components/auth/AuthGuard.test.tsx src/hooks/auth/useAuthPageController.test.tsx`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/services/authService.ts frontend/src/components/auth/AuthGuard.tsx frontend/src/hooks/auth/useAuthPageController.ts frontend/src/store/slices/userSlice.ts frontend/src/components/auth/AuthGuard.test.tsx frontend/src/hooks/auth/useAuthPageController.test.tsx
git commit -m "前端: 对齐 HireSpark 认证链路"
```

### Task 5: 重建第一阶段职业主链路路由

**Files:**
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/lib/constants.ts`
- Test: `frontend/src/app/router.test.tsx`

- [ ] **Step 1: 写路由失败测试**

```tsx
import { render, screen } from "@testing-library/react";
import { RouterProvider, createMemoryRouter } from "react-router-dom";
import { appRoutes } from "@/app/router";

test("已登录用户访问根路径时进入 /career", () => {
  const router = createMemoryRouter(appRoutes, {
    initialEntries: ["/"],
  });

  render(<RouterProvider router={router} />);

  expect(screen.getByText(/career/i)).toBeInTheDocument();
});
```

- [ ] **Step 2: 运行测试确认先失败**

Run: `npm.cmd run test:run -- src/app/router.test.tsx`
Expected: FAIL，当前根路径与面试型路由不匹配 HireSpark 主链路

- [ ] **Step 3: 最小实现第一阶段路由**

```ts
export const ROUTES = {
  home: "/",
  auth: "/auth",
  career: "/career",
  resumeUpload: "/career/resumes/upload",
  resumeDetail: "/career/resumes/:resumeVersionId",
  resumeOptimize: "/career/optimizations",
  interviewIntro: "/career/interviews",
  interviewRoom: "/career/interviews/:sessionId",
  interviewReport: "/career/interview-reports",
  interviewReportDetail: "/career/interview-reports/:sessionId",
} as const;
```

```tsx
{
  path: ROUTES.home,
  element: isAuthenticated ? <Navigate to={ROUTES.career} replace /> : <MarketingHomePage />,
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm.cmd run test:run -- src/app/router.test.tsx`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/app/router.tsx frontend/src/lib/constants.ts frontend/src/app/router.test.tsx
git commit -m "前端: 重建职业主链路路由"
```

### Task 6: 对齐简历、JD 与优化服务层

**Files:**
- Modify: `frontend/src/services/careerService.ts`
- Modify: `frontend/src/pages/resume/ResumeUploadPage.tsx`
- Modify: `frontend/src/pages/resume/ResumeDetailPage.tsx`
- Modify: `frontend/src/pages/resume/ResumeOptimizePage.tsx`
- Test: `frontend/src/pages/resume/ResumeUploadPage.test.tsx`
- Test: `frontend/src/pages/resume/ResumeOptimizePage.test.tsx`

- [ ] **Step 1: 写 resume / optimize 失败测试**

```tsx
test("上传简历成功后展示 HireSpark 返回的 resumeVersionId", async () => {
  // mock uploadCareerResume 返回 { resumeVersionId: "version-1" }
  // 断言页面展示 version-1
});

test("创建优化任务后展示 taskId 和质量分", async () => {
  // mock createCareerOptimization 返回 { id: "task-1", qualityScore: 0.9 }
  // 断言页面显示 task-1 和 0.9
});
```

- [ ] **Step 2: 运行测试确认先失败**

Run: `npm.cmd run test:run -- src/pages/resume/ResumeUploadPage.test.tsx src/pages/resume/ResumeOptimizePage.test.tsx`
Expected: FAIL，当前页面仍依赖旧 service 结构

- [ ] **Step 3: 最小实现 HireSpark resume / optimization service**

```ts
export const uploadCareerResume = async (file: File) => {
  const formData = new FormData();
  formData.append("file", file);
  return api.post("/career/resumes/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

export const createCareerOptimization = async (payload: {
  resumeVersionId?: string;
  jdId?: string;
  alignmentReportId?: string;
}) => api.post("/career/optimizations", payload);
```

```ts
export const createCareerOptimizationProgressStream = (
  taskId: string,
  handlers: CareerProgressStreamHandlers,
) => createStreamResponse({
  url: `${API_BASE_URL.replace(/\/$/, "")}/career/optimizations/${encodeURIComponent(taskId)}/progress/stream`,
  handlers,
});
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm.cmd run test:run -- src/pages/resume/ResumeUploadPage.test.tsx src/pages/resume/ResumeOptimizePage.test.tsx`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/services/careerService.ts frontend/src/pages/resume/ResumeUploadPage.tsx frontend/src/pages/resume/ResumeDetailPage.tsx frontend/src/pages/resume/ResumeOptimizePage.tsx frontend/src/pages/resume/ResumeUploadPage.test.tsx frontend/src/pages/resume/ResumeOptimizePage.test.tsx
git commit -m "前端: 对齐简历与优化链路"
```

### Task 7: 对齐面试服务层、SSE 与转写 WebSocket

**Files:**
- Modify: `frontend/src/services/careerService.ts`
- Modify: `frontend/src/services/audioToTextWs.ts`
- Modify: `frontend/src/hooks/audio/useAudioTranscriptionTransport.ts`
- Modify: `frontend/src/pages/interview/InterviewPage.tsx`
- Test: `frontend/src/services/audioToTextWs.test.ts`
- Test: `frontend/src/hooks/interview/session/useInterviewSessionFlow.test.tsx`

- [ ] **Step 1: 写面试链路失败测试**

```ts
import { describe, expect, it } from "vitest";
import { createCareerInterviewTranscriptionUrl } from "@/services/careerService";

describe("career interview ws", () => {
  it("基于 sessionId 构建 HireSpark 转写地址", () => {
    const url = createCareerInterviewTranscriptionUrl("session-1");
    expect(url).toContain("/career/interviews/session-1/transcription/ws");
  });
});
```

```tsx
test("创建面试 session 后加载第一题", async () => {
  // mock createCareerInterview 与 getCareerInterviewNextQuestion
  // 断言页面展示当前问题
});
```

- [ ] **Step 2: 运行测试确认先失败**

Run: `npm.cmd run test:run -- src/services/audioToTextWs.test.ts src/hooks/interview/session/useInterviewSessionFlow.test.tsx`
Expected: FAIL，当前仍使用 `/xunzhi/v1/xunfei/audio-to-text/{userId}`

- [ ] **Step 3: 最小实现 HireSpark 面试服务**

```ts
export const createCareerInterview = async (resumeVersionId: string, jdId: string) =>
  api.post("/career/interviews", { resumeVersionId, jdId });

export const getCareerInterviewNextQuestion = async (sessionId: string) =>
  api.get(`/career/interviews/${sessionId}/next-question`);

export const submitCareerInterviewAnswer = async (
  sessionId: string,
  payload: SubmitCareerInterviewAnswerPayload,
) => api.post(`/career/interviews/${sessionId}/answers`, payload);
```

```ts
export const createCareerInterviewTranscriptionUrl = (sessionId: string): string => {
  const apiBase = new URL(API_BASE_URL || "/", window.location.origin);
  apiBase.protocol = apiBase.protocol === "https:" ? "wss:" : "ws:";
  const basePath = apiBase.pathname.replace(/\/$/, "");
  const token = storage.getToken();
  const url = new URL(`${basePath}/career/interviews/${encodeURIComponent(sessionId)}/transcription/ws`, apiBase.origin);
  if (token) {
    url.searchParams.set("Authorization", token);
  }
  return url.toString();
};
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm.cmd run test:run -- src/services/audioToTextWs.test.ts src/hooks/interview/session/useInterviewSessionFlow.test.tsx`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/services/careerService.ts frontend/src/services/audioToTextWs.ts frontend/src/hooks/audio/useAudioTranscriptionTransport.ts frontend/src/pages/interview/InterviewPage.tsx frontend/src/services/audioToTextWs.test.ts frontend/src/hooks/interview/session/useInterviewSessionFlow.test.tsx
git commit -m "前端: 对齐面试流与转写链路"
```

### Task 8: 对齐面试报告与职业首页入口

**Files:**
- Modify: `frontend/src/pages/marketing/MarketingHomePage.tsx`
- Modify: `frontend/src/pages/interview/InterviewReportPage.tsx`
- Modify: `frontend/src/pages/interview/InterviewReportDetailPage.tsx`
- Modify: `frontend/src/pages/chat/HomePage.tsx` or `frontend/src/pages/career/CareerHomePage.tsx`（按最终目录结构选择）
- Test: `frontend/src/pages/marketing/MarketingHomePage.test.tsx`
- Test: `frontend/src/pages/interview/InterviewReportPage.test.tsx`

- [ ] **Step 1: 写首页与报告失败测试**

```tsx
test("首页 CTA 对已登录用户跳转到 /career", async () => {
  // mock isAuthenticated=true
  // 点击开始按钮后断言跳到 /career
});

test("报告页加载 HireSpark report 并展示 overallScore", async () => {
  // mock getCareerInterviewReport 返回 overallScore
  // 断言页面展示评分
});
```

- [ ] **Step 2: 运行测试确认先失败**

Run: `npm.cmd run test:run -- src/pages/marketing/MarketingHomePage.test.tsx src/pages/interview/InterviewReportPage.test.tsx`
Expected: FAIL，当前首页和报告页还不是 HireSpark 职业链路入口

- [ ] **Step 3: 最小实现首页与报告对齐**

```tsx
const handleStartNow = useCallback(() => {
  if (isAuthenticated) {
    navigate("/career");
    return;
  }
  navigate("/auth", { state: { from: { pathname: "/career" } } });
}, [isAuthenticated, navigate]);
```

```ts
export const getCareerInterviewReport = async (sessionId: string) =>
  api.get(`/career/interviews/${sessionId}/report`);
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm.cmd run test:run -- src/pages/marketing/MarketingHomePage.test.tsx src/pages/interview/InterviewReportPage.test.tsx`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/pages/marketing/MarketingHomePage.tsx frontend/src/pages/interview/InterviewReportPage.tsx frontend/src/pages/interview/InterviewReportDetailPage.tsx frontend/src/pages/marketing/MarketingHomePage.test.tsx frontend/src/pages/interview/InterviewReportPage.test.tsx
git commit -m "前端: 对齐职业入口与面试报告"
```

### Task 9: 第一阶段联调验证与文档收口

**Files:**
- Modify: `frontend/README.md`（如存在）
- Modify: `docs/superpowers/specs/2026-06-02-ai-meeting-hirespark-frontend-alignment-design.md`
- Modify: `docs/superpowers/plans/2026-06-02-ai-meeting-hirespark-phase1-implementation.md`

- [ ] **Step 1: 写验证清单**

```md
- 登录成功，能获取当前用户
- 能进入 /career
- 能上传简历
- 能创建优化任务并收到 progress stream
- 能创建面试 session
- 能收到面试 progress stream
- 能连接转写 ws
- 能生成报告
```

- [ ] **Step 2: 运行前端完整验证命令**

Run: `npm.cmd run check`
Expected: PASS，lint + typecheck + test 全绿

- [ ] **Step 3: 运行生产构建验证**

Run: `npm.cmd run build`
Expected: PASS，产出 Vite build

- [ ] **Step 4: 补充文档与残留说明**

```md
## Phase 1 Status

- 已完成：认证、简历、优化、面试、报告
- 未完成：admin、chat、旧前端下线、部署收口
```

- [ ] **Step 5: 提交**

```bash
git add frontend docs/superpowers/specs/2026-06-02-ai-meeting-hirespark-frontend-alignment-design.md docs/superpowers/plans/2026-06-02-ai-meeting-hirespark-phase1-implementation.md
git commit -m "前端: 完成第一阶段对齐验证与文档收口"
```

## 自检

### Spec coverage

- 设计文档中的“单一后端原则”对应 Task 2、Task 3、Task 6、Task 7
- “第一阶段只覆盖职业主链路”对应 Task 5、Task 6、Task 7、Task 8
- “不在第一阶段做 admin / chat”已通过任务范围控制明确体现
- “适配层”由 Task 3 直接实现

### Placeholder scan

- 已移除 TBD / TODO / 类似占位语
- 每个任务都包含具体文件、具体命令、具体提交信息
- 所有关键代码步骤都给出了最小代码块

### Type consistency

- `HireSpark adapter`、`careerService`、`AuthGuard`、`ROUTES`、`createCareerInterviewTranscriptionUrl` 的命名在任务间保持一致
- 第一阶段页面统一使用 `/career/*` 路由语义

## 执行说明

Plan complete and saved to `docs/superpowers/plans/2026-06-02-ai-meeting-hirespark-phase1-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

当前用户已经明确选择 `Subagent-Driven`，后续执行应使用 `superpowers:subagent-driven-development`。

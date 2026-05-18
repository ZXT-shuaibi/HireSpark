import { api } from "./api";

export type CareerRecord = Record<string, unknown>;

export interface CareerResumeUpload {
  documentId: string;
  profileId: string;
  resumeVersionId: string;
  parseStatus: string;
}

export interface CareerResumeVersion {
  id: string;
  profileId?: string | null;
  versionNo?: number | null;
  title?: string | null;
  content?: string | null;
  markdownContent?: string | null;
  createTime?: string | null;
}

export interface CareerResumeExport {
  id: string;
  resumeVersionId: string;
  exportType: string;
  fileUrl?: string | null;
  status?: string | null;
  errorMessage?: string | null;
  templateVersion?: string | null;
  validationResultJson?: string | null;
  traceId?: string | null;
}

export interface CareerJob {
  id: string;
  title?: string | null;
  company?: string | null;
  rawText?: string | null;
  parsed?: CareerRecord | null;
  createTime?: string | null;
}

export interface CareerAlignmentReport {
  id: string;
  resumeVersionId: string;
  jdId: string;
  score?: number | null;
  summary?: string | null;
  evidence?: unknown[];
  gaps?: unknown[];
  risks?: unknown[];
  traceId?: string | null;
}

export interface CareerProgressEvent {
  id: string;
  eventType?: string | null;
  message?: string | null;
  payloadJson?: string | null;
  createTime?: string | null;
}

export interface CareerOptimizationSuggestion {
  id: string;
  category?: string | null;
  title?: string | null;
  originalText?: string | null;
  suggestedText?: string | null;
  reason?: string | null;
  riskLevel?: string | null;
  status?: string | null;
}

export interface CareerOptimizationTask {
  id: string;
  status?: string | null;
  resumeVersionId?: string | null;
  jdId?: string | null;
  summary?: string | null;
  qualityScore?: number | string | null;
  reviewStatus?: string | null;
  riskSummary?: string | null;
  suggestions?: CareerOptimizationSuggestion[];
  progressEvents?: CareerProgressEvent[];
  traceId?: string | null;
}

export interface CareerInterviewTurn {
  id?: string | null;
  sessionId?: string | null;
  turnNo?: number | null;
  turnType?: string | null;
  question?: string | null;
  answer?: string | null;
  score?: number | null;
  feedback?: CareerRecord | null;
  status?: string | null;
  stepIdempotencyKey?: string | null;
  answerStatus?: string | null;
  evaluationStatus?: string | null;
  followUpDecisionStatus?: string | null;
  compensationStatus?: string | null;
  attemptCount?: number | null;
  lastError?: string | null;
}

export interface CareerInterviewSession {
  id: string;
  status?: string | null;
  plan?: CareerRecord | null;
  currentTurnNo?: number | null;
  currentQuestion?: CareerInterviewTurn | null;
}

export interface CareerInterviewReport {
  id: string;
  sessionId: string;
  overallScore?: number | null;
  radar?: unknown[];
  playback?: unknown[];
  suggestions?: unknown[];
  summary?: string | null;
  traceId?: string | null;
  createTime?: string | null;
}

export interface CareerAdminOverview {
  resumeDocuments?: number | null;
  candidateProfiles?: number | null;
  resumeVersions?: number | null;
  jobDescriptions?: number | null;
  alignmentReports?: number | null;
  optimizationTasks?: number | null;
  optimizationReviewPassed?: number | null;
  optimizationReviewNeedsRevision?: number | null;
  optimizationReviewBlockedByRisk?: number | null;
  interviewSessions?: number | null;
  interviewReports?: number | null;
  singleFlightRecords?: number | null;
  singleFlightRunning?: number | null;
  singleFlightSuccess?: number | null;
  singleFlightFailed?: number | null;
  taskAttempts?: number | null;
  taskAttemptFailed?: number | null;
  taskAttemptReplayed?: number | null;
  failedExports?: number | null;
}

export interface CareerAdminTask {
  id: string;
  type?: string | null;
  status?: string | null;
  userId?: string | null;
  businessId?: string | null;
  summary?: string | null;
  failureReason?: string | null;
  traceId?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  qualityScore?: number | string | null;
  reviewStatus?: string | null;
  riskFlag?: boolean | null;
  runtimeStatus?: string | null;
  scene?: string | null;
  fencingToken?: number | string | null;
  requestCount?: number | null;
  currentTurnNo?: number | null;
  modelName?: string | null;
  replayed?: boolean | null;
  latencyMs?: number | null;
}

export interface CareerAdminRubricDimension {
  id?: string | null;
  name?: string | null;
  weight?: number | null;
  description?: string | null;
  signals?: string[];
}

export interface CareerAdminRubric {
  id: string;
  name?: string | null;
  version?: string | null;
  editable?: boolean;
  dimensions?: CareerAdminRubricDimension[];
}

export interface CreateCareerJobPayload {
  title?: string;
  company?: string;
  rawText?: string;
  sourceType?: string;
  sourceLocation?: string;
}

export interface CreateCareerOptimizationPayload {
  resumeVersionId?: string;
  jdId?: string;
  alignmentReportId?: string;
}

export interface SubmitCareerInterviewAnswerPayload {
  turnNo?: number;
  answer?: string;
  answerRevision?: string;
}

export const uploadCareerResume = async (file: File): Promise<CareerResumeUpload> => {
  const formData = new FormData();
  formData.append("file", file);
  return api.post<CareerResumeUpload, CareerResumeUpload>("/career/resumes/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
};

export const getCareerResumeVersion = async (versionId: string): Promise<CareerResumeVersion> => {
  return api.get<CareerResumeVersion, CareerResumeVersion>(`/career/resumes/versions/${versionId}`);
};

export const listCareerResumeVersions = async (profileId: string): Promise<CareerResumeVersion[]> => {
  return api.get<CareerResumeVersion[], CareerResumeVersion[]>(`/career/profiles/${profileId}/versions`);
};

export const exportCareerResume = async (
  resumeVersionId: string,
  exportType = "PDF"
): Promise<CareerResumeExport> => {
  return api.post<CareerResumeExport, CareerResumeExport>("/career/resumes/export", {
    resumeVersionId,
    exportType
  });
};

export const createCareerJob = async (payload: CreateCareerJobPayload): Promise<CareerJob> => {
  return api.post<CareerJob, CareerJob>("/career/jobs", payload);
};

export const getCareerJob = async (jdId: string): Promise<CareerJob> => {
  return api.get<CareerJob, CareerJob>(`/career/jobs/${jdId}`);
};

export const createCareerAlignment = async (
  resumeVersionId: string,
  jdId: string
): Promise<CareerAlignmentReport> => {
  return api.post<CareerAlignmentReport, CareerAlignmentReport>("/career/alignments", {
    resumeVersionId,
    jdId
  });
};

export const getCareerAlignment = async (reportId: string): Promise<CareerAlignmentReport> => {
  return api.get<CareerAlignmentReport, CareerAlignmentReport>(`/career/alignments/${reportId}`);
};

export const createCareerOptimization = async (
  payload: CreateCareerOptimizationPayload
): Promise<CareerOptimizationTask> => {
  return api.post<CareerOptimizationTask, CareerOptimizationTask>("/career/optimizations", payload);
};

export const getCareerOptimization = async (taskId: string): Promise<CareerOptimizationTask> => {
  return api.get<CareerOptimizationTask, CareerOptimizationTask>(`/career/optimizations/${taskId}`);
};

export const decideCareerOptimizationSuggestion = async (
  suggestionId: string,
  status: string,
  editedText?: string
): Promise<CareerOptimizationSuggestion> => {
  return api.put<CareerOptimizationSuggestion, CareerOptimizationSuggestion>(
    `/career/optimizations/suggestions/${suggestionId}`,
    { status, editedText }
  );
};

export const generateCareerOptimizationVersion = async (taskId: string): Promise<CareerResumeVersion> => {
  return api.post<CareerResumeVersion, CareerResumeVersion>(`/career/optimizations/${taskId}/versions`);
};

export const createCareerInterview = async (
  resumeVersionId: string,
  jdId: string
): Promise<CareerInterviewSession> => {
  return api.post<CareerInterviewSession, CareerInterviewSession>("/career/interviews", {
    resumeVersionId,
    jdId
  });
};

export const getCareerInterview = async (sessionId: string): Promise<CareerInterviewSession> => {
  return api.get<CareerInterviewSession, CareerInterviewSession>(`/career/interviews/${sessionId}`);
};

export const getCareerInterviewNextQuestion = async (sessionId: string): Promise<CareerInterviewTurn> => {
  return api.get<CareerInterviewTurn, CareerInterviewTurn>(`/career/interviews/${sessionId}/next-question`);
};

export const submitCareerInterviewAnswer = async (
  sessionId: string,
  payload: SubmitCareerInterviewAnswerPayload
): Promise<CareerInterviewTurn> => {
  return api.post<CareerInterviewTurn, CareerInterviewTurn>(`/career/interviews/${sessionId}/answers`, payload);
};

export const recoverCareerInterview = async (sessionId: string): Promise<CareerInterviewSession> => {
  return api.post<CareerInterviewSession, CareerInterviewSession>(`/career/interviews/${sessionId}/recover`);
};

export const generateCareerInterviewReport = async (sessionId: string): Promise<CareerInterviewReport> => {
  return api.post<CareerInterviewReport, CareerInterviewReport>(`/career/interviews/${sessionId}/report`);
};

export const getCareerInterviewReport = async (sessionId: string): Promise<CareerInterviewReport> => {
  return api.get<CareerInterviewReport, CareerInterviewReport>(`/career/interviews/${sessionId}/report`);
};

export const getCareerAdminOverview = async (): Promise<CareerAdminOverview> => {
  return api.get<CareerAdminOverview, CareerAdminOverview>("/admin/career/overview");
};

export const listCareerAdminTasks = async (params?: {
  limit?: number;
  type?: string;
  status?: string;
}): Promise<CareerAdminTask[]> => {
  return api.get<CareerAdminTask[], CareerAdminTask[]>("/admin/career/tasks", { params });
};

export const listCareerAdminRubrics = async (): Promise<CareerAdminRubric[]> => {
  return api.get<CareerAdminRubric[], CareerAdminRubric[]>("/admin/career/rubrics");
};

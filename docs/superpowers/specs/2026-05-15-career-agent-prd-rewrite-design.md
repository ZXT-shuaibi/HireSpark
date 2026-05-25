# Career Agent PRD Rewrite Design

## Purpose

Rewrite `docs/career-agent-platform/prd-career-agent-platform.md` from a BRD-like concept document into a product-grounded PRD that can support demand review, engineering decomposition, testing, and project defense.

The rewritten PRD will remain the single source of truth. It will directly overwrite the current PRD and advance it to v0.2.

## Confirmed Decisions

- PRD style: product-grounded PRD, not pure business narrative and not pure engineering specification.
- Rewrite target: directly overwrite `docs/career-agent-platform/prd-career-agent-platform.md`.
- Detailed scope: expand Phase 1 and Phase 2 to product-detail level.
- Roadmap scope: keep Phase 3 and Phase 4 at roadmap level.
- MVP primary scenario: Java backend / AI application development job seeker.
- MVP primary JD: Java backend engineer with Spring Boot, RAG, Agent, PostgreSQL, Redis, and engineering quality expectations.
- MVP text loop: resume parsing, JD alignment, resume optimization, text mock interview, report, and feedback into the next resume iteration.
- AI output contracts: include structured output requirements in the PRD.
- Competitive analysis: rewrite as capability matrices plus explicit product trade-offs.

## Current PRD Diagnosis

The current PRD is valuable as a concept and review draft, but it is too high-level for product delivery.

What works:

- It explains why Ragent should be the base project.
- It identifies JobNavigator and AI-Meeting as capability sources.
- It describes the closed loop from resume to JD alignment to interview review.
- It states important integration boundaries such as one user system, one model routing system, and one trace system.

What must be improved:

- User stories are listed as intentions, but not expanded into executable scenarios.
- Functional requirements do not define inputs, outputs, states, empty states, errors, or permissions.
- AI behavior is described as capability, but the structured output fields are not contractual.
- Frontend pages are listed, but page tasks and transitions are not clear.
- Competitive analysis names products, but does not compare capability dimensions sharply.
- Acceptance criteria are too broad to drive testing.

## Rewritten PRD Structure

### 1. Version Summary And Review Conclusion

This section will state that v0.2 turns the previous review draft into an executable PRD.

It will preserve these review conclusions:

- Ragent is the only runtime base.
- JobNavigator and AI-Meeting are capability sources, not embedded sub-systems.
- Phase 1 and Phase 2 are the MVP implementation target.
- Voice, ASR/TTS, and multimodal interview analysis stay out of MVP.

### 2. Product Goal, Non-Goals, And Success Metrics

This section will define what success means in product terms.

Success metrics:

- A user can complete the whole loop from resume upload to interview report.
- Every AI-generated recommendation is tied to a resume version, JD, and trace record.
- The frontend can render AI outputs from structured fields without parsing free-form prose.
- A reviewer can inspect task status and trace evidence for the core AI chain.

Non-goals:

- Do not build a recruiting ATS.
- Do not rank real candidates for employers.
- Do not add payment, membership, or third-party job-board account integration.
- Do not build video anti-cheating.
- Do not promise AI scores are valid hiring decisions.

### 3. Persona And Primary Scenario

The PRD will focus on one default scenario to avoid generic product drift.

Primary persona:

- Name: Java backend / AI application job seeker.
- Experience: fresh graduate to 3 years of experience.
- Goal: convert a technical project resume into a job-targeted resume and interview training loop.
- Common anxiety: resume is generic, JD fit is unclear, interview answers are unstructured, and AI advice may invent experience.

Primary demo package:

- One sample resume.
- One sample Java backend / AI application JD.
- One interview rubric.
- One complete traceable loop.

Other job categories remain extension scenarios and will not receive specialized v1 guarantees.

### 4. MVP User Journey

The PRD will describe the happy path and major branch paths.

Happy path:

1. User opens Career Home.
2. User uploads a resume.
3. System extracts resume text and creates a structured profile.
4. User reviews and edits parsed resume fields.
5. User creates a target JD.
6. System parses the JD and creates requirement dimensions.
7. User runs resume-JD alignment.
8. System returns score, evidence, gaps, risks, and suggested actions.
9. User generates optimization suggestions.
10. User accepts, edits, or rejects suggestions.
11. System creates a new resume version.
12. User starts a text mock interview with the resume version and JD.
13. System generates an interview plan and first question.
14. User answers each question.
15. System scores answers and may ask follow-up questions.
16. User ends the interview.
17. System generates a report with radar dimensions, per-question review, risks, and next actions.
18. User sends report suggestions back into the next resume optimization round.

Major branch paths:

- Resume parsing fails.
- JD text is too short or unclear.
- AI output is malformed.
- User rejects all optimization suggestions.
- Interview is paused and resumed.
- Report generation fails and can be retried.

### 5. Information Architecture And Page List

User pages:

- `/career`: career home and loop overview.
- `/career/resumes`: resume upload, parse status, profile correction, and resume versions.
- `/career/alignment`: JD creation, JD parsing, and alignment report.
- `/career/optimization`: optimization tasks, suggestions, user decisions, and version generation.
- `/career/interview`: text mock interview session.
- `/career/reports/:reportId`: interview report and next-step recommendations.

Admin pages:

- `/admin/career`: dashboard summary.
- `/admin/career/tasks`: career task list and failure inspection.
- `/admin/career/rubrics`: first-version rubric configuration view.
- Existing trace pages remain the source of detailed trace inspection.

### 6. Functional Requirement Template

Each Phase 1 and Phase 2 feature will use the same format:

- User goal.
- Preconditions.
- Page entry.
- Inputs.
- Outputs.
- Main flow.
- State transitions.
- Empty states.
- Error states.
- Permission and data isolation.
- AI output contract.
- Trace requirement.
- Acceptance criteria.

This template will be applied to:

- Resume upload and parsing.
- Resume profile correction.
- Resume version management.
- JD creation and parsing.
- Resume-JD alignment report.
- Resume optimization suggestions.
- Suggestion adoption and new version generation.
- Markdown/HTML resume export.
- Text interview session creation.
- Interview plan generation.
- Answer submission, scoring, and follow-up.
- Interview pause, resume, and finish.
- Interview report generation.
- Report-to-resume feedback.
- Basic admin task visibility.

### 7. AI Output Contracts

The rewritten PRD will define structured output contracts so product, frontend, backend, and tests share the same expectations.

Resume parsing output:

- `basicInfo`: name, targetRole, location, contactSummary.
- `education`: school, degree, major, startDate, endDate, highlights.
- `workExperiences`: company, role, startDate, endDate, responsibilities, achievements.
- `projects`: name, role, techStack, background, actions, results, metrics.
- `skills`: category, name, level, evidence.
- `certifications`: name, issuer, date.
- `highlights`: text, evidenceSource.
- `missingFields`: field, reason, severity.
- `riskFlags`: field, riskType, explanation.

JD parsing output:

- `jobTitle`, `company`, `seniority`, `businessDomain`.
- `responsibilities`.
- `hardRequirements`.
- `niceToHaveRequirements`.
- `techStack`.
- `softSkills`.
- `experienceYears`.
- `keywords`.

Alignment output:

- `overallScore`.
- `dimensionScores`: dimension, score, reason.
- `requirementMatches`: requirement, resumeEvidence, matchLevel.
- `gaps`: requirement, gapType, improvementAction.
- `risks`: riskType, explanation, severity.
- `recommendedActions`.

Optimization output:

- `suggestions`: category, originalText, suggestedText, reason, riskLevel, evidenceRef.
- `userAction`: accept, edit, reject.
- `versionImpact`: section, changeSummary.

Interview plan output:

- `rounds`: turnNo, questionType, dimension, questionGoal, evidenceRef.
- `coverage`: technicalDepth, projectOwnership, communication, jdFit, learningPotential.

Answer evaluation output:

- `score`.
- `dimensionScores`.
- `strengths`.
- `weaknesses`.
- `followUpQuestion`.
- `referenceAnswer`.
- `evidenceRef`.

Interview report output:

- `overallScore`.
- `radar`: dimension, score, explanation.
- `turnReviews`: turnNo, question, answerSummary, score, feedback.
- `risks`.
- `nextTrainingActions`.
- `resumeFeedbackSuggestions`.

### 8. Data Objects And State Machines

The PRD will explain the product meaning of core objects without duplicating full database DDL.

Core objects:

- CandidateProfile.
- ResumeDocument.
- ResumeVersion.
- JobDescription.
- JobAlignmentReport.
- ResumeOptimizationTask.
- ResumeOptimizationSuggestion.
- ResumeExportRecord.
- InterviewSession.
- InterviewTurn.
- InterviewReport.

Required state machines:

- Resume parsing: `PENDING -> RUNNING -> SUCCESS | FAILED`.
- Optimization task: `PENDING -> RUNNING -> SUCCESS | FAILED`.
- Suggestion: `PENDING -> ACCEPTED | EDITED | REJECTED`.
- Interview session: `CREATED -> IN_PROGRESS -> PAUSED -> IN_PROGRESS -> COMPLETED`.
- Interview turn: `ASKED -> ANSWERED -> EVALUATED`.
- Report generation: `PENDING -> RUNNING -> SUCCESS | FAILED`.

### 9. API Requirement Summary

The PRD will define API groups at product level and leave method signatures to the implementation plan.

API groups:

- Resume APIs: upload, parse status, detail, update parsed fields, list versions, create version, export.
- JD APIs: create, parse, detail, list, align with resume version.
- Optimization APIs: create task, get task, list suggestions, apply suggestion, create optimized version.
- Interview APIs: create session, get plan, get current question, submit answer, pause, resume, finish, generate report.
- Admin APIs: list career tasks, task detail, dashboard summary, rubric view.

### 10. Competitive Analysis Rewrite

The rewritten PRD will use two capability matrices.

General RAG/Agent platform matrix:

- Dify.
- FastGPT.
- MaxKB.
- RAGFlow.
- Ragent Career.

Dimensions:

- Knowledge base management.
- Workflow and agent orchestration.
- Trace observability.
- Vertical business loop.
- Structured AI outputs.
- Career scenario depth.

Career and interview tool matrix:

- Teal.
- Yoodli.
- HireVue.
- Ragent Career.

Dimensions:

- Resume optimization.
- JD tailoring.
- Mock interview.
- Voice feedback.
- Rubric scoring.
- Resume/JD/interview closed loop.
- Engineering observability.

Product trade-offs:

- Ragent Career does not compete as a generic low-code AI builder.
- Ragent Career does not compete as an employer ATS.
- Ragent Career prioritizes traceable career-growth workflow over broad recruitment automation.

### 11. Acceptance And Testing Matrix

The PRD will include acceptance cases in Given/When/Then form.

Core acceptance cases:

- Given a valid sample resume, when the user uploads it, then the system creates a parsed profile and at least one resume version.
- Given a parsed resume and JD, when the user runs alignment, then the report includes score, evidence, gaps, risks, and trace reference.
- Given optimization suggestions, when the user accepts selected suggestions, then the system creates a new resume version and preserves the original version.
- Given a resume version and JD, when the user starts a mock interview, then the system generates a plan and first question tied to resume/JD evidence.
- Given an answered interview turn, when the system evaluates it, then the result includes score, feedback, weakness, and optional follow-up.
- Given a completed interview, when the user generates a report, then the report includes radar dimensions, turn reviews, next actions, and resume feedback suggestions.
- Given a user tries to access another user's career data, when the request is made, then the system denies access.
- Given an AI output is malformed, when parsing fails, then the task is marked failed and the user sees a retryable error.

### 12. Delivery Plan

Phase 1: Resume and JD alignment MVP.

- Resume upload and parsing.
- Resume profile correction.
- JD creation and parsing.
- Alignment report.
- Optimization suggestions.
- Resume version generation.
- Markdown and HTML export.

Phase 2: Text mock interview loop.

- Interview session.
- Interview plan.
- Question and answer flow.
- Scoring and follow-up.
- Pause and resume.
- Interview report.
- Report feedback into resume optimization.

Phase 3: Engineering and admin enhancement.

- Career dashboard.
- Rubric management.
- Prompt version review.
- Better retry and fallback strategies.
- Task analytics.

Phase 4: Voice and multimodal extension.

- ASR answer capture.
- TTS interviewer.
- WebSocket real-time state.
- Optional demeanor analysis.

## Rewrite Rules

- Keep the document in Chinese because the current PRD and project notes are Chinese.
- Use direct product language instead of marketing language.
- Preserve useful context from the current PRD, but compress repeated background.
- Make every Phase 1 and Phase 2 requirement testable.
- Do not introduce a second authentication system, model SDK system, vector database decision, or trace system.
- Do not claim voice or video features are MVP.
- Do not over-specify database DDL inside the PRD; keep DDL in the implementation plan.

## Files Affected By The Next Step

The next step after approval is to modify:

- `docs/career-agent-platform/prd-career-agent-platform.md`

Reference artifacts:

- `docs/prd-career-agent-platform-rewrite.canvas`
- `docs/career-agent-platform/implementation-plan.md`

## Self-Review

- Placeholder scan: no open placeholder markers are intentionally left in this design.
- Scope check: Phase 1 and Phase 2 are detailed; Phase 3 and Phase 4 are roadmap only.
- Consistency check: all confirmed decisions are reflected in the rewritten PRD structure.
- Ambiguity check: the default MVP scenario is Java backend / AI application job seeking, with other roles treated as extensions.

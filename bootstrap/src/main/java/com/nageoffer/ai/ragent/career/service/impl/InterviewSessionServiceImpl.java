/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.career.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewAnswerRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewCreateRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewSessionVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewTurnVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.enums.InterviewTurnType;
import com.nageoffer.ai.ragent.career.service.InterviewSessionService;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionRecoveryService;
import com.nageoffer.ai.ragent.career.service.prompt.CareerPromptTemplates;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancement;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancementService;
import com.nageoffer.ai.ragent.career.service.runtime.InterviewTurnRuntimeService;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final CareerJsonParser careerJsonParser;
    private final CareerSingleFlightLlmService singleFlightLlmService;
    private final InterviewTurnRuntimeService interviewTurnRuntimeService;
    private final InterviewSessionRecoveryService interviewSessionRecoveryService;
    private final CareerRetrievalEnhancementService careerRetrievalEnhancementService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerInterviewSessionVO createSession(CareerInterviewCreateRequest request) {
        String userId = requireUserId();
        if (request == null) {
            throw new ClientException("Interview create request is required");
        }
        ResumeVersionDO resumeVersion = requireResumeVersion(request.getResumeVersionId(), userId);
        JobDescriptionDO job = requireJob(request.getJdId(), userId);
        Map<String, Object> plan = generatePlan(resumeVersion, job);
        List<Map<String, Object>> questions = readQuestions(plan);
        Map<String, Object> firstQuestion = questions.get(0);

        InterviewSessionDO session = InterviewSessionDO.builder()
                .userId(userId)
                .resumeVersionId(resumeVersion.getId())
                .jdId(job.getId())
                .status(InterviewSessionStatus.CREATED.name())
                .planJson(writeJson(plan, "Failed to serialize interview plan JSON"))
                .currentTurnNo(1)
                .traceId(null)
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        sessionMapper.insert(session);

        InterviewTurnDO turn = InterviewTurnDO.builder()
                .sessionId(session.getId())
                .userId(userId)
                .turnNo(1)
                .turnType(normalizeTurnType(firstQuestion.get("type")).name())
                .question(requireQuestion(firstQuestion))
                .build();
        interviewTurnRuntimeService.initializeAskedTurn(turn);
        turnMapper.insert(turn);
        interviewSessionRecoveryService.snapshotStableState(session, userId, null);
        return toSessionVO(session, plan, turn);
    }

    @Override
    public CareerInterviewSessionVO querySession(String sessionId) {
        String userId = requireUserId();
        InterviewSessionDO session = requireSession(sessionId, userId);
        ensureLinkedObjectsVisible(session, userId);
        InterviewTurnDO turn = currentAskedTurn(session, userId);
        return toSessionVO(session, readPlan(session.getPlanJson()), turn);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerInterviewTurnVO nextQuestion(String sessionId) {
        String userId = requireUserId();
        InterviewSessionDO session = requireSession(sessionId, userId);
        ensureLinkedObjectsVisible(session, userId);
        if (parseSessionStatus(session.getStatus()).terminal()) {
            throw new ClientException("Interview session is already completed");
        }
        if (InterviewSessionStatus.CREATED.name().equals(session.getStatus())
                || InterviewSessionStatus.PAUSED.name().equals(session.getStatus())) {
            session.setStatus(InterviewSessionStatus.RUNNING.name());
            session.setUpdatedBy(userId);
            sessionMapper.updateById(session);
        }
        InterviewTurnDO turn = currentAskedTurn(session, userId);
        if (turn == null) {
            throw new ClientException("Current interview question does not exist");
        }
        return toTurnVO(turn);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerInterviewTurnVO submitAnswer(String sessionId, CareerInterviewAnswerRequest request) {
        String userId = requireUserId();
        if (request == null) {
            throw new ClientException("Interview answer request is required");
        }
        String answer = trimToNull(request.getAnswer());
        if (answer == null) {
            throw new ClientException("Interview answer is required");
        }
        InterviewSessionDO session = requireSession(sessionId, userId);
        ensureLinkedObjectsVisible(session, userId);
        InterviewSessionStatus status = parseSessionStatus(session.getStatus());
        if (status.terminal()) {
            throw new ClientException("Interview session is already completed");
        }
        if (status == InterviewSessionStatus.PAUSED || status == InterviewSessionStatus.CREATED) {
            session.setStatus(InterviewSessionStatus.RUNNING.name());
        }

        InterviewTurnDO currentTurn = request.getTurnNo() == null
                ? currentAskedTurn(session, userId)
                : requireTurn(session.getId(), userId, request.getTurnNo());
        if (currentTurn == null) {
            throw new ClientException("Current interview question does not exist");
        }

        String stepIdempotencyKey = interviewTurnRuntimeService.buildStepIdempotencyKey(
                session.getId(), currentTurn.getTurnNo(), request.getAnswerRevision(), answer);
        if (request.getTurnNo() != null && !request.getTurnNo().equals(session.getCurrentTurnNo())
                && !interviewTurnRuntimeService.isSameStep(currentTurn, stepIdempotencyKey)) {
            throw new ClientException("Interview turn is not current");
        }

        if (interviewTurnRuntimeService.isSameStep(currentTurn, stepIdempotencyKey)
                && !"ASKED".equals(currentTurn.getStatus())) {
            return toTurnVO(currentTurn);
        }

        interviewTurnRuntimeService.markAnswerSaved(currentTurn, answer, stepIdempotencyKey);
        turnMapper.updateById(currentTurn);

        session.setStatus(InterviewSessionStatus.RUNNING.name());
        session.setUpdatedBy(userId);
        sessionMapper.updateById(session);

        EvaluationResult evaluation;
        try {
            interviewTurnRuntimeService.markEvaluating(currentTurn);
            turnMapper.updateById(currentTurn);
            evaluation = evaluateAnswer(session, currentTurn, answer, userId);
        } catch (RuntimeException ex) {
            interviewTurnRuntimeService.markEvaluationFailed(currentTurn, ex);
            turnMapper.updateById(currentTurn);
            interviewSessionRecoveryService.snapshotStableState(session, userId, stepIdempotencyKey);
            return toTurnVO(currentTurn);
        }

        currentTurn.setScore(evaluation.score());
        currentTurn.setFeedbackJson(writeJson(evaluation.feedback(), "Failed to serialize interview feedback JSON"));
        interviewTurnRuntimeService.markEvaluated(currentTurn);
        turnMapper.updateById(currentTurn);

        interviewTurnRuntimeService.markFollowUpDeciding(currentTurn);
        if (evaluation.followUpRequired()) {
            InterviewTurnDO followUp = createFollowUpTurn(session, currentTurn, evaluation.followUpQuestion(), userId);
            session.setCurrentTurnNo(followUp.getTurnNo());
            interviewTurnRuntimeService.markFollowUpCreated(currentTurn);
        } else {
            int nextPlanIndex = nextPlannedQuestionIndex(session.getId());
            List<Map<String, Object>> questions = readQuestions(readPlan(session.getPlanJson()));
            if (nextPlanIndex <= questions.size()) {
                InterviewTurnDO next = createPlannedTurn(session, questions.get(nextPlanIndex - 1),
                        nextTurnNo(session.getId()), userId);
                session.setCurrentTurnNo(next.getTurnNo());
                interviewTurnRuntimeService.markNextMainCreated(currentTurn);
            } else {
                session.setStatus(InterviewSessionStatus.COMPLETED.name());
                interviewTurnRuntimeService.markSessionCompleted(currentTurn);
            }
        }
        turnMapper.updateById(currentTurn);
        session.setUpdatedBy(userId);
        sessionMapper.updateById(session);
        interviewSessionRecoveryService.snapshotStableState(session, userId, stepIdempotencyKey);
        return toTurnVO(currentTurn, evaluation.feedback());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void pause(String sessionId) {
        String userId = requireUserId();
        InterviewSessionDO session = requireSession(sessionId, userId);
        if (parseSessionStatus(session.getStatus()).terminal()) {
            throw new ClientException("Interview session is already completed");
        }
        session.setStatus(InterviewSessionStatus.PAUSED.name());
        session.setUpdatedBy(userId);
        sessionMapper.updateById(session);
        InterviewTurnDO turn = currentAskedTurn(session, userId);
        interviewSessionRecoveryService.snapshotStableState(session, userId,
                turn == null ? null : turn.getStepIdempotencyKey());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void finish(String sessionId) {
        String userId = requireUserId();
        InterviewSessionDO session = requireSession(sessionId, userId);
        if (InterviewSessionStatus.CANCELLED.name().equals(session.getStatus())) {
            throw new ClientException("Interview session is already cancelled");
        }
        session.setStatus(InterviewSessionStatus.COMPLETED.name());
        session.setUpdatedBy(userId);
        sessionMapper.updateById(session);
        InterviewTurnDO turn = currentAskedTurn(session, userId);
        interviewSessionRecoveryService.snapshotStableState(session, userId,
                turn == null ? null : turn.getStepIdempotencyKey());
    }

    private InterviewTurnDO createPlannedTurn(InterviewSessionDO session,
                                              Map<String, Object> question,
                                              int turnNo,
                                              String userId) {
        InterviewTurnDO turn = InterviewTurnDO.builder()
                .sessionId(session.getId())
                .userId(userId)
                .turnNo(turnNo)
                .turnType(normalizeTurnType(question.get("type")).name())
                .question(requireQuestion(question))
                .build();
        interviewTurnRuntimeService.initializeAskedTurn(turn);
        turnMapper.insert(turn);
        return turn;
    }

    private InterviewTurnDO createFollowUpTurn(InterviewSessionDO session,
                                               InterviewTurnDO currentTurn,
                                               String followUpQuestion,
                                               String userId) {
        String question = trimToNull(followUpQuestion);
        if (question == null) {
            throw new ClientException("Follow-up question is required");
        }
        InterviewTurnDO followUp = InterviewTurnDO.builder()
                .sessionId(session.getId())
                .userId(userId)
                .turnNo(nextTurnNo(session.getId()))
                .turnType(InterviewTurnType.FOLLOW_UP.name())
                .question(question)
                .build();
        interviewTurnRuntimeService.initializeAskedTurn(followUp);
        turnMapper.insert(followUp);
        return followUp;
    }

    private EvaluationResult evaluateAnswer(InterviewSessionDO session,
                                            InterviewTurnDO turn,
                                            String answer,
                                            String userId) {
        ResumeVersionDO resumeVersion = requireResumeVersion(session.getResumeVersionId(), userId);
        JobDescriptionDO job = requireJob(session.getJdId(), userId);
        CareerRetrievalEnhancement retrievalEnhancement =
                careerRetrievalEnhancementService.enhanceInterview(resumeVersion, job, turn.getQuestion());
        String prompt = appendRetrievalEvidence(String.format(CareerPromptTemplates.INTERVIEW_EVALUATE,
                turn.getQuestion(),
                answer,
                defaultJson(resumeVersion.getContentJson()),
                defaultJson(job.getParsedJson())), retrievalEnhancement);
        String response = singleFlightLlmService.chat("INTERVIEW_EVALUATE",
                buildSingleFlightKey("INTERVIEW_EVALUATE", userId, session.getId(), turn.getTurnNo(), prompt),
                session.getTraceId(),
                ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.1D)
                .thinking(false)
                .build());
        Map<String, Object> parsed = careerJsonParser.parseObject(response);
        Integer score = readScore(parsed.get("score"));
        Map<String, Object> feedback = buildFeedback(parsed);
        boolean followUpRequired = readBoolean(parsed.get("followUpRequired"));
        String followUpQuestion = extractString(parsed.get("followUpQuestion"));
        return new EvaluationResult(score, feedback, followUpRequired, followUpQuestion);
    }

    private Map<String, Object> buildFeedback(Map<String, Object> parsed) {
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("summary", extractString(parsed.get("feedback")));
        feedback.put("strengths", toList(parsed.get("strengths")));
        feedback.put("weaknesses", toList(parsed.get("weaknesses")));
        return feedback;
    }

    private Map<String, Object> generatePlan(ResumeVersionDO resumeVersion, JobDescriptionDO job) {
        CareerRetrievalEnhancement retrievalEnhancement =
                careerRetrievalEnhancementService.enhanceInterview(resumeVersion, job, "interview plan");
        String prompt = appendRetrievalEvidence(String.format(CareerPromptTemplates.INTERVIEW_PLAN,
                defaultJson(resumeVersion.getContentJson()),
                defaultJson(job.getParsedJson())), retrievalEnhancement);
        String response = singleFlightLlmService.chat("INTERVIEW_PLAN",
                buildSingleFlightKey("INTERVIEW_PLAN", resumeVersion.getUserId(), resumeVersion.getId(), 0, prompt),
                null,
                ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.1D)
                .thinking(false)
                .build());
        Map<String, Object> plan = careerJsonParser.parseObject(response);
        if (readQuestions(plan).isEmpty()) {
            throw new ClientException("Interview plan must contain at least one question");
        }
        return plan;
    }

    private List<Map<String, Object>> readQuestions(Map<String, Object> plan) {
        Object questions = plan == null ? null : plan.get("questions");
        if (questions == null) {
            return List.of();
        }
        if (questions instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) map;
                    result.add(cast);
                }
            }
            return result;
        }
        return List.of();
    }

    private InterviewTurnDO currentAskedTurn(InterviewSessionDO session, String userId) {
        Integer turnNo = session.getCurrentTurnNo();
        if (turnNo == null || turnNo < 1) {
            turnNo = 1;
        }
        return requireTurn(session.getId(), userId, turnNo);
    }

    private InterviewTurnDO requireTurn(String sessionId, String userId, Integer turnNo) {
        if (turnNo == null || turnNo < 1) {
            throw new ClientException("Interview turn no is invalid");
        }
        InterviewTurnDO turn = turnMapper.selectOne(Wrappers.lambdaQuery(InterviewTurnDO.class)
                .eq(InterviewTurnDO::getSessionId, sessionId)
                .eq(InterviewTurnDO::getUserId, userId)
                .eq(InterviewTurnDO::getTurnNo, turnNo)
                .eq(InterviewTurnDO::getDeleted, 0));
        if (turn == null) {
            return null;
        }
        if (!"ASKED".equals(turn.getStatus()) && !"ANSWERED".equals(turn.getStatus())
                && !"EVALUATED".equals(turn.getStatus()) && !"WAITING_RETRY".equals(turn.getStatus())) {
            return null;
        }
        return turn;
    }

    private int nextPlannedQuestionIndex(String sessionId) {
        List<InterviewTurnDO> turns = turnMapper.selectList(Wrappers.lambdaQuery(InterviewTurnDO.class)
                .eq(InterviewTurnDO::getSessionId, sessionId)
                .eq(InterviewTurnDO::getDeleted, 0)
                .orderByAsc(InterviewTurnDO::getTurnNo));
        if (turns == null) {
            return 1;
        }
        return (int) turns.stream()
                .filter(turn -> !InterviewTurnType.FOLLOW_UP.name().equals(turn.getTurnType()))
                .count() + 1;
    }

    private int nextTurnNo(String sessionId) {
        List<InterviewTurnDO> turns = turnMapper.selectList(Wrappers.lambdaQuery(InterviewTurnDO.class)
                .eq(InterviewTurnDO::getSessionId, sessionId)
                .eq(InterviewTurnDO::getDeleted, 0)
                .orderByDesc(InterviewTurnDO::getTurnNo));
        if (turns == null || turns.isEmpty()) {
            return 1;
        }
        return turns.stream()
                .map(InterviewTurnDO::getTurnNo)
                .filter(turnNo -> turnNo != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private InterviewSessionDO requireSession(String sessionId, String userId) {
        if (StrUtil.isBlank(sessionId)) {
            throw new ClientException("Interview session id is required");
        }
        InterviewSessionDO session = sessionMapper.selectOne(Wrappers.lambdaQuery(InterviewSessionDO.class)
                .eq(InterviewSessionDO::getId, sessionId)
                .eq(InterviewSessionDO::getUserId, userId)
                .eq(InterviewSessionDO::getDeleted, 0));
        if (session == null) {
            throw new ClientException("Interview session does not exist");
        }
        return session;
    }

    private ResumeVersionDO requireResumeVersion(String resumeVersionId, String userId) {
        if (StrUtil.isBlank(resumeVersionId)) {
            throw new ClientException("Resume version id is required");
        }
        ResumeVersionDO resumeVersion = resumeVersionMapper.selectOne(Wrappers.lambdaQuery(ResumeVersionDO.class)
                .eq(ResumeVersionDO::getId, resumeVersionId)
                .eq(ResumeVersionDO::getUserId, userId)
                .eq(ResumeVersionDO::getDeleted, 0));
        if (resumeVersion == null) {
            throw new ClientException("Resume version does not exist");
        }
        return resumeVersion;
    }

    private JobDescriptionDO requireJob(String jdId, String userId) {
        if (StrUtil.isBlank(jdId)) {
            throw new ClientException("Job description id is required");
        }
        JobDescriptionDO job = jobDescriptionMapper.selectOne(Wrappers.lambdaQuery(JobDescriptionDO.class)
                .eq(JobDescriptionDO::getId, jdId)
                .eq(JobDescriptionDO::getUserId, userId)
                .eq(JobDescriptionDO::getDeleted, 0));
        if (job == null) {
            throw new ClientException("Job description does not exist");
        }
        return job;
    }

    private void ensureLinkedObjectsVisible(InterviewSessionDO session, String userId) {
        requireResumeVersion(session.getResumeVersionId(), userId);
        requireJob(session.getJdId(), userId);
    }

    private CareerInterviewSessionVO toSessionVO(InterviewSessionDO session,
                                                 Map<String, Object> plan,
                                                 InterviewTurnDO currentQuestion) {
        return CareerInterviewSessionVO.builder()
                .id(session.getId())
                .status(session.getStatus())
                .plan(plan)
                .currentTurnNo(session.getCurrentTurnNo())
                .currentQuestion(currentQuestion == null ? null : toTurnVO(currentQuestion))
                .build();
    }

    private CareerInterviewTurnVO toTurnVO(InterviewTurnDO turn) {
        return toTurnVO(turn, readFeedback(turn.getFeedbackJson()));
    }

    private CareerInterviewTurnVO toTurnVO(InterviewTurnDO turn, Map<String, Object> feedback) {
        return CareerInterviewTurnVO.builder()
                .id(turn.getId())
                .sessionId(turn.getSessionId())
                .turnNo(turn.getTurnNo())
                .turnType(turn.getTurnType())
                .question(turn.getQuestion())
                .answer(turn.getAnswer())
                .score(turn.getScore())
                .feedback(feedback)
                .status(turn.getStatus())
                .stepIdempotencyKey(turn.getStepIdempotencyKey())
                .answerStatus(turn.getAnswerStatus())
                .evaluationStatus(turn.getEvaluationStatus())
                .followUpDecisionStatus(turn.getFollowUpDecisionStatus())
                .compensationStatus(turn.getCompensationStatus())
                .attemptCount(turn.getAttemptCount())
                .lastError(turn.getLastError())
                .build();
    }

    private Map<String, Object> readPlan(String value) {
        if (StrUtil.isBlank(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new ServiceException("Failed to parse interview plan JSON");
        }
    }

    private Map<String, Object> readFeedback(String value) {
        if (StrUtil.isBlank(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new ServiceException("Failed to parse interview feedback JSON");
        }
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    private String defaultJson(String value) {
        String text = trimToNull(value);
        return text == null ? "{}" : text;
    }

    private String buildSingleFlightKey(String scene,
                                        String userId,
                                        String artifactId,
                                        Integer turnNo,
                                        String prompt) {
        return String.join(":",
                StrUtil.blankToDefault(scene, "CAREER_AI"),
                StrUtil.blankToDefault(userId, "anonymous"),
                StrUtil.blankToDefault(artifactId, "artifact"),
                String.valueOf(turnNo == null ? 0 : turnNo),
                StrUtil.blankToDefault(prompt, ""));
    }

    private String appendRetrievalEvidence(String prompt, CareerRetrievalEnhancement enhancement) {
        if (enhancement == null) {
            return prompt;
        }
        return prompt + "\n\nCareer retrieval evidence JSON:\n"
                + writeJson(enhancement.toPromptPayload(), "Failed to serialize retrieval evidence JSON")
                + "\nRules: HYDE_QUERY evidence is QUERY_ONLY. Use it to deepen questions, not as a candidate fact.";
    }

    private InterviewSessionStatus parseSessionStatus(String value) {
        if (StrUtil.isBlank(value)) {
            return InterviewSessionStatus.CREATED;
        }
        try {
            return InterviewSessionStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ClientException("Interview session status is invalid");
        }
    }

    private InterviewTurnType normalizeTurnType(Object value) {
        String type = extractString(value);
        if (type == null) {
            throw new ClientException("Interview question type is required");
        }
        try {
            return InterviewTurnType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ClientException("Interview question type is invalid");
        }
    }

    private String requireQuestion(Map<String, Object> question) {
        String value = extractString(question.get("question"));
        if (value == null) {
            throw new ClientException("Interview question text is required");
        }
        return value;
    }

    private Integer readScore(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            try {
                return Double.valueOf(text.trim()).intValue();
            } catch (NumberFormatException ex) {
                throw new ClientException("Interview score is invalid");
            }
        }
        throw new ClientException("Interview score is required");
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            return Boolean.parseBoolean(text.trim());
        }
        return false;
    }

    private List<Object> toList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String extractString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User information is missing");
        }
        return userId;
    }

    private record EvaluationResult(Integer score,
                                    Map<String, Object> feedback,
                                    boolean followUpRequired,
                                    String followUpQuestion) {
    }
}

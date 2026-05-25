package com.nageoffer.ai.ragent.rag.core.memory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationSummaryDO;
import com.nageoffer.ai.ragent.rag.service.ConversationGroupService;
import com.nageoffer.ai.ragent.rag.service.ConversationMessageService;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationSummaryBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CONTEXT_FORMAT_PATH;
import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CONVERSATION_SUMMARY_PROMPT_PATH;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdbcConversationMemorySummaryService implements ConversationMemorySummaryService {

    private static final String SUMMARY_LOCK_PREFIX = "ragent:memory:summary:lock:";

    private final ConversationGroupService conversationGroupService;
    private final ConversationMessageService conversationMessageService;
    private final MemoryProperties memoryProperties;
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final RedissonClient redissonClient;
    private final Executor memorySummaryExecutor;
    private final ConversationMemoryCompressionPlanner compressionPlanner;
    private final ConversationMemoryTriggerPolicy triggerPolicy;

    @Override
    public void compressIfNeeded(String conversationId, String userId, ChatMessage message) {
        if (!memoryProperties.getSummaryEnabled()) {
            return;
        }
        if (message.getRole() != ChatMessage.Role.ASSISTANT) {
            return;
        }
        CompletableFuture.runAsync(() -> doCompressIfNeeded(conversationId, userId, ConversationMemoryTriggerContext.assistantAppend()), memorySummaryExecutor)
                .exceptionally(ex -> {
                    log.error("对话记忆摘要异步任务失败 - conversationId: {}, userId: {}",
                            conversationId, userId, ex);
                    return null;
                });
    }

    @Override
    public void compressOnStageSwitch(String conversationId, String userId) {
        if (Boolean.TRUE.equals(memoryProperties.getSummaryEnabled())) {
            runCompressionAsync(conversationId, userId, ConversationMemoryTriggerContext.stageSwitch());
        }
    }

    @Override
    public void compressOnRecoveryEvent(String conversationId, String userId) {
        if (Boolean.TRUE.equals(memoryProperties.getSummaryEnabled())) {
            runCompressionAsync(conversationId, userId, ConversationMemoryTriggerContext.recoveryEvent());
        }
    }

    private void runCompressionAsync(String conversationId,
                                     String userId,
                                     ConversationMemoryTriggerContext triggerContext) {
        CompletableFuture.runAsync(() -> doCompressIfNeeded(conversationId, userId, triggerContext), memorySummaryExecutor)
                .exceptionally(ex -> {
                    log.error("瀵硅瘽璁板繂鎽樿寮傛浠诲姟澶辫触 - conversationId: {}, userId: {}",
                            conversationId, userId, ex);
                    return null;
                });
    }

    @Override
    public ChatMessage loadLatestSummary(String conversationId, String userId) {
        ConversationSummaryDO summary = conversationGroupService.findLatestSummary(conversationId, userId);
        return toChatMessage(summary);
    }

    @Override
    public ChatMessage decorateIfNeeded(ChatMessage summary) {
        if (summary == null || StrUtil.isBlank(summary.getContent())) {
            return summary;
        }
        String wrapped = promptTemplateLoader.renderSection(
                CONTEXT_FORMAT_PATH, "summary-wrapper",
                Map.of("content", summary.getContent().trim())
        );
        return ChatMessage.system(wrapped);
    }

    private void doCompressIfNeeded(String conversationId,
                                    String userId,
                                    ConversationMemoryTriggerContext triggerContext) {
        long startTime = System.currentTimeMillis();
        int triggerTurns = valueOrDefault(memoryProperties.getSummaryStartTurns(), 0);
        int maxTurns = valueOrDefault(memoryProperties.getHistoryKeepTurns(), 0);
        if (maxTurns <= 0 || triggerTurns <= 0) {
            return;
        }

        String lockKey = SUMMARY_LOCK_PREFIX + buildLockKey(conversationId, userId);
        RLock lock = redissonClient.getLock(lockKey);
        if (!lock.tryLock()) {
            return;
        }
        try {
            long total = conversationGroupService.countUserMessages(conversationId, userId);

            ConversationSummaryDO latestSummary = conversationGroupService.findLatestSummary(conversationId, userId);
            List<ConversationMessageDO> latestUserTurns = conversationGroupService.listLatestUserOnlyMessages(
                    conversationId,
                    userId,
                    maxTurns
            );
            if (latestUserTurns.isEmpty()) {
                return;
            }
            String cutoffId = resolveCutoffId(latestUserTurns);
            if (StrUtil.isBlank(cutoffId)) {
                return;
            }

            String afterId = resolveSummaryStartId(conversationId, userId, latestSummary);
            if (afterId != null && Long.parseLong(afterId) >= Long.parseLong(cutoffId)) {
                return;
            }

            List<ConversationMessageDO> toSummarize = conversationGroupService.listMessagesBetweenIds(
                    conversationId,
                    userId,
                    afterId,
                    cutoffId
            );
            if (CollUtil.isEmpty(toSummarize)) {
                return;
            }
            String lastMessageId = resolveLastMessageId(toSummarize);
            if (StrUtil.isBlank(lastMessageId)) {
                return;
            }

            String existingSummary = latestSummary == null ? "" : latestSummary.getContent();
            String summary = summarizeTriggeredMessages(total, toSummarize, existingSummary, triggerContext);
            if (StrUtil.isBlank(summary)) {
                return;
            }

            createSummary(conversationId, userId, summary, lastMessageId);
            log.info("摘要成功 - conversationId：{}，userId：{}，消息数：{}，耗时：{}ms",
                    conversationId, userId, toSummarize.size(),
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("摘要失败 - conversationId：{}，userId：{}", conversationId, userId, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    String summarizeTriggeredMessages(long userTurns,
                                      List<ConversationMessageDO> messages,
                                      String existingSummary,
                                      ConversationMemoryTriggerContext triggerContext) {
        ConversationMemoryTriggerDecision triggerDecision = triggerPolicy.decide(
                userTurns,
                messages,
                memoryProperties,
                triggerContext
        );
        if (!triggerDecision.shouldCompress()) {
            return null;
        }
        return summarizeMessages(messages, existingSummary, triggerDecision);
    }

    String summarizeMessages(List<ConversationMessageDO> messages,
                             String existingSummary,
                             ConversationMemoryTriggerDecision triggerDecision) {
        ConversationMemoryCompressionPlan compressionPlan = resolveCompressionPlan(messages);
        List<ConversationMessageDO> sourceMessages = Boolean.TRUE.equals(memoryProperties.getHybridEnabled())
                ? compressionPlan.getSummarizableMessages()
                : messages;
        List<ChatMessage> histories = toHistoryMessages(sourceMessages);
        if (CollUtil.isEmpty(histories)) {
            if (Boolean.TRUE.equals(memoryProperties.getHybridEnabled())
                    && hasPersistedBuckets(compressionPlan)) {
                return buildPersistedSummary(existingSummary, compressionPlan, triggerDecision);
            }
            return existingSummary;
        }

        int summaryMaxChars = memoryProperties.getSummaryMaxChars();
        List<ChatMessage> summaryMessages = new ArrayList<>();
        String summaryPrompt = promptTemplateLoader.render(
                CONVERSATION_SUMMARY_PROMPT_PATH,
                Map.of("summary_max_chars", String.valueOf(summaryMaxChars))
        );
        summaryMessages.add(ChatMessage.system(summaryPrompt));

        if (StrUtil.isNotBlank(existingSummary)) {
            summaryMessages.add(ChatMessage.assistant(
                    "历史摘要（仅用于合并去重，不得作为事实新增来源；若与本轮对话冲突，以本轮对话为准）：\n"
                            + existingSummary.trim()
            ));
        }
        summaryMessages.addAll(histories);
        summaryMessages.add(ChatMessage.user(
                "合并以上对话与历史摘要，去重后输出更新摘要。要求：严格≤" + summaryMaxChars + "字符；仅一行。"
        ));

        ChatRequest request = ChatRequest.builder()
                .messages(summaryMessages)
                .temperature(0.3D)
                .topP(0.9D)
                .thinking(false)
                .build();
        try {
            String result = llmService.chat(request);
            log.info("对话摘要生成 - resultChars: {}", result.length());

            return buildPersistedSummary(result, compressionPlan, triggerDecision);
        } catch (Exception e) {
            log.error("对话记忆摘要生成失败, conversationId相关消息数: {}", messages.size(), e);
            return existingSummary;
        }
    }

    private ConversationMemoryCompressionPlan resolveCompressionPlan(List<ConversationMessageDO> messages) {
        if (!Boolean.TRUE.equals(memoryProperties.getHybridEnabled())) {
            return new ConversationMemoryCompressionPlan(
                    List.of(),
                    List.of(),
                    messages == null ? List.of() : messages,
                    ids(messages),
                    List.of()
            );
        }
        return compressionPlanner.plan(messages, memoryProperties, RagTraceContext.getTraceId());
    }

    String buildPersistedSummary(String generatedSummary,
                                 ConversationMemoryCompressionPlan plan,
                                 ConversationMemoryTriggerDecision triggerDecision) {
        if (!Boolean.TRUE.equals(memoryProperties.getHybridEnabled()) || plan == null) {
            return generatedSummary;
        }
        String shortSummary = StrUtil.blankToDefault(generatedSummary, "").trim();
        return """
                [memory-compression strategy=hybrid triggers=%s traceId=%s estimatedTokens=%d userTurns=%d sourceIds=%s sourceTurns=%s protectedIds=%s protectedTurns=%s hotContextIds=%s]
                [hot-context]
                %s
                [long-term-facts]
                %s
                [key-evidence]
                %s
                [risk-flags]
                %s
                [short-summary]
                %s
                """.formatted(
                triggerDecision == null ? List.of() : triggerDecision.getTriggers(),
                StrUtil.blankToDefault(plan.getTraceId(), ""),
                triggerDecision == null ? 0 : triggerDecision.getEstimatedTokens(),
                triggerDecision == null ? 0 : triggerDecision.getUserTurns(),
                plan.getSourceMessageIds(),
                plan.getSourceTurnRefs(),
                plan.getProtectedMessageIds(),
                plan.getProtectedTurnRefs(),
                ids(plan.getHotContextMessages()),
                formatMessagesWithIds(plan.getHotContextMessages()),
                formatMessagesWithIds(plan.getLongTermFactMessages()),
                formatMessagesWithIds(plan.getKeyEvidenceMessages()),
                formatMessagesWithIds(plan.getRiskFlagMessages()),
                shortSummary
        ).trim();
    }

    private boolean hasPersistedBuckets(ConversationMemoryCompressionPlan plan) {
        return plan != null
                && (!plan.getHotContextMessages().isEmpty()
                || !plan.getProtectedMessages().isEmpty());
    }

    private String formatMessagesWithIds(List<ConversationMessageDO> messages) {
        if (CollUtil.isEmpty(messages)) {
            return "";
        }
        return messages.stream()
                .filter(item -> item != null && StrUtil.isNotBlank(item.getContent()))
                .map(item -> "- id=%s role=%s content=%s".formatted(
                        StrUtil.blankToDefault(item.getId(), ""),
                        StrUtil.blankToDefault(item.getRole(), ""),
                        item.getContent()))
                .collect(Collectors.joining("\n"));
    }

    private List<String> ids(List<ConversationMessageDO> messages) {
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }
        return messages.stream()
                .filter(item -> item != null && StrUtil.isNotBlank(item.getId()))
                .map(ConversationMessageDO::getId)
                .toList();
    }

    private List<ChatMessage> toHistoryMessages(List<ConversationMessageDO> messages) {
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }
        return messages.stream()
                .filter(item -> item != null
                        && StrUtil.isNotBlank(item.getContent())
                        && StrUtil.isNotBlank(item.getRole()))
                .map(item -> {
                    String role = item.getRole().toLowerCase();
                    if ("user".equals(role)) {
                        return ChatMessage.user(item.getContent());
                    } else if ("assistant".equals(role)) {
                        return ChatMessage.assistant(item.getContent());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ChatMessage toChatMessage(ConversationSummaryDO record) {
        if (record == null || StrUtil.isBlank(record.getContent())) {
            return null;
        }
        return new ChatMessage(ChatMessage.Role.SYSTEM, record.getContent());
    }

    private String resolveSummaryStartId(String conversationId, String userId, ConversationSummaryDO summary) {
        if (summary == null) {
            return null;
        }
        if (summary.getLastMessageId() != null) {
            return summary.getLastMessageId();
        }

        Date after = summary.getUpdateTime();
        if (after == null) {
            after = summary.getCreateTime();
        }
        return conversationGroupService.findMaxMessageIdAtOrBefore(conversationId, userId, after);
    }

    private String resolveCutoffId(List<ConversationMessageDO> latestUserTurns) {
        if (CollUtil.isEmpty(latestUserTurns)) {
            return null;
        }

        // 倒序列表的最后一个就是最早的
        ConversationMessageDO oldest = latestUserTurns.get(latestUserTurns.size() - 1);
        return oldest == null ? null : oldest.getId();
    }

    private String resolveLastMessageId(List<ConversationMessageDO> toSummarize) {
        for (int i = toSummarize.size() - 1; i >= 0; i--) {
            ConversationMessageDO item = toSummarize.get(i);
            if (item != null && item.getId() != null) {
                return item.getId();
            }
        }
        return null;
    }

    private void createSummary(String conversationId,
                               String userId,
                               String content,
                               String lastMessageId) {
        ConversationSummaryBO summaryRecord = ConversationSummaryBO.builder()
                .conversationId(conversationId)
                .userId(userId)
                .content(content)
                .lastMessageId(lastMessageId)
                .build();
        conversationMessageService.addMessageSummary(summaryRecord);
    }

    private String buildLockKey(String conversationId, String userId) {
        return userId.trim() + ":" + conversationId.trim();
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}

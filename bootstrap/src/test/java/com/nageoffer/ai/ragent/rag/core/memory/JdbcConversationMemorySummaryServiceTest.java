package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConversationMemorySummaryServiceTest {

    @Test
    void persistedHybridSummaryKeepsProtectedSectionsOutsideGeneratedShortSummary() {
        MemoryProperties properties = new MemoryProperties();
        JdbcConversationMemorySummaryService service = new JdbcConversationMemorySummaryService(
                null,
                null,
                properties,
                null,
                null,
                null,
                Runnable::run,
                new ConversationMemoryCompressionPlanner(new ConversationMemoryImportanceScorer()),
                new ConversationMemoryTriggerPolicy()
        );
        ConversationMemoryCompressionPlan plan = new ConversationMemoryCompressionPlan(
                "trace-1",
                List.of(message("9", "assistant", "hot context")),
                List.of(message("1", "user", "ordinary source")),
                List.of(message("2", "user", "fact: user wants remote work")),
                List.of(message("3", "assistant", "evidence: score 82 from rubric")),
                List.of(message("4", "assistant", "risk: unsupported claim")),
                List.of("1"),
                List.of("2", "3", "4"),
                List.of("turn#1:1"),
                List.of("turn#2:2", "turn#3:3", "turn#4:4")
        );
        ConversationMemoryTriggerDecision decision = new ConversationMemoryTriggerDecision(
                List.of(ConversationMemoryTrigger.TURN_COUNT),
                5,
                128
        );

        String persisted = service.buildPersistedSummary("short summary", plan, decision);

        assertThat(persisted).contains("strategy=hybrid");
        assertThat(persisted).contains("traceId=trace-1");
        assertThat(persisted).contains("sourceIds=[1]");
        assertThat(persisted).contains("protectedIds=[2, 3, 4]");
        assertThat(persisted).contains("[hot-context]");
        assertThat(persisted).contains("hot context");
        assertThat(persisted).contains("[long-term-facts]");
        assertThat(persisted).contains("fact: user wants remote work");
        assertThat(persisted).contains("[key-evidence]");
        assertThat(persisted).contains("evidence: score 82 from rubric");
        assertThat(persisted).contains("[risk-flags]");
        assertThat(persisted).contains("risk: unsupported claim");
        assertThat(persisted).contains("[short-summary]\nshort summary");
    }

    @Test
    void summarizeMessagesSendsOnlyShortSummarySourcesToLlmAndPersistsProtectedAndHotBuckets() {
        MemoryProperties properties = new MemoryProperties();
        properties.setHybridEnabled(true);
        properties.setSummaryMaxChars(200);
        properties.setRecentKeepMessages(2);
        properties.setImportantMessageThreshold(70);
        properties.setSummaryMaxSourceMessages(10);
        CapturingLlmService llmService = new CapturingLlmService("generated short summary");
        JdbcConversationMemorySummaryService service = new JdbcConversationMemorySummaryService(
                null,
                null,
                properties,
                llmService,
                new StubPromptTemplateLoader(),
                null,
                Runnable::run,
                new ConversationMemoryCompressionPlanner(new ConversationMemoryImportanceScorer()),
                new ConversationMemoryTriggerPolicy()
        );
        String persisted = service.summarizeTriggeredMessages(7, List.of(
                message("1", "user", "ordinary source one"),
                message("2", "assistant", "fact: candidate prefers remote work"),
                message("3", "assistant", "evidence: score 82 from interview rubric"),
                message("4", "user", "risk: unsupported claim needs review"),
                message("5", "assistant", "ordinary source two"),
                message("6", "assistant", "hot context answer"),
                message("7", "user", "hot context question")
        ), "existing summary", ConversationMemoryTriggerContext.stageSwitch());

        assertThat(llmService.capturedRequest).isNotNull();
        String llmSource = contents(llmService.capturedRequest.getMessages());
        assertThat(llmSource).contains("ordinary source one", "ordinary source two");
        assertThat(llmSource).doesNotContain(
                "fact: candidate prefers remote work",
                "evidence: score 82 from interview rubric",
                "risk: unsupported claim needs review",
                "hot context answer",
                "hot context question"
        );
        assertThat(persisted).contains("triggers=[STAGE_SWITCH]");
        assertThat(persisted).contains("userTurns=7");
        assertThat(persisted).containsPattern("estimatedTokens=[1-9][0-9]*");
        assertThat(persisted).contains("[hot-context]");
        assertThat(persisted).contains("hot context answer", "hot context question");
        assertThat(persisted).contains("[long-term-facts]");
        assertThat(persisted).contains("fact: candidate prefers remote work");
        assertThat(persisted).contains("[key-evidence]");
        assertThat(persisted).contains("evidence: score 82 from interview rubric");
        assertThat(persisted).contains("[risk-flags]");
        assertThat(persisted).contains("risk: unsupported claim needs review");
        assertThat(persisted).contains("[short-summary]\ngenerated short summary");
    }

    private String contents(List<ChatMessage> messages) {
        return messages.stream()
                .map(ChatMessage::getContent)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private ConversationMessageDO message(String id, String role, String content) {
        return ConversationMessageDO.builder()
                .id(id)
                .role(role)
                .content(content)
                .build();
    }

    private static final class CapturingLlmService implements LLMService {

        private final String response;
        private ChatRequest capturedRequest;

        private CapturingLlmService(String response) {
            this.response = response;
        }

        @Override
        public String chat(ChatRequest request) {
            this.capturedRequest = request;
            return response;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            return () -> {
            };
        }
    }

    private static final class StubPromptTemplateLoader extends PromptTemplateLoader {

        private StubPromptTemplateLoader() {
            super(new DefaultResourceLoader());
        }

        @Override
        public String render(String path, Map<String, String> slots) {
            return "memory summary prompt max=" + slots.get("summary_max_chars");
        }
    }
}

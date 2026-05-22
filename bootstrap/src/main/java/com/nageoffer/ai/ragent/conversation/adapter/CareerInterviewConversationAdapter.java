package com.nageoffer.ai.ragent.conversation.adapter;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.conversation.port.ConversationAppendCommand;
import com.nageoffer.ai.ragent.conversation.port.ConversationMessageView;
import com.nageoffer.ai.ragent.conversation.port.ConversationPort;
import com.nageoffer.ai.ragent.conversation.port.ConversationScene;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CareerInterviewConversationAdapter implements ConversationPort {

    private final InterviewTurnMapper turnMapper;

    @Override
    public ConversationScene scene() {
        return ConversationScene.CAREER_INTERVIEW;
    }

    @Override
    public List<ConversationMessageView> load(String conversationId, String userId) {
        List<InterviewTurnDO> turns = turnMapper.selectList(Wrappers.lambdaQuery(InterviewTurnDO.class)
                .eq(InterviewTurnDO::getSessionId, conversationId)
                .eq(InterviewTurnDO::getUserId, userId)
                .eq(InterviewTurnDO::getDeleted, 0)
                .orderByAsc(InterviewTurnDO::getTurnNo));
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        List<ConversationMessageView> messages = new ArrayList<>();
        for (InterviewTurnDO turn : turns) {
            if (StrUtil.isNotBlank(turn.getQuestion())) {
                messages.add(new ConversationMessageView(
                        turn.getId() + ":question",
                        turn.getSessionId(),
                        turn.getUserId(),
                        "USER",
                        turn.getQuestion(),
                        turn.getTurnNo(),
                        turn.getStepIdempotencyKey()));
            }
            if (StrUtil.isNotBlank(turn.getAnswer())) {
                messages.add(new ConversationMessageView(
                        turn.getId() + ":answer",
                        turn.getSessionId(),
                        turn.getUserId(),
                        "ASSISTANT",
                        turn.getAnswer(),
                        turn.getTurnNo(),
                        turn.getStepIdempotencyKey()));
            }
        }
        return List.copyOf(messages);
    }

    @Override
    public boolean supportsAppend() {
        return false;
    }

    @Override
    public String append(ConversationAppendCommand command) {
        throw new UnsupportedOperationException("Career interview conversation adapter is read-only");
    }
}

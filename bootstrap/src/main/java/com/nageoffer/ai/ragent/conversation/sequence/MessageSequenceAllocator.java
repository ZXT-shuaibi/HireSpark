package com.nageoffer.ai.ragent.conversation.sequence;

public interface MessageSequenceAllocator {

    long next(MessageSequenceScope scope);
}

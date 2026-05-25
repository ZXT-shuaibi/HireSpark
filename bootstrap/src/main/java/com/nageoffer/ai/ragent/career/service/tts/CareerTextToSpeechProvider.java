package com.nageoffer.ai.ragent.career.service.tts;

public interface CareerTextToSpeechProvider {

    CareerTextToSpeechProviderResult synthesize(CareerTextToSpeechProviderRequest request);
}

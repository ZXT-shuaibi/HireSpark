package com.nageoffer.ai.ragent.career.controller.request;

import lombok.Data;

@Data
public class CareerTextToSpeechPlanRequest {

    private String turnId;

    private String text;
}

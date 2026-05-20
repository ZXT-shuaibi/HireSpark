package com.nageoffer.ai.ragent.career.controller.request;

import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorObservation;
import lombok.Data;

import java.util.List;

@Data
public class CareerDemeanorAnalysisSubmitRequest {

    private Boolean consentGranted;

    private List<CareerDemeanorObservation> observations;
}

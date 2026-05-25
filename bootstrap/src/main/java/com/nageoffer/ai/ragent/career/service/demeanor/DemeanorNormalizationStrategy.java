package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public interface DemeanorNormalizationStrategy {

    String SIGNAL_FACE_EMOTION = "face-emotion";

    String SIGNAL_COMPOSITE_SCORE = "composite-score";

    DemeanorNormalizedResult normalize(CareerDemeanorAnalysisProviderResult workflowResult,
                                       DemeanorFaceSignal faceSignal,
                                       List<CareerDemeanorObservation> observations);
}

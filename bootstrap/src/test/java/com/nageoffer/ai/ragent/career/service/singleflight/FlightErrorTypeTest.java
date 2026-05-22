package com.nageoffer.ai.ragent.career.service.singleflight;

import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardTimeoutException;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlightErrorTypeTest {

    @Test
    void classifiesKnownSingleFlightFailures() {
        assertThat(FlightErrorType.from(new CareerAiGuardTimeoutException("REPORT", null)))
                .isEqualTo(FlightErrorType.AI_GUARD_TIMEOUT);
        assertThat(FlightErrorType.from(new ClientException("bad request")))
                .isEqualTo(FlightErrorType.CLIENT_ERROR);
        assertThat(FlightErrorType.from(new ServiceException("model unavailable")))
                .isEqualTo(FlightErrorType.SERVICE_ERROR);
        assertThat(FlightErrorType.from(new IllegalStateException("owner lost")))
                .isEqualTo(FlightErrorType.OWNER_LOST);
    }

    @Test
    void fallsBackToUnknownForBlankFailure() {
        assertThat(FlightErrorType.from(null)).isEqualTo(FlightErrorType.UNKNOWN);
    }
}

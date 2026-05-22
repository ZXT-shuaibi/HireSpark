package com.nageoffer.ai.ragent.career.service.singleflight;

import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardTimeoutException;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;

public enum FlightErrorType {

    AI_GUARD_TIMEOUT,
    CLIENT_ERROR,
    SERVICE_ERROR,
    OWNER_LOST,
    INTERRUPTED,
    UNKNOWN;

    public static FlightErrorType from(Throwable failure) {
        if (failure == null) {
            return UNKNOWN;
        }
        if (failure instanceof CareerAiGuardTimeoutException) {
            return AI_GUARD_TIMEOUT;
        }
        if (failure instanceof ClientException) {
            return CLIENT_ERROR;
        }
        if (failure instanceof ServiceException) {
            return SERVICE_ERROR;
        }
        if (failure instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
            return INTERRUPTED;
        }
        String message = failure.getMessage();
        if (message != null && message.toLowerCase().contains("owner lost")) {
            return OWNER_LOST;
        }
        return UNKNOWN;
    }

    public String code() {
        return name();
    }
}

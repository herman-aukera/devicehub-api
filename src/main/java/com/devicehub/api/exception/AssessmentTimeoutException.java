package com.devicehub.api.exception;

public class AssessmentTimeoutException extends RuntimeException {

    public AssessmentTimeoutException(Long deviceId) {
        super("Device assessment timed out for id: " + deviceId);
    }
}

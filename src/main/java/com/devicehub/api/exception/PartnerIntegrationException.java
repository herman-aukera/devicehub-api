package com.devicehub.api.exception;

public class PartnerIntegrationException extends RuntimeException {

    public PartnerIntegrationException(String partner, Throwable cause) {
        super("Partner integration failed: " + partner, cause);
    }
}

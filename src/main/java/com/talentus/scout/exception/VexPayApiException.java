package com.talentus.scout.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class VexPayApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public VexPayApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public VexPayApiException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}

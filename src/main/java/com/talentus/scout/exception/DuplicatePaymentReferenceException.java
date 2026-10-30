package com.talentus.scout.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicatePaymentReferenceException extends RuntimeException {
    public DuplicatePaymentReferenceException(String message) {
        super(message);
    }
}

package com.talentus.scout.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateAthleteDocumentException extends RuntimeException {
    public DuplicateAthleteDocumentException(String message) {
        super(message);
    }
}

package com.vista.pdg.exception;

import com.vista.pdg.model.response.StructureResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidContractException.class)
    public ResponseEntity<StructureResponse> handleInvalidContract(InvalidContractException ex) {
        return ResponseEntity.status(HttpStatusCode.valueOf(422))
            .body(StructureResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(LlmExhaustedException.class)
    public ResponseEntity<StructureResponse> handleLlmExhausted(LlmExhaustedException ex) {
        return ResponseEntity.status(503)
            .body(StructureResponse.error(ex.getMessage(), ex.attempts()));
    }

    @ExceptionHandler(UnsupportedStructureException.class)
    public ResponseEntity<StructureResponse> handleUnsupported(UnsupportedStructureException ex) {
        return ResponseEntity.badRequest()
            .body(StructureResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StructureResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError()
            .body(StructureResponse.error("Unexpected error: " + ex.getMessage(), null));
    }
}

package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.OperationalEffectivenessNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OperationalEffectivenessController.class)
public final class OperationalEffectivenessErrorHandler {
  @ExceptionHandler({
    InvalidOperationalEffectivenessRequestException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<Map<String, String>> invalid(RuntimeException error) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "INVALID_OPERATIONAL_EFFECTIVENESS_REQUEST",
                "message",
                error.getMessage()));
  }

  @ExceptionHandler(OperationalEffectivenessNotFoundException.class)
  ResponseEntity<Map<String, String>> missing(RuntimeException error) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("code", "OPERATIONAL_EFFECTIVENESS_NOT_FOUND", "message", error.getMessage()));
  }
}

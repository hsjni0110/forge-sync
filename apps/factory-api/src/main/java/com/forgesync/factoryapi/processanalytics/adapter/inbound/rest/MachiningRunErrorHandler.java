package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.CanonicalObservationHistoryNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunProcessingNotFoundException;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MachiningRunController.class)
@ConditionalOnBean(MachiningRunController.class)
public final class MachiningRunErrorHandler {

  @ExceptionHandler(InvalidMachiningRunRequestException.class)
  ProblemDetail invalidRequest(InvalidMachiningRunRequestException exception) {
    return problem(HttpStatus.BAD_REQUEST, "MACHINING_RUN_REQUEST_INVALID", exception.getMessage());
  }

  @ExceptionHandler({
    CanonicalObservationHistoryNotFoundException.class,
    MachiningRunProcessingNotFoundException.class
  })
  ProblemDetail notFound(RuntimeException exception) {
    return problem(HttpStatus.NOT_FOUND, "MACHINING_RUN_INPUT_NOT_FOUND", exception.getMessage());
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle("Machining Run processing failed");
    problem.setType(URI.create("https://forgesync.local/problems/" + code.toLowerCase()));
    problem.setProperty("code", code);
    return problem;
  }
}

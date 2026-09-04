package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.CycleFeatureProcessingNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunProcessingNotFoundException;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureUnitMismatchException;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CycleFeatureController.class)
@ConditionalOnProperty(
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
public final class CycleFeatureErrorHandler {
  @ExceptionHandler(InvalidCycleFeatureRequestException.class)
  ProblemDetail invalidRequest(RuntimeException exception) {
    return problem(HttpStatus.BAD_REQUEST, "CYCLE_FEATURE_REQUEST_INVALID", exception.getMessage());
  }

  @ExceptionHandler({
    MachiningRunProcessingNotFoundException.class,
    CycleFeatureProcessingNotFoundException.class
  })
  ProblemDetail notFound(RuntimeException exception) {
    return problem(HttpStatus.NOT_FOUND, "CYCLE_FEATURE_INPUT_NOT_FOUND", exception.getMessage());
  }

  @ExceptionHandler(CycleFeatureUnitMismatchException.class)
  ProblemDetail unitMismatch(CycleFeatureUnitMismatchException exception) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "CYCLE_FEATURE_UNIT_MISMATCH", exception.getMessage());
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle("Cycle Feature processing failed");
    problem.setType(URI.create("https://forgesync.local/problems/" + code.toLowerCase()));
    problem.setProperty("code", code);
    return problem;
  }
}

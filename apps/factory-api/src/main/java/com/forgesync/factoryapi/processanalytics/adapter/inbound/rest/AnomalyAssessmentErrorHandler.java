package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.AnomalyAssessmentProcessingNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureProcessingNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunProcessingNotFoundException;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AnomalyAssessmentController.class)
@ConditionalOnProperty(
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
public final class AnomalyAssessmentErrorHandler {
  @ExceptionHandler(InvalidAnomalyAssessmentRequestException.class)
  ProblemDetail invalidRequest(RuntimeException exception) {
    return problem(HttpStatus.BAD_REQUEST, "ANOMALY_ASSESSMENT_REQUEST_INVALID", exception);
  }

  @ExceptionHandler({
    CycleFeatureProcessingNotFoundException.class,
    MachiningRunProcessingNotFoundException.class,
    AnomalyAssessmentProcessingNotFoundException.class
  })
  ProblemDetail notFound(RuntimeException exception) {
    return problem(HttpStatus.NOT_FOUND, "ANOMALY_ASSESSMENT_INPUT_NOT_FOUND", exception);
  }

  private static ProblemDetail problem(HttpStatus status, String code, RuntimeException exception) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setTitle("Anomaly Assessment processing failed");
    problem.setType(URI.create("https://forgesync.local/problems/" + code.toLowerCase()));
    problem.setProperty("code", code);
    return problem;
  }
}

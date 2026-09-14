package com.forgesync.factoryapi.alarm.adapter.inbound.rest;

import com.forgesync.factoryapi.alarm.application.AlarmNotFoundException;
import com.forgesync.factoryapi.alarm.application.AlarmRevisionConflictException;
import com.forgesync.factoryapi.alarm.domain.InvalidAlarmTransitionException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AlarmController.class)
public final class AlarmErrorHandler {
  @ExceptionHandler(AlarmNotFoundException.class)
  ResponseEntity<ProblemDetail> notFound() {
    return problem(HttpStatus.NOT_FOUND, "ALARM_NOT_FOUND", "Alarm was not found");
  }

  @ExceptionHandler(AlarmRevisionConflictException.class)
  ResponseEntity<ProblemDetail> conflict() {
    return problem(
        HttpStatus.CONFLICT, "ALARM_REVISION_CONFLICT", "Alarm changed; refresh and retry");
  }

  @ExceptionHandler({IllegalArgumentException.class, InvalidAlarmTransitionException.class})
  ResponseEntity<ProblemDetail> invalid() {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "ALARM_COMMAND_INVALID", "Alarm request is invalid");
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle("Alarm request failed");
    problem.setType(URI.create("https://forgesync.local/problems/" + code.toLowerCase()));
    problem.setProperty("code", code);
    return ResponseEntity.status(status).body(problem);
  }
}

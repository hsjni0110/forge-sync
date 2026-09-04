package com.forgesync.factoryapi.replay.adapter.inbound.rest;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = ReplayController.class)
@ConditionalOnBean(ReplayController.class)
public final class ReplayErrorHandler {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ProblemDetail> handleStatus(ResponseStatusException exception) {
    HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
    return problem(status, code(status), exception.getReason());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> handleInvalidCommand(IllegalArgumentException exception) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "REPLAY_COMMAND_INVALID", "Replay command is invalid");
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle("Replay control failed");
    problem.setType(URI.create("https://forgesync.local/problems/" + code.toLowerCase()));
    problem.setProperty("code", code);
    return ResponseEntity.status(status).body(problem);
  }

  private static String code(HttpStatus status) {
    return switch (status) {
      case NOT_FOUND -> "REPLAY_SESSION_NOT_FOUND";
      case CONFLICT -> "REPLAY_STATE_CONFLICT";
      case UNPROCESSABLE_ENTITY -> "REPLAY_COMMAND_INVALID";
      default -> "REPLAY_SERVICE_UNAVAILABLE";
    };
  }
}

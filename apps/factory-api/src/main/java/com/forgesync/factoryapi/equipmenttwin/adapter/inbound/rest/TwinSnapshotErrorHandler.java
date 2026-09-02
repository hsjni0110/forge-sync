package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.application.MachineTwinNotFoundException;
import com.forgesync.factoryapi.equipmenttwin.application.TwinSnapshotUnavailableException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TwinSnapshotController.class)
public final class TwinSnapshotErrorHandler {

  @ExceptionHandler(InvalidMachineIdException.class)
  ProblemDetail invalidMachineId(InvalidMachineIdException exception) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_MACHINE_ID",
        "Invalid machine identifier",
        exception.getMessage());
  }

  @ExceptionHandler(MachineTwinNotFoundException.class)
  ProblemDetail machineNotFound(MachineTwinNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "MACHINE_TWIN_NOT_FOUND",
        "Machine Twin not found",
        exception.getMessage());
  }

  @ExceptionHandler(TwinSnapshotUnavailableException.class)
  ProblemDetail snapshotUnavailable(TwinSnapshotUnavailableException exception) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "TWIN_SNAPSHOT_UNAVAILABLE",
        "Twin snapshot unavailable",
        "The authoritative Twin snapshot is temporarily unavailable");
  }

  private static ProblemDetail problem(
      HttpStatus status, String code, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("https://forgesync.local/problems/" + code.toLowerCase()));
    problem.setTitle(title);
    problem.setProperty("code", code);
    return problem;
  }
}

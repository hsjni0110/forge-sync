package com.forgesync.factoryapi.equipmenttwin.application;

public final class TwinSnapshotUnavailableException extends RuntimeException {

  public TwinSnapshotUnavailableException(String message) {
    super(message);
  }

  public TwinSnapshotUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

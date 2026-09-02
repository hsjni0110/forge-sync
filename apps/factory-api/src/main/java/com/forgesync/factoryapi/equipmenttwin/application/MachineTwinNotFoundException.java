package com.forgesync.factoryapi.equipmenttwin.application;

public final class MachineTwinNotFoundException extends RuntimeException {

  public MachineTwinNotFoundException(String machineId) {
    super("Machine Twin was not found: " + machineId);
  }
}

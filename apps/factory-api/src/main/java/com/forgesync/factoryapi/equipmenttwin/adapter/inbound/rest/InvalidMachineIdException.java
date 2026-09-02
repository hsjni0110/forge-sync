package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

final class InvalidMachineIdException extends RuntimeException {

  InvalidMachineIdException() {
    super("machineId must match [A-Za-z0-9._-]{1,64}");
  }
}

package com.forgesync.factoryapi.equipmenttwin.domain;

public record TwinVersion(long value) {

  public TwinVersion {
    if (value < 0) {
      throw new IllegalArgumentException("TwinVersion must not be negative");
    }
  }

  public TwinVersion next() {
    return new TwinVersion(Math.addExact(value, 1));
  }
}

package com.forgesync.factoryapi.processanalytics.domain;

public enum CycleSignal {
  EXECUTION,
  SPINDLE_SPEED,
  LOAD,
  PATH_FEEDRATE;

  public boolean isMetric() {
    return this != EXECUTION;
  }

  public CycleMetric metric() {
    return CycleMetric.valueOf(name());
  }
}

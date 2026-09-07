package com.forgesync.factoryapi.equipmenttwin.domain;

/** The Event signals whose observations are reconstructed into dwell intervals. */
public enum StateSignal {
  EXECUTION,
  CONTROLLER_MODE,
  POWER_STATE,
  EMERGENCY_STOP;

  /** Short aliases used by callers and tests that read closer to the source names. */
  public static final StateSignal MODE = CONTROLLER_MODE;

  public static final StateSignal POWER = POWER_STATE;

  public static final StateSignal ESTOP = EMERGENCY_STOP;
}

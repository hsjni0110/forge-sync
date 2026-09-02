package com.forgesync.factoryapi.equipmenttwin.application;

/** Best-effort notification invoked only after an authoritative Twin projection commits. */
@FunctionalInterface
public interface TwinProjectionNotifier {

  void projectionCommitted(String machineId);

  static TwinProjectionNotifier noOp() {
    return machineId -> {};
  }
}

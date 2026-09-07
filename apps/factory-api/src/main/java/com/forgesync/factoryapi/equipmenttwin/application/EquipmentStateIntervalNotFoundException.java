package com.forgesync.factoryapi.equipmenttwin.application;

public final class EquipmentStateIntervalNotFoundException extends RuntimeException {
  public EquipmentStateIntervalNotFoundException(String reference) {
    super("No equipment state interval processing for " + reference);
  }
}

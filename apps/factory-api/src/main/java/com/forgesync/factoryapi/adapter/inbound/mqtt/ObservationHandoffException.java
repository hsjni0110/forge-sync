package com.forgesync.factoryapi.adapter.inbound.mqtt;

final class ObservationHandoffException extends RuntimeException {

  ObservationHandoffException(RuntimeException cause) {
    super("MQTT observation application handoff failed", cause);
  }
}

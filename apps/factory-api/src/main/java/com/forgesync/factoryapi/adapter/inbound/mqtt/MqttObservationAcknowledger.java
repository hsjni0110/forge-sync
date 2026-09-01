package com.forgesync.factoryapi.adapter.inbound.mqtt;

@FunctionalInterface
public interface MqttObservationAcknowledger {

  void acknowledge();
}

package com.forgesync.factoryapi.adapter.inbound.mqtt;

final class MqttObservationRejectedException extends RuntimeException {

  private final MqttObservationRejection rejection;

  MqttObservationRejectedException(MqttObservationRejection rejection) {
    super("MQTT observation rejected: " + rejection.metricValue());
    this.rejection = rejection;
  }

  MqttObservationRejectedException(MqttObservationRejection rejection, Throwable cause) {
    super("MQTT observation rejected: " + rejection.metricValue(), cause);
    this.rejection = rejection;
  }

  MqttObservationRejection rejection() {
    return rejection;
  }
}

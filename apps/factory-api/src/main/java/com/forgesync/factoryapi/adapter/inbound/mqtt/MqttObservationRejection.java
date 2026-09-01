package com.forgesync.factoryapi.adapter.inbound.mqtt;

enum MqttObservationRejection {
  PAYLOAD_TOO_LARGE("payload_too_large"),
  TOPIC_MISMATCH("topic_mismatch"),
  METADATA_MISMATCH("metadata_mismatch"),
  MALFORMED_JSON("malformed_json"),
  CONTRACT_INVALID("contract_invalid"),
  REPLAY_IDENTITY_MISSING("replay_identity_missing");

  private final String metricValue;

  MqttObservationRejection(String metricValue) {
    this.metricValue = metricValue;
  }

  String metricValue() {
    return metricValue;
  }
}

package com.forgesync.factoryapi.adapter.inbound.mqtt;

import com.forgesync.factoryapi.application.IngestionResult;
import com.forgesync.factoryapi.application.ObservationIngress;
import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public final class MqttObservationConsumer {

  private final ObservationIngress observationIngress;
  private final MqttObservationValidator observationValidator;
  private final MeterRegistry meterRegistry;
  private final Counter received;
  private final Counter forwarded;

  public MqttObservationConsumer(
      ObservationIngress observationIngress,
      MqttObservationValidator observationValidator,
      MeterRegistry meterRegistry) {
    this.observationIngress = observationIngress;
    this.observationValidator = observationValidator;
    this.meterRegistry = meterRegistry;
    this.received = meterRegistry.counter("forgesync.mqtt.observations.received");
    this.forwarded = meterRegistry.counter("forgesync.mqtt.observations.forwarded");
  }

  public void consumeObservation(
      MqttObservationPacket observationPacket, MqttObservationAcknowledger acknowledger) {
    received.increment();
    ValidatedObservationMessage observation;
    try {
      observation = observationValidator.validate(observationPacket);
    } catch (MqttObservationRejectedException exception) {
      countRejection(exception.rejection());
      acknowledgeObservation(acknowledger);
      return;
    }

    IngestionResult ingestionResult = handOffObservation(observation);
    forwarded.increment();
    meterRegistry
        .counter("forgesync.ingestion.observations", "result", ingestionResult.metricValue())
        .increment();
    acknowledgeObservation(acknowledger);
  }

  private IngestionResult handOffObservation(ValidatedObservationMessage observation) {
    try {
      return observationIngress.acceptObservation(observation);
    } catch (RuntimeException exception) {
      meterRegistry.counter("forgesync.mqtt.observations.handoff.failures").increment();
      throw new ObservationHandoffException(exception);
    }
  }

  private void countRejection(MqttObservationRejection rejection) {
    meterRegistry
        .counter("forgesync.mqtt.observations.rejected", "reason", rejection.metricValue())
        .increment();
  }

  private void acknowledgeObservation(MqttObservationAcknowledger acknowledger) {
    try {
      acknowledger.acknowledge();
    } catch (RuntimeException exception) {
      meterRegistry.counter("forgesync.mqtt.observations.acknowledgment.failures").increment();
      throw exception;
    }
  }
}

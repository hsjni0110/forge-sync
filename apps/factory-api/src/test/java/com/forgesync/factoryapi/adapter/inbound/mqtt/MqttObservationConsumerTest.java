package com.forgesync.factoryapi.adapter.inbound.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.adapter.inbound.observation.ObservationContractValidator;
import com.forgesync.factoryapi.application.IngestionResult;
import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MqttObservationConsumerTest {

  private static final String REPLAY_SESSION_ID = "00d64db8-967e-41ba-9d09-fdd087710aac";
  private static final String SOURCE_EVENT_KEY =
      "sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=1526";

  private final List<ValidatedObservationMessage> accepted = new ArrayList<>();
  private final AtomicInteger acknowledgments = new AtomicInteger();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private MqttObservationConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer =
        new MqttObservationConsumer(
            observation -> {
              accepted.add(observation);
              return IngestionResult.ACCEPTED;
            },
            new MqttObservationValidator(new ObservationContractValidator(), new ObjectMapper()),
            meterRegistry);
  }

  @Test
  void forwardsValidObservationAndAcknowledgesAfterApplicationSuccess() {
    consumer.consumeObservation(validPacket(), acknowledgments::incrementAndGet);

    assertThat(accepted).hasSize(1);
    assertThat(accepted.getFirst().machineId()).isEqualTo("Mazak01");
    assertThat(acknowledgments).hasValue(1);
    assertThat(counter("forgesync.mqtt.observations.forwarded")).isEqualTo(1);
  }

  @Test
  void exposesDuplicateQoS1DeliveriesToApplicationForInboxHandling() {
    MqttObservationPacket duplicate = validPacket();
    AtomicInteger handoffs = new AtomicInteger();
    consumer =
        new MqttObservationConsumer(
            observation -> {
              accepted.add(observation);
              return handoffs.getAndIncrement() == 0
                  ? IngestionResult.ACCEPTED
                  : IngestionResult.SKIPPED_DUPLICATE;
            },
            new MqttObservationValidator(new ObservationContractValidator(), new ObjectMapper()),
            meterRegistry);

    consumer.consumeObservation(duplicate, acknowledgments::incrementAndGet);
    consumer.consumeObservation(duplicate, acknowledgments::incrementAndGet);

    assertThat(accepted).hasSize(2);
    assertThat(acknowledgments).hasValue(2);
    assertThat(ingestionResult("accepted")).isEqualTo(1);
    assertThat(ingestionResult("skipped_duplicate")).isEqualTo(1);
  }

  @Test
  void rejectsInvalidVersionBeforeApplicationAndCountsReason() {
    byte[] invalid =
        validPayload().replace("\"2.0.0\"", "\"9.0.0\"").getBytes(StandardCharsets.UTF_8);
    MqttObservationPacket packet = packet(invalid, validProperties());

    consumer.consumeObservation(packet, acknowledgments::incrementAndGet);

    assertThat(accepted).isEmpty();
    assertThat(acknowledgments).hasValue(1);
    assertThat(rejected("contract_invalid")).isEqualTo(1);
  }

  @Test
  void acceptsUpgradedSchemaVersionCarryingNewCanonicalVocabulary() {
    MqttObservationPacket packet =
        packet(upgradedPayload().getBytes(StandardCharsets.UTF_8), properties("2.1.0"));

    consumer.consumeObservation(packet, acknowledgments::incrementAndGet);

    assertThat(accepted).hasSize(1);
    assertThat(accepted.getFirst().schemaVersion()).isEqualTo("2.1.0");
    assertThat(acknowledgments).hasValue(1);
  }

  @Test
  void rejectsDeliveryPropertyThatDisagreesWithThePayloadVersion() {
    MqttObservationPacket packet =
        packet(validPayload().getBytes(StandardCharsets.UTF_8), properties("2.1.0"));

    consumer.consumeObservation(packet, acknowledgments::incrementAndGet);

    assertThat(accepted).isEmpty();
    assertThat(rejected("metadata_mismatch")).isEqualTo(1);
  }

  @Test
  void rejectsMetadataMismatchWithoutLoggingOrForwardingPayload() {
    MqttObservationPacket packet =
        packet(validPayload().getBytes(StandardCharsets.UTF_8), Map.of());

    consumer.consumeObservation(packet, acknowledgments::incrementAndGet);

    assertThat(accepted).isEmpty();
    assertThat(rejected("metadata_mismatch")).isEqualTo(1);
  }

  @Test
  void rejectsMalformedJsonAndCountsStableReason() {
    MqttObservationPacket packet = packet("{".getBytes(StandardCharsets.UTF_8), validProperties());

    consumer.consumeObservation(packet, acknowledgments::incrementAndGet);

    assertThat(accepted).isEmpty();
    assertThat(acknowledgments).hasValue(1);
    assertThat(rejected("malformed_json")).isEqualTo(1);
  }

  @Test
  void leavesDeliveryUnacknowledgedWhenApplicationFails() {
    MqttObservationConsumer failing =
        new MqttObservationConsumer(
            ignored -> {
              throw new IllegalStateException("database unavailable");
            },
            new MqttObservationValidator(new ObservationContractValidator(), new ObjectMapper()),
            meterRegistry);

    assertThatThrownBy(
            () -> failing.consumeObservation(validPacket(), acknowledgments::incrementAndGet))
        .isInstanceOf(ObservationHandoffException.class)
        .hasCauseInstanceOf(IllegalStateException.class);

    assertThat(acknowledgments).hasValue(0);
    assertThat(counter("forgesync.mqtt.observations.handoff.failures")).isEqualTo(1);
  }

  @Test
  void countsAcknowledgmentFailureSeparatelyFromSuccessfulHandoff() {
    assertThatThrownBy(
            () ->
                consumer.consumeObservation(
                    validPacket(),
                    () -> {
                      throw new IllegalStateException("ack unavailable");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(accepted).hasSize(1);
    assertThat(counter("forgesync.mqtt.observations.forwarded")).isEqualTo(1);
    assertThat(counter("forgesync.mqtt.observations.acknowledgment.failures")).isEqualTo(1);
    assertThat(meterRegistry.find("forgesync.mqtt.observations.handoff.failures").counter())
        .isNull();
  }

  @Test
  void rejectsPayloadOver65536BytesBeforeParsing() {
    MqttObservationPacket packet = packet(new byte[65_537], validProperties());

    consumer.consumeObservation(packet, acknowledgments::incrementAndGet);

    assertThat(accepted).isEmpty();
    assertThat(rejected("payload_too_large")).isEqualTo(1);
  }

  @Test
  void rejectsMalformedUtf8InsteadOfReplacingInvalidBytes() {
    byte[] invalidUtf8 = validPayload().getBytes(StandardCharsets.UTF_8);
    invalidUtf8[0] = (byte) 0x80;

    consumer.consumeObservation(
        packet(invalidUtf8, validProperties()), acknowledgments::incrementAndGet);

    assertThat(accepted).isEmpty();
    assertThat(acknowledgments).hasValue(1);
    assertThat(rejected("malformed_json")).isEqualTo(1);
  }

  private MqttObservationPacket validPacket() {
    return packet(validPayload().getBytes(StandardCharsets.UTF_8), validProperties());
  }

  private static MqttObservationPacket packet(
      byte[] payload, Map<String, List<String>> properties) {
    return new MqttObservationPacket(
        "forgesync/observations/Mazak01",
        payload,
        1,
        false,
        MqttObservationValidator.CONTENT_TYPE,
        1,
        properties);
  }

  private static Map<String, List<String>> validProperties() {
    return properties("2.0.0");
  }

  private static Map<String, List<String>> properties(String schemaVersion) {
    return Map.of(
        "schema-version", List.of(schemaVersion),
        "message-key", List.of(REPLAY_SESSION_ID + ":" + SOURCE_EVENT_KEY));
  }

  private static String upgradedPayload() {
    return validPayload()
        .replace("\"schemaVersion\": \"2.0.0\"", "\"schemaVersion\": \"2.1.0\"")
        .replace("\"EXECUTION\"", "\"EMERGENCY_STOP\"")
        .replace("\"ACTIVE\"", "\"TRIGGERED\"");
  }

  private static String validPayload() {
    String path = "fixtures/canonical/v2/valid/event-execution.json";
    try (InputStream stream =
        MqttObservationConsumerTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Fixture is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read fixture", exception);
    }
  }

  private double counter(String name) {
    return meterRegistry.get(name).counter().count();
  }

  private double rejected(String reason) {
    return meterRegistry
        .get("forgesync.mqtt.observations.rejected")
        .tag("reason", reason)
        .counter()
        .count();
  }

  private double ingestionResult(String result) {
    return meterRegistry
        .get("forgesync.ingestion.observations")
        .tag("result", result)
        .counter()
        .count();
  }
}

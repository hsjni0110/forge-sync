package com.forgesync.factoryapi.adapter.inbound.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.adapter.inbound.observation.ObservationContractValidator;
import com.forgesync.factoryapi.application.InvalidObservationContractException;
import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class MqttObservationValidator {

  static final int MAX_OBSERVATION_BYTES = 65_536;
  static final String CONTENT_TYPE = "application/vnd.forgesync.observation+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final String TOPIC_PREFIX = "forgesync/observations/";

  private final ObservationContractValidator contractValidator;
  private final ObjectMapper objectMapper;

  public MqttObservationValidator(
      ObservationContractValidator contractValidator, ObjectMapper objectMapper) {
    this.contractValidator = contractValidator;
    this.objectMapper = objectMapper;
  }

  public ValidatedObservationMessage validate(MqttObservationPacket observationPacket) {
    validateTransportMetadata(observationPacket);
    String observationJson = decodeUtf8(observationPacket.payload());
    JsonNode document = parseJson(observationJson);
    validateContract(observationJson);
    JsonNode replay = requireReplayIdentity(document);
    String machineId = document.path("machineId").asText();
    String replaySessionId = replay.path("replaySessionId").asText();
    String sourceEventKey = document.path("sourceEventKey").asText();
    validateRoutingMetadata(observationPacket, machineId, replaySessionId, sourceEventKey);
    return new ValidatedObservationMessage(
        observationJson, machineId, replaySessionId, sourceEventKey);
  }

  private static void validateTransportMetadata(MqttObservationPacket observationPacket) {
    if (observationPacket.payload().length > MAX_OBSERVATION_BYTES) {
      reject(MqttObservationRejection.PAYLOAD_TOO_LARGE);
    }
    if (!hasValidTopic(observationPacket.topic())) {
      reject(MqttObservationRejection.TOPIC_MISMATCH);
    }
    if (observationPacket.qos() != 1
        || observationPacket.retained()
        || !CONTENT_TYPE.equals(observationPacket.contentType())
        || !Integer.valueOf(1).equals(observationPacket.payloadFormatIndicator())) {
      reject(MqttObservationRejection.METADATA_MISMATCH);
    }
  }

  private static boolean hasValidTopic(String topic) {
    if (!topic.startsWith(TOPIC_PREFIX)) {
      return false;
    }
    String topicMachineId = topic.substring(TOPIC_PREFIX.length());
    return MACHINE_ID.matcher(topicMachineId).matches();
  }

  private static String decodeUtf8(byte[] payload) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(payload))
          .toString();
    } catch (CharacterCodingException exception) {
      throw new MqttObservationRejectedException(
          MqttObservationRejection.MALFORMED_JSON, exception);
    }
  }

  private JsonNode parseJson(String observationJson) {
    try {
      return objectMapper.readTree(observationJson);
    } catch (JsonProcessingException exception) {
      throw new MqttObservationRejectedException(
          MqttObservationRejection.MALFORMED_JSON, exception);
    }
  }

  private void validateContract(String observationJson) {
    try {
      contractValidator.validate(observationJson);
    } catch (InvalidObservationContractException exception) {
      throw new MqttObservationRejectedException(
          MqttObservationRejection.CONTRACT_INVALID, exception);
    }
  }

  private static JsonNode requireReplayIdentity(JsonNode document) {
    JsonNode replay = document.path("replay");
    if (!replay.isObject()) {
      reject(MqttObservationRejection.REPLAY_IDENTITY_MISSING);
    }
    return replay;
  }

  private static void validateRoutingMetadata(
      MqttObservationPacket observationPacket,
      String machineId,
      String replaySessionId,
      String sourceEventKey) {
    Map<String, List<String>> properties = observationPacket.userProperties();
    boolean matches =
        observationPacket.topic().equals(TOPIC_PREFIX + machineId)
            && properties.getOrDefault("schema-version", List.of()).equals(List.of("2.0.0"))
            && properties
                .getOrDefault("message-key", List.of())
                .equals(List.of(replaySessionId + ":" + sourceEventKey));
    if (!matches) {
      reject(MqttObservationRejection.METADATA_MISMATCH);
    }
  }

  private static void reject(MqttObservationRejection rejection) {
    throw new MqttObservationRejectedException(rejection);
  }
}

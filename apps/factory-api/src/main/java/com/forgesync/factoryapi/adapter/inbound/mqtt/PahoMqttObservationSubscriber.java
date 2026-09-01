package com.forgesync.factoryapi.adapter.inbound.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PahoMqttObservationSubscriber implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(PahoMqttObservationSubscriber.class);
  private static final String TOPIC_FILTER = "forgesync/observations/+";
  private static final long OPERATION_TIMEOUT_MILLIS = 5_000;

  private final MqttObservationConsumer consumer;
  private final MqttAsyncClient client;
  private final MqttConnectionOptions connectionOptions;
  private final ExecutorService reconnectExecutor =
      Executors.newSingleThreadExecutor(
          runnable -> Thread.ofPlatform().name("mqtt-observation-reconnect").unstarted(runnable));
  private final AtomicBoolean isClosing = new AtomicBoolean();
  private final AtomicBoolean isReconnectScheduled = new AtomicBoolean();

  public PahoMqttObservationSubscriber(
      MqttSubscriberConfig config, MqttObservationConsumer consumer) throws MqttException {
    this.consumer = consumer;
    this.client = new MqttAsyncClient(config.serverUri(), config.clientId());
    this.connectionOptions = createConnectionOptions(config);
    client.setManualAcks(true);
    client.setCallback(new ObservationCallback());
  }

  public void start() throws MqttException {
    awaitCompletion(client.connect(connectionOptions));
    awaitCompletion(client.subscribe(TOPIC_FILTER, 1));
  }

  @Override
  public void close() throws MqttException {
    isClosing.set(true);
    reconnectExecutor.shutdownNow();
    if (client.isConnected()) {
      awaitCompletion(client.disconnect());
    }
    client.close();
  }

  private static MqttConnectionOptions createConnectionOptions(MqttSubscriberConfig config) {
    MqttConnectionOptions options = new MqttConnectionOptions();
    options.setCleanStart(false);
    options.setSessionExpiryInterval(86_400L);
    options.setKeepAliveInterval(30);
    options.setConnectionTimeout(5);
    options.setAutomaticReconnect(true);
    options.setAutomaticReconnectDelay(1, 30);
    if (config.username() != null) {
      options.setUserName(config.username());
      if (config.password() != null) {
        options.setPassword(config.password().getBytes(StandardCharsets.UTF_8));
      }
    }
    return options;
  }

  private static void awaitCompletion(IMqttToken token) throws MqttException {
    token.waitForCompletion(OPERATION_TIMEOUT_MILLIS);
  }

  private void scheduleReconnect() {
    if (isClosing.get() || !isReconnectScheduled.compareAndSet(false, true)) {
      return;
    }
    try {
      reconnectExecutor.submit(this::reconnectAfterDeliveryFailure);
    } catch (RejectedExecutionException exception) {
      isReconnectScheduled.set(false);
      if (!isClosing.get()) {
        throw exception;
      }
    }
  }

  private void reconnectAfterDeliveryFailure() {
    try {
      client.disconnectForcibly();
      awaitCompletion(client.connect(connectionOptions));
      awaitCompletion(client.subscribe(TOPIC_FILTER, 1));
    } catch (MqttException exception) {
      LOGGER.warn(
          "MQTT observation consumer reconnect failed: reasonCode={}", exception.getReasonCode());
    } finally {
      isReconnectScheduled.set(false);
    }
  }

  private final class ObservationCallback implements MqttCallback {

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {
      if (!isClosing.get()) {
        LOGGER.warn("MQTT observation consumer disconnected");
      }
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
      LOGGER.warn("MQTT observation consumer error: reasonCode={}", exception.getReasonCode());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
      try {
        consumer.consumeObservation(
            toObservationPacket(topic, message),
            () -> acknowledge(message.getId(), message.getQos()));
      } catch (ObservationHandoffException exception) {
        LOGGER.warn("MQTT observation application handoff failed");
        scheduleReconnect();
      } catch (MqttAcknowledgmentException exception) {
        LOGGER.warn("MQTT observation acknowledgment failed");
        scheduleReconnect();
      } catch (RuntimeException exception) {
        LOGGER.error("Unexpected MQTT observation consumer failure", exception);
        scheduleReconnect();
      }
    }

    @Override
    public void deliveryComplete(IMqttToken token) {}

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
      LOGGER.info("MQTT observation consumer connected: reconnect={}", reconnect);
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {}
  }

  private void acknowledge(int messageId, int qos) {
    try {
      client.messageArrivedComplete(messageId, qos);
    } catch (MqttException exception) {
      throw new MqttAcknowledgmentException(exception);
    }
  }

  private static MqttObservationPacket toObservationPacket(String topic, MqttMessage message) {
    MqttProperties properties = message.getProperties();
    if (properties == null) {
      return new MqttObservationPacket(
          topic,
          message.getPayload(),
          message.getQos(),
          message.isRetained(),
          null,
          null,
          Map.of());
    }
    return new MqttObservationPacket(
        topic,
        message.getPayload(),
        message.getQos(),
        message.isRetained(),
        properties.getContentType(),
        properties.getPayloadFormat() ? 1 : 0,
        groupUserProperties(properties.getUserProperties()));
  }

  private static Map<String, List<String>> groupUserProperties(List<UserProperty> properties) {
    if (properties == null) {
      return Map.of();
    }
    Map<String, List<String>> groupedProperties = new LinkedHashMap<>();
    for (UserProperty property : properties) {
      groupedProperties
          .computeIfAbsent(property.getKey(), ignored -> new ArrayList<>())
          .add(property.getValue());
    }
    return groupedProperties;
  }

  private static final class MqttAcknowledgmentException extends RuntimeException {
    private MqttAcknowledgmentException(MqttException cause) {
      super("MQTT acknowledgment failed", cause);
    }
  }
}

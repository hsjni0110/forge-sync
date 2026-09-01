package com.forgesync.factoryapi.adapter.inbound.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.adapter.inbound.observation.ObservationContractValidator;
import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("mqtt-integration")
@EnabledIfEnvironmentVariable(named = "FORGESYNC_MQTT_INTEGRATION", matches = "1")
class MqttObservationBrokerIntegrationTest {

  private static final String REPLAY_SESSION_ID = "00d64db8-967e-41ba-9d09-fdd087710aac";
  private static final String SOURCE_EVENT_KEY =
      "sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=1526";

  @Test
  void brokerDeliversDuplicatesAndRejectsInvalidVersionBeforeApplication() throws Exception {
    String serverUri =
        "tcp://"
            + System.getenv().getOrDefault("FORGESYNC_MQTT_HOST", "127.0.0.1")
            + ":"
            + System.getenv().getOrDefault("FORGESYNC_MQTT_PORT", "18883");
    List<ValidatedObservationMessage> accepted = new CopyOnWriteArrayList<>();
    CountDownLatch acceptedTwice = new CountDownLatch(2);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    MqttObservationConsumer consumer =
        new MqttObservationConsumer(
            observation -> {
              accepted.add(observation);
              acceptedTwice.countDown();
            },
            new MqttObservationValidator(new ObservationContractValidator(), new ObjectMapper()),
            registry);
    PahoMqttObservationSubscriber subscriber =
        new PahoMqttObservationSubscriber(
            new MqttSubscriberConfig(serverUri, "forgesync-api-contract-test", null, null),
            consumer);
    MqttAsyncClient publisher = new MqttAsyncClient(serverUri, "forgesync-api-test-publisher");
    try {
      subscriber.start();
      publisher.connect(connectionOptions()).waitForCompletion(5_000);

      String invalid = validPayload().replaceFirst("2\\.0\\.0", "9.0.0");
      publish(publisher, invalid, validProperties());
      publish(publisher, validPayload(), validProperties());
      publish(publisher, validPayload(), validProperties());

      assertThat(acceptedTwice.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(accepted).hasSize(2);
      assertThat(
              registry
                  .get("forgesync.mqtt.observations.rejected")
                  .tag("reason", "contract_invalid")
                  .counter()
                  .count())
          .isEqualTo(1);
    } finally {
      if (publisher.isConnected()) {
        publisher.disconnect().waitForCompletion(5_000);
      }
      publisher.close();
      subscriber.close();
    }
  }

  private static void publish(
      MqttAsyncClient publisher, String payload, List<UserProperty> userProperties)
      throws Exception {
    MqttProperties properties = new MqttProperties();
    properties.setPayloadFormat(true);
    properties.setContentType(MqttObservationValidator.CONTENT_TYPE);
    properties.setUserProperties(userProperties);
    MqttMessage message =
        new MqttMessage(payload.getBytes(StandardCharsets.UTF_8), 1, false, properties);
    publisher.publish("forgesync/observations/Mazak01", message).waitForCompletion(5_000);
  }

  private static MqttConnectionOptions connectionOptions() {
    MqttConnectionOptions options = new MqttConnectionOptions();
    options.setCleanStart(true);
    options.setConnectionTimeout(5);
    return options;
  }

  private static List<UserProperty> validProperties() {
    List<UserProperty> properties = new ArrayList<>();
    properties.add(new UserProperty("schema-version", "2.0.0"));
    properties.add(new UserProperty("message-key", REPLAY_SESSION_ID + ":" + SOURCE_EVENT_KEY));
    return properties;
  }

  private static String validPayload() {
    String path = "fixtures/canonical/v2/valid/event-execution.json";
    try (InputStream stream =
        MqttObservationBrokerIntegrationTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Fixture is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read fixture", exception);
    }
  }
}

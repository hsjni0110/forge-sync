package com.forgesync.factoryapi.adapter.inbound.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.adapter.inbound.observation.ObservationContractValidator;
import com.forgesync.factoryapi.application.ObservationIngress;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "forgesync.mqtt.enabled", havingValue = "true")
@EnableConfigurationProperties(MqttSubscriberConfig.class)
public class MqttObservationConfiguration {

  @Bean(initMethod = "start", destroyMethod = "close")
  PahoMqttObservationSubscriber mqttObservationSubscriber(
      ObservationIngress observationIngress,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      MqttSubscriberConfig subscriberConfig)
      throws MqttException {
    MqttObservationValidator observationValidator =
        new MqttObservationValidator(new ObservationContractValidator(), objectMapper);
    MqttObservationConsumer consumer =
        new MqttObservationConsumer(observationIngress, observationValidator, meterRegistry);
    return new PahoMqttObservationSubscriber(subscriberConfig, consumer);
  }
}

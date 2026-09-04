package com.forgesync.factoryapi.replay.adapter;

import com.forgesync.factoryapi.equipmenttwin.application.ActivateReplayProjection;
import com.forgesync.factoryapi.replay.adapter.outbound.http.HttpReplayControlGateway;
import com.forgesync.factoryapi.replay.application.ControlReplay;
import com.forgesync.factoryapi.replay.application.ReplayControlGateway;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(
    name = "forgesync.replay.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ReplayConfiguration {
  @Bean
  ReplayControlGateway replayControlGateway(
      @Value("${forgesync.replay.edge-base-url:http://127.0.0.1:8002}") String edgeBaseUrl) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(10));
    RestClient client =
        RestClient.builder().baseUrl(edgeBaseUrl).requestFactory(requestFactory).build();
    return new HttpReplayControlGateway(client);
  }

  @Bean
  ControlReplay controlReplay(
      ReplayControlGateway gateway, ActivateReplayProjection projectionActivator, Clock clock) {
    return new ControlReplay(gateway, projectionActivator, clock);
  }
}

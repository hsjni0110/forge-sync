package com.forgesync.factoryapi.replay.adapter.outbound.http;

import com.forgesync.factoryapi.replay.application.ReplayControlGateway;
import com.forgesync.factoryapi.replay.application.ReplaySessionState;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

public final class HttpReplayControlGateway implements ReplayControlGateway {
  private final RestClient client;

  public HttpReplayControlGateway(RestClient client) {
    this.client = Objects.requireNonNull(client);
  }

  @Override
  public ReplaySessionState prepare(String machineId, String sourceSetId, int speedMultiplier) {
    return call(
        () ->
            client
                .post()
                .uri("/internal/v1/replay-sessions")
                .body(
                    Map.of(
                        "machineId", machineId,
                        "sourceSetId", sourceSetId,
                        "speedMultiplier", speedMultiplier))
                .retrieve()
                .body(ReplaySessionState.class));
  }

  @Override
  public ReplaySessionState current(String machineId) {
    return call(
        () ->
            client
                .get()
                .uri("/internal/v1/machines/{machineId}/replay-session", machineId)
                .retrieve()
                .body(ReplaySessionState.class));
  }

  @Override
  public ReplaySessionState start(UUID sessionId, long expectedRevision, Instant seekTarget) {
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("expectedRevision", expectedRevision);
    if (seekTarget != null) {
      body.put("seekTarget", seekTarget);
    }
    return post(sessionId, "start", body);
  }

  @Override
  public ReplaySessionState pause(UUID sessionId, long expectedRevision) {
    return post(sessionId, "pause", Map.of("expectedRevision", expectedRevision));
  }

  @Override
  public ReplaySessionState resume(UUID sessionId, long expectedRevision) {
    return post(sessionId, "resume", Map.of("expectedRevision", expectedRevision));
  }

  @Override
  public ReplaySessionState changeSpeed(
      UUID sessionId, long expectedRevision, int speedMultiplier) {
    return call(
        () ->
            client
                .put()
                .uri("/internal/v1/replay-sessions/{id}/speed", sessionId)
                .body(
                    Map.of(
                        "expectedRevision", expectedRevision,
                        "speedMultiplier", speedMultiplier))
                .retrieve()
                .body(ReplaySessionState.class));
  }

  @Override
  public ReplaySessionState prepareReplacement(
      UUID sessionId, long expectedRevision, int speedMultiplier) {
    return post(
        sessionId,
        "replacement",
        Map.of("expectedRevision", expectedRevision, "speedMultiplier", speedMultiplier));
  }

  private ReplaySessionState post(UUID sessionId, String action, Map<String, ?> body) {
    return call(
        () ->
            client
                .post()
                .uri("/internal/v1/replay-sessions/{id}/{action}", sessionId, action)
                .body(body)
                .retrieve()
                .body(ReplaySessionState.class));
  }

  private static ReplaySessionState call(ReplayCall call) {
    try {
      return Objects.requireNonNull(call.invoke(), "Edge Replay response");
    } catch (RestClientResponseException exception) {
      HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
      HttpStatus translated =
          status == HttpStatus.NOT_FOUND
                  || status == HttpStatus.CONFLICT
                  || status == HttpStatus.UNPROCESSABLE_ENTITY
              ? status
              : HttpStatus.SERVICE_UNAVAILABLE;
      throw new ResponseStatusException(translated, "Replay control was rejected");
    } catch (RuntimeException exception) {
      if (exception instanceof ResponseStatusException responseStatusException) {
        throw responseStatusException;
      }
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Replay Edge is unavailable", exception);
    }
  }

  @FunctionalInterface
  private interface ReplayCall {
    ReplaySessionState invoke();
  }
}

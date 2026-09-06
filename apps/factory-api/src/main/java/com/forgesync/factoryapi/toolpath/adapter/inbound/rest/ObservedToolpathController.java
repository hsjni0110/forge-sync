package com.forgesync.factoryapi.toolpath.adapter.inbound.rest;

import com.forgesync.factoryapi.toolpath.application.FindObservedToolpath;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(
    name = "forgesync.replay.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/observed-toolpath")
public final class ObservedToolpathController {
  public static final String MEDIA_TYPE = "application/vnd.forgesync.observed-toolpath.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private final FindObservedToolpath findToolpath;

  public ObservedToolpathController(FindObservedToolpath findToolpath) {
    this.findToolpath = findToolpath;
  }

  @GetMapping(produces = MEDIA_TYPE)
  public ResponseEntity<ObservedToolpathResponse> find(
      @PathVariable String machineId,
      @RequestParam UUID replaySessionId,
      @RequestParam long startSequence,
      @RequestParam long endSequence,
      @RequestParam long throughReplaySequence) {
    if (!MACHINE_ID.matcher(machineId).matches()
        || startSequence < 0
        || endSequence < startSequence
        || throughReplaySequence < startSequence) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "invalid observed toolpath request");
    }
    long effectiveEnd = Math.min(endSequence, throughReplaySequence);
    var path =
        findToolpath.find(
            machineId, replaySessionId, startSequence, endSequence, throughReplaySequence);
    var points =
        path.points().stream()
            .map(
                point ->
                    new ObservedToolpathResponse.Point(
                        point.replaySequence(),
                        point.sourceObservedAt(),
                        point.coordinatesMillimeters(),
                        point.sourceObservations().stream()
                            .map(
                                source ->
                                    new ObservedToolpathResponse.SourceObservation(
                                        source.axis(),
                                        source.replaySequence(),
                                        source.sourceObservedAt(),
                                        source.sourceDataItemId(),
                                        source.sourceSetId(),
                                        source.artifactId(),
                                        source.rawRecordId(),
                                        source.mappingVersion()))
                            .toList()))
            .toList();
    var envelope =
        path.observedEnvelope() == null
            ? null
            : new ObservedToolpathResponse.Envelope(
                path.observedEnvelope().minimumMillimeters(),
                path.observedEnvelope().maximumMillimeters());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(
            new ObservedToolpathResponse(
                "1.0.0",
                machineId,
                replaySessionId,
                startSequence,
                effectiveEnd,
                path.availability().name(),
                path.reason(),
                "OBSERVED_PATH",
                points,
                envelope));
  }
}

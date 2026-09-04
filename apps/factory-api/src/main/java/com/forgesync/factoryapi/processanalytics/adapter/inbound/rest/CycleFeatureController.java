package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.FindCycleFeatures;
import com.forgesync.factoryapi.processanalytics.application.ProcessCycleFeatures;
import com.forgesync.factoryapi.processanalytics.application.ProcessCycleFeaturesCommand;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureExtractor;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/cycle-features")
public final class CycleFeatureController {
  public static final String MEDIA_TYPE = "application/vnd.forgesync.cycle-features.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern HASH_ID = Pattern.compile("sha256:[0-9a-f]{64}");

  private final ProcessCycleFeatures processCycleFeatures;
  private final FindCycleFeatures findCycleFeatures;
  private final CycleFeatureResponseMapper responseMapper = new CycleFeatureResponseMapper();

  public CycleFeatureController(
      ProcessCycleFeatures processCycleFeatures, FindCycleFeatures findCycleFeatures) {
    this.processCycleFeatures = Objects.requireNonNull(processCycleFeatures);
    this.findCycleFeatures = Objects.requireNonNull(findCycleFeatures);
  }

  @PostMapping(
      path = "/processing-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MEDIA_TYPE)
  ResponseEntity<CycleFeatureResponse> process(
      @PathVariable String machineId, @RequestBody ProcessingRequest request) {
    requireMachineId(machineId);
    if (request.machiningRunProcessingRunId() == null
        || !HASH_ID.matcher(request.machiningRunProcessingRunId()).matches()) {
      throw new InvalidCycleFeatureRequestException("machiningRunProcessingRunId is invalid");
    }
    if (!CycleFeatureExtractor.FEATURE_VERSION.equals(request.cycleFeatureVersion())) {
      throw new InvalidCycleFeatureRequestException("Unsupported cycleFeatureVersion");
    }
    var result =
        processCycleFeatures.process(
            new ProcessCycleFeaturesCommand(
                machineId, request.machiningRunProcessingRunId(), request.cycleFeatureVersion()));
    return ResponseEntity.status(result.isCreated() ? 201 : 200)
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(result));
  }

  @GetMapping(produces = MEDIA_TYPE)
  ResponseEntity<CycleFeatureResponse> find(
      @PathVariable String machineId, @RequestParam String featureProcessingRunId) {
    requireMachineId(machineId);
    if (!HASH_ID.matcher(featureProcessingRunId).matches()) {
      throw new InvalidCycleFeatureRequestException("featureProcessingRunId is invalid");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(responseMapper.map(findCycleFeatures.find(machineId, featureProcessingRunId)));
  }

  private static void requireMachineId(String machineId) {
    if (!MACHINE_ID.matcher(machineId).matches()) {
      throw new InvalidCycleFeatureRequestException("machineId is invalid");
    }
  }

  public record ProcessingRequest(String machiningRunProcessingRunId, String cycleFeatureVersion) {}
}

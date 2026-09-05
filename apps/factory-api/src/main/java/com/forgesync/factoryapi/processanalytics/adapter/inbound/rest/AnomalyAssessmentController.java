package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.FindAnomalyAssessments;
import com.forgesync.factoryapi.processanalytics.application.ProcessAnomalyAssessments;
import com.forgesync.factoryapi.processanalytics.application.ProcessAnomalyAssessmentsCommand;
import com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessmentPolicy;
import com.forgesync.factoryapi.processanalytics.domain.CycleBaselinePolicy;
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
@RequestMapping("/api/v1/machines/{machineId}/anomaly-assessments")
public final class AnomalyAssessmentController {
  public static final String MEDIA_TYPE = "application/vnd.forgesync.anomaly-assessments.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern HASH_ID = Pattern.compile("sha256:[0-9a-f]{64}");
  private final ProcessAnomalyAssessments processor;
  private final FindAnomalyAssessments finder;
  private final AnomalyAssessmentResponseMapper mapper = new AnomalyAssessmentResponseMapper();

  public AnomalyAssessmentController(
      ProcessAnomalyAssessments processor, FindAnomalyAssessments finder) {
    this.processor = processor;
    this.finder = finder;
  }

  @PostMapping(
      path = "/processing-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MEDIA_TYPE)
  ResponseEntity<AnomalyAssessmentResponse> process(
      @PathVariable String machineId, @RequestBody ProcessingRequest request) {
    requireMachineId(machineId);
    if (request.cycleFeatureProcessingRunId() == null
        || !HASH_ID.matcher(request.cycleFeatureProcessingRunId()).matches()) {
      throw new InvalidAnomalyAssessmentRequestException("cycleFeatureProcessingRunId is invalid");
    }
    if (!CycleBaselinePolicy.POLICY_VERSION.equals(request.baselinePolicyVersion())) {
      throw new InvalidAnomalyAssessmentRequestException("Unsupported baselinePolicyVersion");
    }
    if (!AnomalyAssessmentPolicy.POLICY_VERSION.equals(request.anomalyAssessmentVersion())) {
      throw new InvalidAnomalyAssessmentRequestException("Unsupported anomalyAssessmentVersion");
    }
    var result =
        processor.process(
            new ProcessAnomalyAssessmentsCommand(
                machineId,
                request.cycleFeatureProcessingRunId(),
                request.baselinePolicyVersion(),
                request.anomalyAssessmentVersion()));
    return ResponseEntity.status(result.isCreated() ? 201 : 200)
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(mapper.map(result));
  }

  @GetMapping(produces = MEDIA_TYPE)
  ResponseEntity<AnomalyAssessmentResponse> find(
      @PathVariable String machineId, @RequestParam String assessmentProcessingRunId) {
    requireMachineId(machineId);
    if (!HASH_ID.matcher(assessmentProcessingRunId).matches()) {
      throw new InvalidAnomalyAssessmentRequestException("assessmentProcessingRunId is invalid");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(MEDIA_TYPE))
        .body(mapper.map(finder.find(machineId, assessmentProcessingRunId)));
  }

  private static void requireMachineId(String machineId) {
    if (!MACHINE_ID.matcher(machineId).matches()) {
      throw new InvalidAnomalyAssessmentRequestException("machineId is invalid");
    }
  }

  public record ProcessingRequest(
      String cycleFeatureProcessingRunId,
      String baselinePolicyVersion,
      String anomalyAssessmentVersion) {}
}

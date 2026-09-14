package com.forgesync.factoryapi.production.adapter.inbound.rest;

import com.forgesync.factoryapi.production.application.ObservedProductionContextService;
import com.forgesync.factoryapi.production.application.ProductionContextInputNotFoundException;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/production-context")
public final class ObservedProductionContextController {
  public static final String MEDIA_TYPE =
      "application/vnd.forgesync.observed-production-context.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern HASH_ID = Pattern.compile("sha256:[0-9a-f]{64}");
  private final ObservedProductionContextService service;
  private final ObservedProductionContextResponseMapper mapper =
      new ObservedProductionContextResponseMapper();

  public ObservedProductionContextController(ObservedProductionContextService service) {
    this.service = service;
  }

  @GetMapping(produces = MEDIA_TYPE)
  ResponseEntity<ObservedProductionContextResponse> find(
      @PathVariable String machineId,
      @RequestParam String machiningRunProcessingRunId,
      @RequestParam(defaultValue = "1.0.0") String ruleVersion) {
    if (!MACHINE_ID.matcher(machineId).matches()
        || !HASH_ID.matcher(machiningRunProcessingRunId).matches()) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid identity");
    }
    try {
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(MEDIA_TYPE))
          .body(mapper.map(service.find(machineId, machiningRunProcessingRunId, ruleVersion)));
    } catch (ProductionContextInputNotFoundException missing) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND,
          "Production context input not found",
          missing);
    }
  }
}

package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.ToolLoadTrendInputNotFoundException;
import com.forgesync.factoryapi.processanalytics.application.ToolLoadTrendService;
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
    name = "forgesync.process-analytics.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/tool-load-trends")
public final class ToolLoadTrendController {
  public static final String MEDIA_TYPE = "application/vnd.forgesync.tool-load-trends.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern HASH_ID = Pattern.compile("sha256:[0-9a-f]{64}");
  private final ToolLoadTrendService service;
  private final ToolLoadTrendResponseMapper mapper = new ToolLoadTrendResponseMapper();

  public ToolLoadTrendController(ToolLoadTrendService service) {
    this.service = service;
  }

  @GetMapping(produces = MEDIA_TYPE)
  ResponseEntity<ToolLoadTrendResponse> find(
      @PathVariable String machineId,
      @RequestParam String machiningRunProcessingRunId,
      @RequestParam(defaultValue = "1.0.0") String policyVersion) {
    if (!MACHINE_ID.matcher(machineId).matches()
        || !HASH_ID.matcher(machiningRunProcessingRunId).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid identity");
    }
    try {
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(MEDIA_TYPE))
          .body(mapper.map(service.find(machineId, machiningRunProcessingRunId, policyVersion)));
    } catch (ToolLoadTrendInputNotFoundException missing) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Tool load trend input not found", missing);
    } catch (IllegalArgumentException invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
    }
  }
}

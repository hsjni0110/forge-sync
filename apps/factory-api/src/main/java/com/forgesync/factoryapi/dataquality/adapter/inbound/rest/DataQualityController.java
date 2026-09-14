package com.forgesync.factoryapi.dataquality.adapter.inbound.rest;

import com.forgesync.factoryapi.dataquality.application.DataQualityReport;
import com.forgesync.factoryapi.dataquality.application.GetDataQualityReport;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(
    name = "forgesync.twin.query.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines/{machineId}/data-quality")
public final class DataQualityController {
  public static final String MEDIA_TYPE = "application/vnd.forgesync.data-quality.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private final GetDataQualityReport getDataQualityReport;

  public DataQualityController(GetDataQualityReport getDataQualityReport) {
    this.getDataQualityReport = getDataQualityReport;
  }

  @GetMapping(produces = MEDIA_TYPE)
  public DataQualityReport get(
      @PathVariable String machineId,
      @RequestParam(required = false) UUID replaySessionId,
      @RequestParam(required = false) Long throughReplaySequence) {
    if (!MACHINE_ID.matcher(machineId).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "machineId is invalid");
    }
    if ((replaySessionId == null) != (throughReplaySequence == null)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "replaySessionId and throughReplaySequence must be provided together");
    }
    if (throughReplaySequence != null && throughReplaySequence < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "throughReplaySequence must not be negative");
    }
    return replaySessionId == null
        ? getDataQualityReport.getSource(machineId)
        : getDataQualityReport.getScoped(machineId, replaySessionId, throughReplaySequence);
  }
}

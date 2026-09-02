package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.application.GetOperationalTwinSnapshot;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
    name = "forgesync.twin.query.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequestMapping("/api/v1/machines")
public final class TwinSnapshotController {

  public static final String TWIN_MEDIA_TYPE = "application/vnd.forgesync.twin.v1+json";
  private static final Pattern MACHINE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  private final GetOperationalTwinSnapshot getOperationalTwinSnapshot;
  private final TwinSnapshotResponseMapper responseMapper = new TwinSnapshotResponseMapper();

  public TwinSnapshotController(GetOperationalTwinSnapshot getOperationalTwinSnapshot) {
    this.getOperationalTwinSnapshot = Objects.requireNonNull(getOperationalTwinSnapshot);
  }

  @GetMapping(path = "/{machineId}/twin", produces = TWIN_MEDIA_TYPE)
  public ResponseEntity<TwinSnapshotResponse> getTwinSnapshot(@PathVariable String machineId) {
    if (!MACHINE_ID.matcher(machineId).matches()) {
      throw new InvalidMachineIdException();
    }
    return ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.parseMediaType(TWIN_MEDIA_TYPE))
        .body(responseMapper.map(getOperationalTwinSnapshot.getSnapshot(machineId)));
  }
}

package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentState;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record LoadedTwinProjection(
    String machineId,
    TwinVersion twinVersion,
    Instant projectedAt,
    ReplayCursor replayCursor,
    EquipmentState equipmentState,
    TwinVersion equipmentStateVersion,
    List<ProjectedTwinObservation> observations) {

  public LoadedTwinProjection(
      String machineId,
      TwinVersion twinVersion,
      Instant projectedAt,
      EquipmentState equipmentState,
      TwinVersion equipmentStateVersion,
      List<ProjectedTwinObservation> observations) {
    this(
        machineId,
        twinVersion,
        projectedAt,
        new ReplayCursor(new java.util.UUID(0, 0), 0, projectedAt, projectedAt, twinVersion),
        equipmentState,
        equipmentStateVersion,
        observations);
  }

  public LoadedTwinProjection {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(twinVersion, "twinVersion");
    Objects.requireNonNull(projectedAt, "projectedAt");
    Objects.requireNonNull(replayCursor, "replayCursor");
    Objects.requireNonNull(equipmentState, "equipmentState");
    Objects.requireNonNull(equipmentStateVersion, "equipmentStateVersion");
    observations = List.copyOf(observations);
  }
}

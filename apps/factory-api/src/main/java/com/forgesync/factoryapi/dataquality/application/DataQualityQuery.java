package com.forgesync.factoryapi.dataquality.application;

import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.DerivedProcessSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.RuntimeSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface DataQualityQuery {
  Optional<RuntimeSnapshot> findRuntime(
      String machineId, UUID replaySessionId, long throughReplaySequence);

  Optional<DerivedProcessSnapshot> findDerivedProcess(
      String machineId, UUID replaySessionId, long throughReplaySequence);
}

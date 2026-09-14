package com.forgesync.factoryapi.dataquality.application;

import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.FreshnessSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.GetOperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.MachineTwinNotFoundException;
import com.forgesync.factoryapi.equipmenttwin.application.TwinSnapshotUnavailableException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DataQualityService implements GetDataQualityReport {
  private final SourceQualityEvidence source;
  private final DataQualityQuery query;
  private final GetOperationalTwinSnapshot twinQuery;
  private final Clock clock;
  private final DataQualityReportAssembler assembler;

  public DataQualityService(
      SourceQualityEvidence source,
      DataQualityQuery query,
      GetOperationalTwinSnapshot twinQuery,
      Clock clock) {
    this.source = Objects.requireNonNull(source);
    this.query = Objects.requireNonNull(query);
    this.twinQuery = Objects.requireNonNull(twinQuery);
    this.clock = Objects.requireNonNull(clock);
    this.assembler = new DataQualityReportAssembler();
  }

  @Override
  public DataQualityReport getSource(String machineId) {
    requireMachine(machineId);
    return assembler.assemble(
        source,
        null,
        null,
        Optional.empty(),
        Optional.empty(),
        freshness(machineId),
        clock.instant());
  }

  @Override
  public DataQualityReport getScoped(
      String machineId, UUID replaySessionId, long throughReplaySequence) {
    requireMachine(machineId);
    Objects.requireNonNull(replaySessionId);
    if (throughReplaySequence < 0) {
      throw new IllegalArgumentException("throughReplaySequence must not be negative");
    }
    return assembler.assemble(
        source,
        replaySessionId,
        throughReplaySequence,
        query.findRuntime(machineId, replaySessionId, throughReplaySequence),
        query.findDerivedProcess(machineId, replaySessionId, throughReplaySequence),
        freshness(machineId),
        clock.instant());
  }

  private Optional<FreshnessSnapshot> freshness(String machineId) {
    try {
      var snapshot = twinQuery.getSnapshot(machineId);
      return Optional.of(
          new FreshnessSnapshot(
              snapshot.freshness().name(),
              snapshot.age().toMillis(),
              snapshot.freshMaxAgeMillis(),
              snapshot.laggingMaxAgeMillis()));
    } catch (MachineTwinNotFoundException | TwinSnapshotUnavailableException exception) {
      return Optional.empty();
    }
  }

  private void requireMachine(String machineId) {
    if (!source.machineId().equals(machineId)) {
      throw new MachineTwinNotFoundException(machineId);
    }
  }
}

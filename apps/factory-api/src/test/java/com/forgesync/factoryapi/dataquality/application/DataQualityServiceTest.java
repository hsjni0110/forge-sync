package com.forgesync.factoryapi.dataquality.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgesync.factoryapi.dataquality.adapter.outbound.resource.ClasspathDataQualityEvidence;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.DerivedProcessSnapshot;
import com.forgesync.factoryapi.dataquality.application.DataQualitySnapshots.RuntimeSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.TwinSnapshotUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataQualityServiceTest {

  @Test
  void keepsEvidenceAvailableWhenTwinFreshnessIsTemporarilyUnavailable() {
    var source = new ClasspathDataQualityEvidence().load();
    var service =
        new DataQualityService(
            source,
            new EmptyDataQualityQuery(),
            machineId -> {
              throw new TwinSnapshotUnavailableException("versions are converging");
            },
            Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC));

    var report = service.getSource("Mazak01");

    assertThat(report.dimensions().freshness().status()).isEqualTo("NOT_EVALUATED");
    assertThat(report.dimensions().semanticCoverage().ratio()).isNotNull();
  }

  private static final class EmptyDataQualityQuery implements DataQualityQuery {
    @Override
    public Optional<RuntimeSnapshot> findRuntime(
        String machineId, UUID replaySessionId, long throughReplaySequence) {
      return Optional.empty();
    }

    @Override
    public Optional<DerivedProcessSnapshot> findDerivedProcess(
        String machineId, UUID replaySessionId, long throughReplaySequence) {
      return Optional.empty();
    }
  }
}

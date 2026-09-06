package com.forgesync.factoryapi.toolchange.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ToolChangePolicy {
  public List<ToolChange> detect(List<ToolNumberObservation> observations) {
    var ordered =
        observations.stream()
            .sorted(
                Comparator.comparingLong(ToolNumberObservation::replaySequence)
                    .thenComparing(ToolNumberObservation::sourceObservedAt))
            .toList();
    var changes = new ArrayList<ToolChange>();
    Long previous = null;
    for (var observation : ordered) {
      if (!observation.isAvailable()) {
        previous = null;
        continue;
      }
      if (previous != null && previous.longValue() != observation.value().longValue()) {
        changes.add(
            new ToolChange(
                observation.replaySessionId(),
                observation.replaySequence(),
                observation.sourceObservedAt(),
                previous,
                observation.value(),
                observation.sourceDataItemId(),
                observation.sourceSetId(),
                observation.artifactId(),
                observation.rawRecordId(),
                observation.mappingVersion()));
      }
      previous = observation.value();
    }
    return List.copyOf(changes);
  }
}

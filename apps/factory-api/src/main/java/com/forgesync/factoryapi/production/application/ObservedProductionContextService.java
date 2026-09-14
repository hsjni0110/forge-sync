package com.forgesync.factoryapi.production.application;

import com.forgesync.factoryapi.production.domain.ObservedProductionContext;
import com.forgesync.factoryapi.production.domain.ObservedProductionContextPolicy;
import java.util.Objects;

public final class ObservedProductionContextService {
  private final ObservedProductionContextSource source;
  private final ObservedProductionContextPolicy policy;

  public ObservedProductionContextService(
      ObservedProductionContextSource source, ObservedProductionContextPolicy policy) {
    this.source = Objects.requireNonNull(source);
    this.policy = Objects.requireNonNull(policy);
  }

  public ObservedProductionContext find(
      String machineId, String machiningRunProcessingRunId, String ruleVersion) {
    if (!ObservedProductionContextPolicy.RULE_VERSION.equals(ruleVersion)) {
      throw new IllegalArgumentException("Unsupported production context rule version");
    }
    var input = source.read(machineId, machiningRunProcessingRunId);
    if (!machineId.equals(input.machineId())) {
      throw new IllegalStateException(
          "Machining Run processing identity belongs to another machine");
    }
    return policy.project(
        input.machineId(),
        input.replaySessionId(),
        input.throughReplaySequence(),
        input.machiningRunProcessingRunId(),
        input.programs(),
        input.machiningRuns(),
        input.partCounts());
  }
}

package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.ToolLoadTrendPolicy;
import com.forgesync.factoryapi.processanalytics.domain.ToolLoadTrendReport;
import java.util.Objects;

public final class ToolLoadTrendService {
  private final ToolLoadTrendSource source;
  private final ToolLoadTrendPolicy policy;

  public ToolLoadTrendService(ToolLoadTrendSource source, ToolLoadTrendPolicy policy) {
    this.source = Objects.requireNonNull(source);
    this.policy = Objects.requireNonNull(policy);
  }

  public ToolLoadTrendReport find(
      String machineId, String machiningRunProcessingRunId, String policyVersion) {
    if (!ToolLoadTrendPolicy.POLICY_VERSION.equals(policyVersion)) {
      throw new IllegalArgumentException("Unsupported tool load trend policy version");
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
        input.machiningRuns(),
        input.toolNumbers(),
        input.loads());
  }
}

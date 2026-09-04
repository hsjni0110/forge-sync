package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Reconstructs machine-level runs without changing or guessing Canonical Observations. */
public final class MachiningRunSegmentationPolicy {

  public static final String RULE_VERSION = "1.0.0";
  private static final Duration SPINDLE_ZERO_FALLBACK = Duration.ofSeconds(30);

  public List<MachiningRun> segment(
      String processingRunId,
      String segmentationRuleVersion,
      List<ProcessObservation> inputObservations) {
    requireSupportedVersion(segmentationRuleVersion);
    Objects.requireNonNull(processingRunId, "processingRunId");
    List<ProcessObservation> observations = orderedCopy(inputObservations);
    if (observations.isEmpty()) {
      return List.of();
    }
    requireOneSource(observations);

    List<MachiningRun> runs = new ArrayList<>();
    CurrentRun current = null;
    ProcessObservation pendingAnchor = null;
    String currentProgram = null;
    ProcessObservation currentProgramEvidence = null;
    String previousExecution = null;
    boolean executionSeen = false;
    boolean hasExecutionGap = false;

    for (ProcessObservation observation : observations) {
      if (current != null) {
        current.observe(observation);
      }
      switch (observation.signal()) {
        case PROGRAM -> {
          if (observation.isAvailable()) {
            String observedProgram = observation.textValue();
            if (current == null) {
              currentProgram = observedProgram;
              currentProgramEvidence = observation;
              pendingAnchor = observation;
            } else {
              current.observeProgram(observedProgram, observation);
              currentProgram = observedProgram;
              currentProgramEvidence = observation;
            }
          } else {
            currentProgram = null;
            currentProgramEvidence = null;
            if (current != null) {
              current.observeProgramUnavailable(observation);
            }
          }
        }
        case EXECUTION -> {
          if (!observation.isAvailable()) {
            if (current != null) {
              runs.add(current.finish(MachiningRunStatus.INTERRUPTED, observation));
              current = null;
            }
            previousExecution = null;
            hasExecutionGap = true;
          } else {
            String execution = observation.textValue().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(execution)) {
              if (current == null) {
                boolean uncertainStart =
                    !executionSeen || hasExecutionGap || isRunActive(previousExecution);
                current =
                    CurrentRun.start(
                        processingRunId,
                        segmentationRuleVersion,
                        observation,
                        currentProgram,
                        currentProgramEvidence,
                        uncertainStart);
              } else {
                current.confirmExecution(observation);
              }
              pendingAnchor = null;
            } else if ("READY".equals(execution) && current != null) {
              MachiningRunStatus status =
                  current.hasUncertainStart()
                      ? MachiningRunStatus.UNKNOWN
                      : MachiningRunStatus.COMPLETED;
              runs.add(current.finish(status, observation));
              current = null;
            } else if ("STOPPED".equals(execution) && current != null) {
              MachiningRunStatus status =
                  current.hasUncertainStart()
                      ? MachiningRunStatus.UNKNOWN
                      : MachiningRunStatus.ABORTED;
              runs.add(current.finish(status, observation));
              current = null;
            }
            previousExecution = execution;
            executionSeen = true;
            hasExecutionGap = false;
          }
        }
        case SPINDLE_SPEED -> {
          if (observation.isAvailable()
              && observation.numericValue().compareTo(BigDecimal.ZERO) > 0) {
            if (current == null && !executionSeen) {
              current =
                  CurrentRun.start(
                      processingRunId,
                      segmentationRuleVersion,
                      observation,
                      currentProgram,
                      currentProgramEvidence,
                      true);
            }
            if (current != null) {
              current.observePositiveSpindle(observation);
            }
            pendingAnchor = null;
          } else if (current != null
              && observation.isAvailable()
              && observation.numericValue().compareTo(BigDecimal.ZERO) == 0
              && !executionSeen
              && current.observeZeroSpindle(observation, SPINDLE_ZERO_FALLBACK)) {
            runs.add(current.finish(MachiningRunStatus.INTERRUPTED, observation));
            current = null;
          }
        }
      }
    }

    if (current != null) {
      runs.add(current.finish(MachiningRunStatus.INTERRUPTED, null));
    } else if (pendingAnchor != null) {
      runs.add(
          CurrentRun.start(
                  processingRunId,
                  segmentationRuleVersion,
                  pendingAnchor,
                  currentProgram,
                  currentProgramEvidence,
                  true)
              .finish(MachiningRunStatus.UNKNOWN, null));
    }
    return List.copyOf(runs);
  }

  private static void requireSupportedVersion(String version) {
    if (!RULE_VERSION.equals(version)) {
      throw new IllegalArgumentException("Unsupported segmentation rule version: " + version);
    }
  }

  private static List<ProcessObservation> orderedCopy(List<ProcessObservation> observations) {
    Objects.requireNonNull(observations, "observations");
    return observations.stream()
        .sorted(
            Comparator.comparingLong(ProcessObservation::replaySequence)
                .thenComparing(ProcessObservation::sourceObservedAt)
                .thenComparing(ProcessObservation::sourceEventKey))
        .toList();
  }

  private static void requireOneSource(List<ProcessObservation> observations) {
    ProcessObservation first = observations.getFirst();
    boolean mixed =
        observations.stream()
            .anyMatch(
                observation ->
                    !first.machineId().equals(observation.machineId())
                        || !first.replaySessionId().equals(observation.replaySessionId()));
    if (mixed) {
      throw new IllegalArgumentException("A segmentation input must have one machine and session");
    }
  }

  private static boolean isRunActive(String execution) {
    return "ACTIVE".equals(execution)
        || "FEED_HOLD".equals(execution)
        || "INTERRUPTED".equals(execution);
  }

  private static final class CurrentRun {
    private final String processingRunId;
    private final String ruleVersion;
    private final ProcessObservation start;
    private final ProcessObservation rangeStart;
    private final boolean uncertainStart;
    private final List<BoundaryEvidence> supportingEvidence = new ArrayList<>();
    private String programName;
    private ProcessObservation last;
    private ProcessObservation firstZeroSpindle;
    private boolean positiveSpindleSeen;
    private boolean programConflict;

    private CurrentRun(
        String processingRunId,
        String ruleVersion,
        ProcessObservation start,
        String programName,
        ProcessObservation programEvidence,
        boolean uncertainStart) {
      this.processingRunId = processingRunId;
      this.ruleVersion = ruleVersion;
      this.start = start;
      this.rangeStart =
          programEvidence != null && programEvidence.replaySequence() < start.replaySequence()
              ? programEvidence
              : start;
      this.last = start;
      this.programName = programName;
      this.uncertainStart = uncertainStart;
      if (programName != null && programEvidence != null) {
        supportingEvidence.add(BoundaryEvidence.from("PROGRAM", programEvidence));
      }
    }

    static CurrentRun start(
        String processingRunId,
        String ruleVersion,
        ProcessObservation start,
        String programName,
        ProcessObservation programEvidence,
        boolean uncertainStart) {
      return new CurrentRun(
          processingRunId, ruleVersion, start, programName, programEvidence, uncertainStart);
    }

    void observe(ProcessObservation observation) {
      last = observation;
    }

    void confirmExecution(ProcessObservation observation) {
      addEvidenceOnce("EXECUTION_CONFIRMED", observation);
    }

    void observeProgram(String observedProgram, ProcessObservation observation) {
      if (programName == null) {
        programName = observedProgram;
        addEvidenceOnce("PROGRAM", observation);
      } else if (!programName.equals(observedProgram)) {
        programConflict = true;
        addEvidenceOnce("PROGRAM_CONFLICT", observation);
      }
    }

    void observeProgramUnavailable(ProcessObservation observation) {
      programConflict = true;
      addEvidenceOnce("PROGRAM_UNAVAILABLE", observation);
    }

    void observePositiveSpindle(ProcessObservation observation) {
      positiveSpindleSeen = true;
      firstZeroSpindle = null;
      addEvidenceOnce("POSITIVE_SPINDLE", observation);
    }

    boolean observeZeroSpindle(ProcessObservation observation, Duration minimumGap) {
      if (firstZeroSpindle == null) {
        firstZeroSpindle = observation;
        return false;
      }
      Duration zeroDuration =
          Duration.between(firstZeroSpindle.sourceObservedAt(), observation.sourceObservedAt());
      return !zeroDuration.isNegative() && zeroDuration.compareTo(minimumGap) >= 0;
    }

    boolean hasUncertainStart() {
      return uncertainStart;
    }

    MachiningRun finish(MachiningRunStatus status, ProcessObservation end) {
      ProcessObservation rangeEnd = end == null ? last : end;
      BoundaryEvidence startEvidence = BoundaryEvidence.from("START", start);
      BoundaryEvidence endEvidence = end == null ? null : BoundaryEvidence.from("END", end);
      SegmentationConfidence confidence = confidence(status);
      List<String> reasons = confidenceReasons(status);
      String machiningRunId =
          DeterministicHash.sha256(
              processingRunId
                  + "\n"
                  + start.machineId()
                  + "\n"
                  + ruleVersion
                  + "\n"
                  + start.sourceEventKey());
      String resultMaterial =
          machiningRunId
              + "\n"
              + status
              + "\n"
              + Objects.toString(programName, "")
              + "\n"
              + start.sourceObservedAt()
              + "\n"
              + (end == null ? "" : end.sourceObservedAt())
              + "\n"
              + rangeEnd.sourceEventKey()
              + "\n"
              + confidence
              + "\n"
              + String.join(",", reasons);
      return new MachiningRun(
          machiningRunId,
          processingRunId,
          ruleVersion,
          start.machineId(),
          status,
          programName,
          start.sourceObservedAt(),
          end == null ? null : end.sourceObservedAt(),
          confidence,
          reasons,
          new ObservationRange(
              rangeStart.replaySessionId(),
              rangeStart.replaySequence(),
              rangeEnd.replaySequence(),
              rangeStart.sourceObservedAt(),
              rangeEnd.sourceObservedAt(),
              rangeStart.sourceEventKey(),
              rangeEnd.sourceEventKey()),
          startEvidence,
          endEvidence,
          supportingEvidence,
          DeterministicHash.sha256(resultMaterial));
    }

    private SegmentationConfidence confidence(MachiningRunStatus status) {
      if (status == MachiningRunStatus.UNKNOWN || status == MachiningRunStatus.INTERRUPTED) {
        return SegmentationConfidence.LOW;
      }
      if (programName != null && positiveSpindleSeen && !programConflict) {
        return SegmentationConfidence.HIGH;
      }
      return SegmentationConfidence.MEDIUM;
    }

    private List<String> confidenceReasons(MachiningRunStatus status) {
      List<String> reasons = new ArrayList<>();
      reasons.add(uncertainStart ? "START_BOUNDARY_UNCERTAIN" : "EXECUTION_START_CONFIRMED");
      reasons.add(programName == null ? "PROGRAM_MISSING" : "PROGRAM_OBSERVED");
      reasons.add(positiveSpindleSeen ? "POSITIVE_SPINDLE_OBSERVED" : "POSITIVE_SPINDLE_MISSING");
      if (programConflict) {
        reasons.add("PROGRAM_CHANGED_DURING_RUN");
      }
      if (status == MachiningRunStatus.INTERRUPTED) {
        reasons.add("END_BOUNDARY_INCOMPLETE");
      }
      return List.copyOf(reasons);
    }

    private void addEvidenceOnce(String role, ProcessObservation observation) {
      boolean alreadyPresent =
          supportingEvidence.stream().anyMatch(item -> role.equals(item.role()));
      if (!alreadyPresent) {
        supportingEvidence.add(BoundaryEvidence.from(role, observation));
      }
    }
  }
}

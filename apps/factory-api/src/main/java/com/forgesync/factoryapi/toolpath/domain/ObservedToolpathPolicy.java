package com.forgesync.factoryapi.toolpath.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ObservedToolpathPolicy {
  private static final List<String> AXES = List.of("X", "Y", "Z");
  private final long minimumIntervalMillis;
  private final int maximumPoints;

  public ObservedToolpathPolicy(long minimumIntervalMillis, int maximumPoints) {
    if (minimumIntervalMillis < 0 || maximumPoints < 2) {
      throw new IllegalArgumentException("Toolpath bounds must be positive");
    }
    this.minimumIntervalMillis = minimumIntervalMillis;
    this.maximumPoints = maximumPoints;
  }

  public ObservedToolpath build(
      List<AxisPositionObservation> observations, long startSequence, long endSequence) {
    if (startSequence < 0 || endSequence < startSequence) {
      throw new IllegalArgumentException("Invalid selected run sequence range");
    }
    Map<String, AxisPositionObservation> latest = new HashMap<>();
    List<ToolpathPoint> candidates = new ArrayList<>();
    var ordered =
        observations.stream()
            .sorted(
                Comparator.comparingLong(AxisPositionObservation::replaySequence)
                    .thenComparing(AxisPositionObservation::sourceObservedAt)
                    .thenComparing(AxisPositionObservation::sourceDataItemId)
                    .thenComparing(AxisPositionObservation::rawRecordId))
            .filter(observation -> observation.replaySequence() <= endSequence)
            .toList();
    for (var observation : ordered) {
      if (!AXES.contains(observation.axis())) continue;
      if (!isAvailable(observation)) {
        latest.remove(observation.axis());
        candidates.clear();
        continue;
      }
      latest.put(observation.axis(), observation);
      if (observation.replaySequence() >= startSequence && latest.keySet().containsAll(AXES)) {
        candidates.add(point(observation, latest));
      }
    }
    if (candidates.size() < 2) {
      String missing =
          AXES.stream()
              .filter(axis -> !latest.containsKey(axis))
              .reduce((a, b) -> a + ", " + b)
              .orElse("경로 점 부족");
      return new ObservedToolpath(
          ToolpathAvailability.UNAVAILABLE, missing + " · 관측 경로 unavailable", List.of(), null);
    }
    List<ToolpathPoint> points = decimate(candidates);
    if (points.size() > maximumPoints) {
      points = new ArrayList<>(points.subList(points.size() - maximumPoints, points.size()));
    }
    return new ObservedToolpath(
        ToolpathAvailability.AVAILABLE, null, List.copyOf(points), envelope(points));
  }

  private boolean isAvailable(AxisPositionObservation observation) {
    return "AVAILABLE".equals(observation.availability())
        && "MILLIMETER".equals(observation.unit())
        && observation.value() != null
        && Double.isFinite(observation.value());
  }

  private static ToolpathPoint point(
      AxisPositionObservation event, Map<String, AxisPositionObservation> latest) {
    List<AxisPositionObservation> sources = AXES.stream().map(latest::get).toList();
    return new ToolpathPoint(
        event.replaySequence(),
        event.sourceObservedAt(),
        sources.stream().map(AxisPositionObservation::value).toList(),
        sources);
  }

  private List<ToolpathPoint> decimate(List<ToolpathPoint> candidates) {
    List<ToolpathPoint> points = new ArrayList<>();
    for (ToolpathPoint candidate : candidates) {
      if (points.isEmpty()
          || Duration.between(points.getLast().sourceObservedAt(), candidate.sourceObservedAt())
                  .toMillis()
              >= minimumIntervalMillis) {
        points.add(candidate);
      }
    }
    ToolpathPoint last = candidates.getLast();
    if (points.getLast().replaySequence() != last.replaySequence()) points.add(last);
    return points;
  }

  private static ObservedEnvelope envelope(List<ToolpathPoint> points) {
    List<Double> minimum = new ArrayList<>(points.getFirst().coordinatesMillimeters());
    List<Double> maximum = new ArrayList<>(minimum);
    for (ToolpathPoint point : points) {
      for (int index = 0; index < 3; index++) {
        minimum.set(index, Math.min(minimum.get(index), point.coordinatesMillimeters().get(index)));
        maximum.set(index, Math.max(maximum.get(index), point.coordinatesMillimeters().get(index)));
      }
    }
    return new ObservedEnvelope(List.copyOf(minimum), List.copyOf(maximum));
  }
}

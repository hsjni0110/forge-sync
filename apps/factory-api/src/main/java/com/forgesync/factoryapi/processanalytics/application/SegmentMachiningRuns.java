package com.forgesync.factoryapi.processanalytics.application;

public interface SegmentMachiningRuns {
  MachiningRunProcessingResult segment(SegmentMachiningRunsCommand command);
}

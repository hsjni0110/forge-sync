package com.forgesync.factoryapi.dataquality.application;

import java.util.List;

public record SourceQualityEvidence(
    String machineId,
    String sourceSetId,
    long totalRecords,
    long invalidRecords,
    long orderingAnomalyCount,
    long mappedRecords,
    long parsedRecords,
    String mappingVersion,
    String sourceProfileProcessingRunId,
    String semanticMappingProcessingRunId,
    String rawArtifactId,
    String sourceHref,
    List<UnmappedDataItem> unmappedDataItems) {

  public record UnmappedDataItem(
      String classification,
      String dataItemId,
      String name,
      long recordCount,
      String firstRawRecordId,
      String evidenceHref) {}
}

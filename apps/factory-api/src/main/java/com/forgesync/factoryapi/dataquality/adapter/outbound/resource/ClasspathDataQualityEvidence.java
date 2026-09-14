package com.forgesync.factoryapi.dataquality.adapter.outbound.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.dataquality.application.SourceQualityEvidence;
import com.forgesync.factoryapi.dataquality.application.SourceQualityEvidence.UnmappedDataItem;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClasspathDataQualityEvidence {
  private static final String ROOT = "data-quality/nist-mazak01-20161005/";
  private final ObjectMapper objectMapper;

  public ClasspathDataQualityEvidence() {
    this(new ObjectMapper());
  }

  public ClasspathDataQualityEvidence(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public SourceQualityEvidence load() {
    JsonNode profile = read(ROOT + "profile.json");
    JsonNode mapping = read(ROOT + "mapping-report.json");
    JsonNode records = mapping.path("records");
    long total = records.path("total").asLong();
    long invalid =
        profile.path("invalidRecords").path("count").asLong()
            + records.path("byStatus").path("INVALID_VALUE").asLong()
            + records.path("byStatus").path("INVALID_RAW_RECORD").asLong();
    String sourceHref = sourceHref(profile);
    Map<String, Long> lineByIdentity = sourceLines(profile);
    List<UnmappedDataItem> unmapped = new ArrayList<>();
    addItems(unmapped, mapping.path("unknownDataItems"), "UNKNOWN", lineByIdentity, sourceHref);
    addItems(unmapped, mapping.path("ambiguousDataItems"), "AMBIGUOUS", lineByIdentity, sourceHref);
    addItems(
        unmapped, mapping.path("unsupportedDataItems"), "UNSUPPORTED", lineByIdentity, sourceHref);
    unmapped.sort(
        Comparator.comparing(UnmappedDataItem::classification)
            .thenComparing(UnmappedDataItem::name));
    JsonNode source = mapping.path("source");
    return new SourceQualityEvidence(
        source.path("machineId").asText(),
        source.path("sourceSetId").asText(),
        total,
        invalid,
        profile.path("ordering").path("anomalyCount").asLong(),
        mapping.path("semanticCoverage").path("mappedRecords").asLong(),
        mapping.path("semanticCoverage").path("parsedRecords").asLong(),
        mapping.path("mappingVersion").asText(),
        profile.path("processingRunId").asText(),
        mapping.path("processingRunId").asText(),
        source.path("rawArtifactId").asText(),
        sourceHref,
        List.copyOf(unmapped));
  }

  private void addItems(
      List<UnmappedDataItem> target,
      JsonNode items,
      String classification,
      Map<String, Long> lineByIdentity,
      String sourceHref) {
    for (JsonNode item : items) {
      String dataItemId = item.path("dataItemId").asText(null);
      String name = item.path("name").asText();
      Long line = lineByIdentity.get(dataItemId == null ? name : dataItemId);
      target.add(
          new UnmappedDataItem(
              classification,
              dataItemId,
              name,
              item.path("recordCount").asLong(),
              item.path("firstRawRecordId").asText(),
              line == null ? sourceHref : sourceHref + "#L" + line));
    }
  }

  private static Map<String, Long> sourceLines(JsonNode profile) {
    Map<String, Long> lines = new HashMap<>();
    for (JsonNode item : profile.path("dataItems")) {
      rememberFirstLine(lines, item.path("dataItemId").asText(), item.path("sampleValues"));
    }
    rememberIssueLines(lines, profile.path("unknownDataItems"));
    rememberIssueLines(lines, profile.path("ambiguousDataItems"));
    return lines;
  }

  private static void rememberIssueLines(Map<String, Long> lines, JsonNode items) {
    for (JsonNode item : items) {
      rememberFirstLine(lines, item.path("name").asText(), item.path("samples"));
    }
  }

  private static void rememberFirstLine(Map<String, Long> lines, String key, JsonNode samples) {
    if (samples.isArray() && !samples.isEmpty()) {
      long line = samples.get(0).path("locator").path("lineNumber").asLong(0);
      if (line > 0) lines.put(key, line);
    }
  }

  private static String sourceHref(JsonNode profile) {
    JsonNode sourceSet = profile.path("sourceSet");
    String repositoryPath = null;
    for (JsonNode artifact : sourceSet.path("artifacts")) {
      if ("SHDR_RAW".equals(artifact.path("role").asText())) {
        repositoryPath = artifact.path("repositoryPath").asText();
      }
    }
    if (repositoryPath == null) throw new IllegalStateException("SHDR source evidence is missing");
    return sourceSet.path("upstreamRepository").asText()
        + "/blob/"
        + sourceSet.path("upstreamCommit").asText()
        + "/"
        + repositoryPath;
  }

  private JsonNode read(String path) {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
      if (stream == null)
        throw new IllegalStateException("Data quality evidence is missing: " + path);
      return objectMapper.readTree(stream);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read data quality evidence: " + path, exception);
    }
  }
}

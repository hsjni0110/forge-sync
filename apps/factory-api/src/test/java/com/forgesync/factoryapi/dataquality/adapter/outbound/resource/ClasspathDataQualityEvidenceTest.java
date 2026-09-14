package com.forgesync.factoryapi.dataquality.adapter.outbound.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClasspathDataQualityEvidenceTest {
  @Test
  void loadsPinnedSourceAndMappingEvidenceWithNavigableRawLocators() throws Exception {
    Class<?> type =
        Class.forName(
            "com.forgesync.factoryapi.dataquality.adapter.outbound.resource.ClasspathDataQualityEvidence");
    Object loader = type.getConstructor().newInstance();
    Object evidence = type.getMethod("load").invoke(loader);

    assertThat(read(evidence, "totalRecords")).isEqualTo(115_991L);
    assertThat(read(evidence, "invalidRecords")).isEqualTo(0L);
    assertThat(read(evidence, "orderingAnomalyCount")).isEqualTo(0L);
    assertThat(read(evidence, "mappedRecords")).isEqualTo(101_693L);
    assertThat(read(evidence, "mappingVersion")).isEqualTo("2.3.0");
    List<?> items = (List<?>) read(evidence, "unmappedDataItems");
    assertThat(items).hasSizeGreaterThan(22);
    Object firstUnknown =
        items.stream()
            .filter(
                item -> {
                  try {
                    return read(item, "classification").equals("UNKNOWN");
                  } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                  }
                })
            .findFirst()
            .orElseThrow();
    assertThat(read(firstUnknown, "firstRawRecordId").toString()).contains("#bytes=");
    assertThat(read(firstUnknown, "evidenceHref").toString()).contains("#L");
  }

  private static Object read(Object target, String methodName) throws Exception {
    Method method = target.getClass().getMethod(methodName);
    return method.invoke(target);
  }
}

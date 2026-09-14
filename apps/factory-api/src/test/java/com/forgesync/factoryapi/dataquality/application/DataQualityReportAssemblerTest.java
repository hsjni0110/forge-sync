package com.forgesync.factoryapi.dataquality.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgesync.factoryapi.dataquality.adapter.outbound.resource.ClasspathDataQualityEvidence;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DataQualityReportAssemblerTest {
  @Test
  void keepsUnavailableRuntimeAndCompletenessDistinctFromMeasuredSourceQuality() throws Exception {
    SourceQualityEvidence source = new ClasspathDataQualityEvidence().load();
    Class<?> type =
        Class.forName(
            "com.forgesync.factoryapi.dataquality.application.DataQualityReportAssembler");
    Object report =
        type.getMethod("assembleSource", SourceQualityEvidence.class, Instant.class)
            .invoke(
                type.getConstructor().newInstance(), source, Instant.parse("2026-09-13T09:00:00Z"));

    assertThat(read(report, "overallGrade")).isNull();
    Object dimensions = read(report, "dimensions");
    Object semanticCoverage = read(dimensions, "semanticCoverage");
    assertThat((BigDecimal) read(semanticCoverage, "ratio"))
        .isEqualByComparingTo("0.8767318153994706");
    assertThat(read(read(dimensions, "completeness"), "status")).isEqualTo("NOT_EVALUATED");
    assertThat(read(read(dimensions, "duplication"), "sourceProfile"))
        .extracting(
            item -> {
              try {
                return read(item, "status");
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            })
        .isEqualTo("NOT_EVALUATED");
  }

  private static Object read(Object target, String methodName) throws Exception {
    Method method = target.getClass().getMethod(methodName);
    return method.invoke(target);
  }
}

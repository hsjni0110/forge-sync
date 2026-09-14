package com.forgesync.factoryapi.dataquality.domain;

import java.math.BigDecimal;
import java.math.MathContext;

public final class DataQualityPolicy {
  private DataQualityPolicy() {}

  public static QualityMeasurement measure(long numerator, long denominator) {
    if (numerator < 0 || denominator < 0 || numerator > denominator) {
      throw new IllegalArgumentException(
          "quality counts must satisfy 0 <= numerator <= denominator");
    }
    if (denominator == 0) {
      return new QualityMeasurement("NOT_EVALUATED", numerator, denominator, null, null);
    }
    BigDecimal ratio =
        BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), MathContext.DECIMAL64);
    return new QualityMeasurement("MEASURED", numerator, denominator, ratio, null);
  }

  public record QualityMeasurement(
      String status, long numerator, long denominator, BigDecimal ratio, String grade) {}
}

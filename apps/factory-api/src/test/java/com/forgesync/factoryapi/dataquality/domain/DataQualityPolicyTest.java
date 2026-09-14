package com.forgesync.factoryapi.dataquality.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DataQualityPolicyTest {

  @Test
  void preservesMeasuredCountsWithoutInventingAnOverallGrade() throws Exception {
    Object measurement = calculate(101_644L, 115_991L);

    assertThat(read(measurement, "status")).isEqualTo("MEASURED");
    assertThat(read(measurement, "numerator")).isEqualTo(101_644L);
    assertThat(read(measurement, "denominator")).isEqualTo(115_991L);
    assertThat((BigDecimal) read(measurement, "ratio")).isEqualByComparingTo("0.8763093688303403");
    assertThat(read(measurement, "grade")).isNull();
  }

  @Test
  void reportsAnEmptyDenominatorAsNotEvaluatedInsteadOfPerfectQuality() throws Exception {
    Object measurement = calculate(0L, 0L);

    assertThat(read(measurement, "status")).isEqualTo("NOT_EVALUATED");
    assertThat(read(measurement, "ratio")).isNull();
    assertThat(read(measurement, "grade")).isNull();
  }

  private static Object calculate(long numerator, long denominator) throws Exception {
    Class<?> policy =
        Class.forName("com.forgesync.factoryapi.dataquality.domain.DataQualityPolicy");
    return policy.getMethod("measure", long.class, long.class).invoke(null, numerator, denominator);
  }

  private static Object read(Object target, String methodName) throws Exception {
    Method method = target.getClass().getMethod(methodName);
    return method.invoke(target);
  }
}

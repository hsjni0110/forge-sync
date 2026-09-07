package com.forgesync.factoryapi.adapter.inbound.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgesync.factoryapi.application.InvalidObservationContractException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObservationContractValidatorTest {

  private final ObservationContractValidator validator = new ObservationContractValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "condition-warning.json",
        "event-emergency-stop.json",
        "event-execution.json",
        "event-unavailable.json",
        "sample-accumulated-time.json",
        "sample-spindle-speed.json",
        "sample-unavailable.json"
      })
  void acceptsSharedProducerFixtureWithoutTranslation(String fixtureName) {
    String observationJson = readFixture("valid", fixtureName);

    assertThatCode(() -> validator.validate(observationJson)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "accumulated-time-before-schema-upgrade.json",
        "condition-missing-provenance.json",
        "event-line-value-type.json",
        "event-value-type.json",
        "missing-source-data-item.json",
        "missing-subject.json",
        "replay-partial.json",
        "sample-unit-mismatch.json",
        "sample-without-unit-provenance.json",
        "schema-version-mismatch.json",
        "source-timezone-missing.json",
        "unavailable-sample-with-value.json",
        "unit-provenance-before-schema-upgrade.json",
        "unknown-field.json"
      })
  void rejectsSharedInvalidFixtureBeforeApplicationUseCase(String fixtureName) {
    String observationJson = readFixture("invalid", fixtureName);

    assertThatThrownBy(() -> validator.validate(observationJson))
        .isInstanceOfSatisfying(
            InvalidObservationContractException.class,
            exception -> {
              assertThat(exception.getMessage())
                  .isEqualTo(InvalidObservationContractException.ERROR_CODE);
              assertThat(exception.violations()).isNotEmpty();
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "not-json"})
  void translatesMalformedJsonToStableApplicationError(String malformedJson) {
    assertThatThrownBy(() -> validator.validate(malformedJson))
        .isInstanceOfSatisfying(
            InvalidObservationContractException.class,
            exception -> assertThat(exception.violations()).containsExactly("MALFORMED_JSON"));
  }

  @Test
  void contractErrorDoesNotExposeRejectedRawValue() {
    String rawValue = "SECRET_RAW_VALUE";
    String observationJson =
        readFixture("valid", "event-execution.json")
            .replace("\"EXECUTION\"", "\"TOOL_NUMBER\"")
            .replace("\"ACTIVE\"", "\"" + rawValue + "\"");

    assertThatThrownBy(() -> validator.validate(observationJson))
        .isInstanceOfSatisfying(
            InvalidObservationContractException.class,
            exception ->
                assertThat(exception.violations())
                    .allMatch(violation -> !violation.contains(rawValue)));
  }

  private static String readFixture(String status, String fixtureName) {
    String path = "fixtures/canonical/v2/" + status + "/" + fixtureName;
    try (InputStream stream =
        ObservationContractValidatorTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Fixture is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read fixture: " + path, exception);
    }
  }
}

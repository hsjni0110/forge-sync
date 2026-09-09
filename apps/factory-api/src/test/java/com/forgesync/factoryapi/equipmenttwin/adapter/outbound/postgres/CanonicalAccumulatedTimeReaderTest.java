package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalAccumulatedTimeReaderTest {

  @Test
  void readsAvailableAndUnavailableCountersWithoutCorrectingTheirValues() {
    CanonicalAccumulatedTimeReader reader = new CanonicalAccumulatedTimeReader(new ObjectMapper());

    Optional<?> available =
        reader.read(
            "{\"payload\":{\"metric\":\"AUTO_ACCUMULATED_TIME\",\"availability\":\"AVAILABLE\",\"value\":42.5,\"unit\":\"SECOND\",\"unitProvenance\":\"DERIVED\"}}");
    Optional<?> unavailable =
        reader.read(
            "{\"payload\":{\"metric\":\"CUT_ACCUMULATED_TIME\",\"availability\":\"UNAVAILABLE\"}}");

    assertThat(available).isPresent();
    assertThat(available.orElseThrow().toString()).contains("AUTO").contains("42.5");
    assertThat(unavailable).isPresent();
    assertThat(unavailable.orElseThrow().toString()).contains("CUT").contains("isAvailable=false");
  }
}

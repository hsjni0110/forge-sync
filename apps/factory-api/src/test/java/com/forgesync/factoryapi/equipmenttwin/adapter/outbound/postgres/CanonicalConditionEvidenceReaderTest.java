package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CanonicalConditionEvidenceReaderTest {

  @Test
  void readsOnlyWarningAndFaultAsPointInTimeConcurrentEvidence() {
    CanonicalConditionEvidenceReader reader =
        new CanonicalConditionEvidenceReader(new ObjectMapper());
    String warning =
        """
        {"observationKind":"CONDITION","subject":{"componentId":"Mazak01-controller"},
         "payload":{"conditionType":"SYSTEM","level":"WARNING","nativeCode":"406",
         "message":"MEMORY PROTECT"}}
        """;
    String normal =
        """
        {"observationKind":"CONDITION","subject":{"componentId":"Mazak01-controller"},
         "payload":{"conditionType":"SYSTEM","level":"NORMAL"}}
        """;

    CanonicalConditionEvidenceReader.ReadCondition evidence = reader.read(warning).orElseThrow();

    assertThat(evidence.componentId()).isEqualTo("Mazak01-controller");
    assertThat(evidence.conditionType()).isEqualTo("SYSTEM");
    assertThat(evidence.level()).isEqualTo("WARNING");
    assertThat(evidence.nativeCode()).isEqualTo("406");
    assertThat(evidence.message()).isEqualTo("MEMORY PROTECT");
    assertThat(reader.read(normal)).isEmpty();
  }
}

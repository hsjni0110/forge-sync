package com.forgesync.factoryapi.equipmenttwin.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JsonSpatialLayoutProviderTest {
  @Test
  void readsTheVersionedMazakLayoutWithExplicitUnitsAndProvenance() {
    var input =
        getClass().getClassLoader().getResourceAsStream("config/spatial/machine-layout-v1.json");
    var layout =
        new JsonSpatialLayoutProvider(new ObjectMapper(), input).findByMachineId("Mazak01");
    assertThat(layout).isPresent();
    assertThat(layout.orElseThrow().positionUnit()).isEqualTo("SCENE_UNIT");
    assertThat(layout.orElseThrow().rotationUnit()).isEqualTo("RADIAN");
    assertThat(layout.orElseThrow().provenance()).isEqualTo("SIMULATED_LAYOUT");
  }

  @Test
  void rejectsAnIncompleteMachineAtomically() {
    String invalid =
        """
        {"schemaVersion":"1.0.0","positionUnit":"SCENE_UNIT","rotationUnit":"RADIAN",
         "machines":[{"machineId":"Mazak01","assetId":"asset","sceneNodeId":"node",
         "position":[1,2],"rotation":[0,0,0],"scale":[1,1,1],"provenance":"SIMULATED_LAYOUT"}]}
        """;
    var provider =
        new JsonSpatialLayoutProvider(
            new ObjectMapper(), new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8)));
    assertThat(provider.findByMachineId("Mazak01")).isEmpty();
  }
}

package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class ProcessAnalysisCorsTest {
  @Test
  void allowsAnalysisPreflightOnlyFromConfiguredOrigin() throws Exception {
    for (String resource : List.of("machining-runs", "cycle-features", "anomaly-assessments")) {
      String path = "/api/v1/machines/Mazak01/" + resource + "/processing-runs";
      var allowed = preflight(path, "http://localhost:5173");
      assertThat(allowed.getStatus()).isEqualTo(200);
      assertThat(allowed.getHeader("Access-Control-Allow-Origin"))
          .isEqualTo("http://localhost:5173");
      assertThat(preflight(path, "https://untrusted.example").getStatus()).isEqualTo(403);
    }
    assertThat(preflight("/api/v1/machines/Mazak01/twin", "http://localhost:5173").getStatus())
        .isEqualTo(403);
  }

  private MockHttpServletResponse preflight(String path, String origin) throws Exception {
    var registry = new TestCorsRegistry();
    new TwinWebSocketConfiguration(List.of("http://localhost:5173")).addCorsMappings(registry);
    var request = new MockHttpServletRequest("OPTIONS", path);
    request.addHeader("Origin", origin);
    request.addHeader("Access-Control-Request-Method", "POST");
    request.addHeader("Access-Control-Request-Headers", "Content-Type");
    var response = new MockHttpServletResponse();
    new CorsFilter(registry.source()).doFilter(request, response, new MockFilterChain());
    return response;
  }

  private static class TestCorsRegistry extends CorsRegistry {
    UrlBasedCorsConfigurationSource source() {
      var source = new UrlBasedCorsConfigurationSource();
      source.setCorsConfigurations(getCorsConfigurations());
      return source;
    }
  }
}

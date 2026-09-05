package com.forgesync.factoryapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.adapter.EquipmentTwinConfiguration;
import com.forgesync.factoryapi.processanalytics.adapter.ProcessAnalyticsConfiguration;
import com.forgesync.factoryapi.processanalytics.adapter.inbound.rest.AnomalyAssessmentController;
import com.forgesync.factoryapi.processanalytics.adapter.inbound.rest.AnomalyAssessmentErrorHandler;
import com.forgesync.factoryapi.processanalytics.adapter.inbound.rest.CycleFeatureController;
import com.forgesync.factoryapi.processanalytics.adapter.inbound.rest.CycleFeatureErrorHandler;
import com.forgesync.factoryapi.processanalytics.adapter.inbound.rest.MachiningRunController;
import com.forgesync.factoryapi.processanalytics.adapter.inbound.rest.MachiningRunErrorHandler;
import com.forgesync.factoryapi.processanalytics.application.AnomalyAssessmentService;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureService;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunService;
import com.forgesync.factoryapi.replay.adapter.ReplayConfiguration;
import com.forgesync.factoryapi.replay.adapter.inbound.rest.ReplayController;
import com.forgesync.factoryapi.replay.adapter.inbound.rest.ReplayErrorHandler;
import com.forgesync.factoryapi.replay.application.ControlReplay;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

class RuntimeRouteConfigurationTest {

  @Test
  void registersReplayRuntimeAndControllerTogetherWhenEnabled() {
    new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withUserConfiguration(
            RuntimeDependencies.class,
            EquipmentTwinConfiguration.class,
            ReplayConfiguration.class,
            ReplayController.class,
            ReplayErrorHandler.class)
        .withPropertyValues(
            "forgesync.replay.enabled=true", "forgesync.replay.edge-base-url=http://127.0.0.1:8002")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ControlReplay.class);
              assertThat(context).hasSingleBean(ReplayController.class);
              assertThat(context).hasSingleBean(ReplayErrorHandler.class);
            });
  }

  @Test
  void registersProcessAnalyticsRuntimeAndControllerTogetherWhenEnabled() {
    new ApplicationContextRunner()
        .withUserConfiguration(
            RuntimeDependencies.class,
            ProcessAnalyticsConfiguration.class,
            CycleFeatureController.class,
            CycleFeatureErrorHandler.class,
            AnomalyAssessmentController.class,
            AnomalyAssessmentErrorHandler.class,
            MachiningRunController.class,
            MachiningRunErrorHandler.class)
        .withPropertyValues("forgesync.process-analytics.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(MachiningRunService.class);
              assertThat(context).hasSingleBean(CycleFeatureService.class);
              assertThat(context).hasSingleBean(AnomalyAssessmentService.class);
              assertThat(context).hasSingleBean(AnomalyAssessmentController.class);
              assertThat(context).hasSingleBean(AnomalyAssessmentErrorHandler.class);
              assertThat(context).hasSingleBean(CycleFeatureController.class);
              assertThat(context).hasSingleBean(CycleFeatureErrorHandler.class);
              assertThat(context).hasSingleBean(MachiningRunController.class);
              assertThat(context).hasSingleBean(MachiningRunErrorHandler.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class RuntimeDependencies {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    JdbcClient jdbcClient() {
      return mock(JdbcClient.class);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }
}

package com.forgesync.factoryapi.equipmenttwin.adapter;

import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateProjectionPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessPolicy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EquipmentTwinConfiguration {

  @Bean
  EquipmentStateProjectionPolicy equipmentStateProjectionPolicy() {
    return new EquipmentStateProjectionPolicy();
  }

  @Bean
  FreshnessPolicy freshnessPolicy(
      @Value("${forgesync.twin.freshness.fresh-max-age:2s}") Duration freshMaxAge,
      @Value("${forgesync.twin.freshness.lagging-max-age:10s}") Duration laggingMaxAge) {
    return new FreshnessPolicy(freshMaxAge, laggingMaxAge);
  }
}

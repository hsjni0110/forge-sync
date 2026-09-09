package com.forgesync.factoryapi.equipmenttwin.application;

public record DowntimeParetoCommand(
    String machineId, String utilizationProcessingRunId, String ruleVersion) {}

"""Call the Factory API projection chain with the rule versions the operator screens use."""

from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

JsonDocument = dict[str, Any]

# Same rule versions the Factory Web screens request, so results match what operators see.
INTERVAL_RULE_VERSION = "1.0.0"
SEGMENTATION_RULE_VERSION = "1.0.0"
CYCLE_FEATURE_VERSION = "1.0.0"
UTILIZATION_CALCULATION_VERSION = "1.0.0"
DOWNTIME_PARETO_RULE_VERSION = "1.1.0"
EFFECTIVENESS_POLICY_VERSION = "1.0.0"
PRODUCTION_CONTEXT_RULE_VERSION = "1.0.0"


class FactoryApiError(RuntimeError):
    pass


class FactoryApiProjectionClient:
    def __init__(self, api_base_url: str, machine_id: str, timeout_seconds: float = 120.0) -> None:
        self._machine_url = f"{api_base_url.rstrip('/')}/api/v1/machines/{machine_id}"
        self._timeout_seconds = timeout_seconds

    def project_state_intervals(
        self, replay_session_id: str, through_replay_sequence: int
    ) -> JsonDocument:
        return self._post(
            "equipment-state-intervals/processing-runs",
            {
                "replaySessionId": replay_session_id,
                "throughReplaySequence": through_replay_sequence,
                "intervalRuleVersion": INTERVAL_RULE_VERSION,
            },
        )

    def segment_machining_runs(
        self, replay_session_id: str, through_replay_sequence: int
    ) -> JsonDocument:
        return self._post(
            "machining-runs/processing-runs",
            {
                "replaySessionId": replay_session_id,
                "throughReplaySequence": through_replay_sequence,
                "segmentationRuleVersion": SEGMENTATION_RULE_VERSION,
            },
        )

    def extract_cycle_features(self, machining_run_processing_run_id: str) -> JsonDocument:
        return self._post(
            "cycle-features/processing-runs",
            {
                "machiningRunProcessingRunId": machining_run_processing_run_id,
                "cycleFeatureVersion": CYCLE_FEATURE_VERSION,
            },
        )

    def project_utilization(self, interval_processing_run_id: str) -> JsonDocument:
        return self._post(
            "utilization-kpis/processing-runs",
            {
                "intervalProcessingRunId": interval_processing_run_id,
                "calculationVersion": UTILIZATION_CALCULATION_VERSION,
            },
        )

    def rank_downtime(self, utilization_processing_run_id: str) -> JsonDocument:
        return self._post(
            "downtime-pareto/processing-runs",
            {
                "utilizationProcessingRunId": utilization_processing_run_id,
                "ruleVersion": DOWNTIME_PARETO_RULE_VERSION,
            },
        )

    def assess_effectiveness(
        self, utilization_processing_run_id: str, cycle_feature_processing_run_id: str
    ) -> JsonDocument:
        # No assumed ideal cycle: ForgeSync must disclose what it cannot derive from the source.
        return self._post(
            "operational-effectiveness/processing-runs",
            {
                "utilizationProcessingRunId": utilization_processing_run_id,
                "cycleFeatureProcessingRunId": cycle_feature_processing_run_id,
                "policyVersion": EFFECTIVENESS_POLICY_VERSION,
                "assumedIdealCycleSecondsByProgram": {},
            },
        )

    def find_production_context(self, machining_run_processing_run_id: str) -> JsonDocument:
        query = urllib.parse.urlencode(
            {
                "machiningRunProcessingRunId": machining_run_processing_run_id,
                "ruleVersion": PRODUCTION_CONTEXT_RULE_VERSION,
            }
        )
        return self._get(f"production-context?{query}")

    def _post(self, resource: str, body: JsonDocument) -> JsonDocument:
        request = urllib.request.Request(
            f"{self._machine_url}/{resource}",
            data=json.dumps(body).encode("utf-8"),
            method="POST",
            headers={"Content-Type": "application/json", "Accept": "*/*"},
        )
        return self._send(request, resource)

    def _get(self, resource: str) -> JsonDocument:
        request = urllib.request.Request(
            f"{self._machine_url}/{resource}", method="GET", headers={"Accept": "*/*"}
        )
        return self._send(request, resource)

    def _send(self, request: urllib.request.Request, resource: str) -> JsonDocument:
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                document: JsonDocument = json.loads(response.read())
                return document
        except urllib.error.HTTPError as error:
            raise FactoryApiError(f"Factory API returned {error.code} for {resource}") from error

from __future__ import annotations

import json
import threading
from collections.abc import Iterator
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any

import pytest
from forgesync_evaluation.adapter.outbound.factory_api_projections import (
    FactoryApiError,
    FactoryApiProjectionClient,
)


class RecordingFactoryApi(BaseHTTPRequestHandler):
    requests: list[tuple[str, str, Any]] = []
    status_code = 201

    def do_POST(self) -> None:
        length = int(self.headers["Content-Length"])
        body = json.loads(self.rfile.read(length))
        RecordingFactoryApi.requests.append(("POST", self.path, body))
        self._respond({"processingRunId": "sha256:aaa"})

    def do_GET(self) -> None:
        RecordingFactoryApi.requests.append(("GET", self.path, None))
        self._respond({"productionResultStatus": "sha256:aaa"})

    def _respond(self, document: dict[str, Any]) -> None:
        payload = json.dumps(document).encode()
        self.send_response(RecordingFactoryApi.status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args: Any) -> None:
        return


@pytest.fixture
def api_base_url() -> Iterator[str]:
    RecordingFactoryApi.requests = []
    RecordingFactoryApi.status_code = 201
    server = HTTPServer(("127.0.0.1", 0), RecordingFactoryApi)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{server.server_port}"
    server.shutdown()
    server.server_close()


def test_client_projects_state_intervals_with_the_pinned_rule_version(api_base_url: str) -> None:
    client = FactoryApiProjectionClient(api_base_url, "Mazak01")

    document = client.project_state_intervals("00000000-0000-0000-0000-000000000001", 10)

    assert document == {"processingRunId": "sha256:aaa"}
    assert RecordingFactoryApi.requests == [
        (
            "POST",
            "/api/v1/machines/Mazak01/equipment-state-intervals/processing-runs",
            {
                "replaySessionId": "00000000-0000-0000-0000-000000000001",
                "throughReplaySequence": 10,
                "intervalRuleVersion": "1.0.0",
            },
        )
    ]


def test_client_translates_an_http_failure_into_a_named_factory_api_error(
    api_base_url: str,
) -> None:
    RecordingFactoryApi.status_code = 404
    client = FactoryApiProjectionClient(api_base_url, "Mazak01")

    with pytest.raises(FactoryApiError, match="404.*equipment-state-intervals"):
        client.project_state_intervals("00000000-0000-0000-0000-000000000001", 10)


def test_client_sends_the_rest_of_the_projection_chain_with_pinned_versions(
    api_base_url: str,
) -> None:
    client = FactoryApiProjectionClient(api_base_url, "Mazak01")

    client.segment_machining_runs("00000000-0000-0000-0000-000000000001", 10)
    client.extract_cycle_features("sha256:runs")
    client.project_utilization("sha256:intervals")
    client.rank_downtime("sha256:utilization")
    client.assess_effectiveness("sha256:utilization", "sha256:features")
    client.find_production_context("sha256:runs")

    machine = "/api/v1/machines/Mazak01"
    assert RecordingFactoryApi.requests == [
        (
            "POST",
            f"{machine}/machining-runs/processing-runs",
            {
                "replaySessionId": "00000000-0000-0000-0000-000000000001",
                "throughReplaySequence": 10,
                "segmentationRuleVersion": "1.0.0",
            },
        ),
        (
            "POST",
            f"{machine}/cycle-features/processing-runs",
            {"machiningRunProcessingRunId": "sha256:runs", "cycleFeatureVersion": "1.0.0"},
        ),
        (
            "POST",
            f"{machine}/utilization-kpis/processing-runs",
            {"intervalProcessingRunId": "sha256:intervals", "calculationVersion": "1.0.0"},
        ),
        (
            "POST",
            f"{machine}/downtime-pareto/processing-runs",
            {"utilizationProcessingRunId": "sha256:utilization", "ruleVersion": "1.1.0"},
        ),
        (
            "POST",
            f"{machine}/operational-effectiveness/processing-runs",
            {
                "utilizationProcessingRunId": "sha256:utilization",
                "cycleFeatureProcessingRunId": "sha256:features",
                "policyVersion": "1.0.0",
                "assumedIdealCycleSecondsByProgram": {},
            },
        ),
        (
            "GET",
            f"{machine}/production-context?machiningRunProcessingRunId=sha256%3Aruns"
            "&ruleVersion=1.0.0",
            None,
        ),
    ]

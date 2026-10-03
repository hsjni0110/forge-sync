from __future__ import annotations

from pathlib import Path

from forgesync_evaluation.adapter.outbound.projection_document_files import (
    load_projection_documents,
    save_projection_documents,
)
from forgesync_evaluation.application.forgesync_track_a import ProjectionDocuments

DOCUMENTS = ProjectionDocuments(
    machining_runs={"processingRunId": "sha256:runs"},
    utilization={"processingRunId": "sha256:utilization"},
    downtime_pareto={"processingRunId": "sha256:pareto"},
    effectiveness={"processingRunId": "sha256:effectiveness"},
    production_context={"productionResultStatus": "NOT_OBSERVED"},
)


def test_saved_documents_load_back_unchanged_under_their_projection_names(tmp_path: Path) -> None:
    save_projection_documents(DOCUMENTS, tmp_path)

    assert sorted(path.name for path in tmp_path.iterdir()) == [
        "downtime-pareto.json",
        "machining-runs.json",
        "operational-effectiveness.json",
        "production-context.json",
        "utilization-kpis.json",
    ]
    assert load_projection_documents(tmp_path) == DOCUMENTS

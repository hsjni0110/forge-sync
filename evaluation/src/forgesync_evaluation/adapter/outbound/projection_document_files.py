"""Keep collected projection documents as files so a comparison can be rerun offline."""

from __future__ import annotations

import json
from dataclasses import asdict
from pathlib import Path

from forgesync_evaluation.application.forgesync_track_a import JsonDocument, ProjectionDocuments

# File names follow the Factory API resource that produced each document.
FILE_NAMES = {
    "machining_runs": "machining-runs.json",
    "utilization": "utilization-kpis.json",
    "downtime_pareto": "downtime-pareto.json",
    "effectiveness": "operational-effectiveness.json",
    "production_context": "production-context.json",
}


def save_projection_documents(documents: ProjectionDocuments, directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for field_name, document in asdict(documents).items():
        (directory / FILE_NAMES[field_name]).write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )


def load_projection_documents(directory: Path) -> ProjectionDocuments:
    loaded: dict[str, JsonDocument] = {
        field_name: json.loads((directory / file_name).read_text(encoding="utf-8"))
        for field_name, file_name in FILE_NAMES.items()
    }
    return ProjectionDocuments(**loaded)

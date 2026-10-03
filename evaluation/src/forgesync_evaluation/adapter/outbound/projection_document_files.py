"""Keep collected projection documents as files so a comparison can be rerun offline."""

from __future__ import annotations

import json
from dataclasses import asdict
from pathlib import Path

from forgesync_evaluation.application.forgesync_track_a import JsonDocument, ProjectionDocuments
from forgesync_evaluation.application.forgesync_track_b import DeliveryDocuments

# File names follow the Factory API resource that produced each document.
FILE_NAMES = {
    "machining_runs": "machining-runs.json",
    "utilization": "utilization-kpis.json",
    "downtime_pareto": "downtime-pareto.json",
    "effectiveness": "operational-effectiveness.json",
    "production_context": "production-context.json",
}


DELIVERY_FILE_NAMES = {
    "twin": "twin.json",
    "intervals": "equipment-state-intervals.json",
    "machining_runs": "machining-runs.json",
    "alarms": "alarms.json",
}


def save_projection_documents(documents: ProjectionDocuments, directory: Path) -> None:
    _save(asdict(documents), FILE_NAMES, directory)


def load_projection_documents(directory: Path) -> ProjectionDocuments:
    return ProjectionDocuments(**_load(FILE_NAMES, directory))


def save_delivery_documents(documents: DeliveryDocuments, directory: Path) -> None:
    _save(asdict(documents), DELIVERY_FILE_NAMES, directory)


def load_delivery_documents(directory: Path) -> DeliveryDocuments:
    return DeliveryDocuments(**_load(DELIVERY_FILE_NAMES, directory))


def _save(documents: dict[str, JsonDocument], file_names: dict[str, str], directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for field_name, document in documents.items():
        (directory / file_names[field_name]).write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )


def _load(file_names: dict[str, str], directory: Path) -> dict[str, JsonDocument]:
    return {
        field_name: json.loads((directory / file_name).read_text(encoding="utf-8"))
        for field_name, file_name in file_names.items()
    }

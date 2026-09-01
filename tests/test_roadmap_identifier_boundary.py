from __future__ import annotations

import re
from pathlib import Path

IGNORED_DIRECTORIES = {
    ".git",
    ".mypy_cache",
    ".pytest_cache",
    ".ruff_cache",
    ".venv",
    "__pycache__",
    "docs",
}
TEXT_SUFFIXES = {".json", ".py", ".toml", ".yaml", ".yml"}
ROADMAP_LABEL = re.compile(r"\bstep(?:[-_ ]?0*\d+)\b", re.IGNORECASE)


def test_roadmap_labels_do_not_leak_into_implementation() -> None:
    repository_root = Path(__file__).parents[1]
    violations: list[str] = []
    for path in repository_root.rglob("*"):
        if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
            continue
        relative_path = path.relative_to(repository_root)
        if any(part in IGNORED_DIRECTORIES for part in relative_path.parts):
            continue
        content = path.read_text(encoding="utf-8", errors="ignore")
        if ROADMAP_LABEL.search(str(relative_path)) or ROADMAP_LABEL.search(content):
            violations.append(str(relative_path))

    assert violations == [], f"Roadmap identifiers leaked into implementation: {violations}"

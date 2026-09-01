from pathlib import Path

from tools.architecture import check_architecture


def test_python_sources_follow_inward_dependency_direction() -> None:
    repository_root = Path(__file__).parents[1]
    source_roots = [
        repository_root / "apps/edge-gateway/src",
        repository_root / "apps/ai-service/src",
        repository_root / "apps/virtual-controller/src",
    ]

    assert check_architecture(source_roots) == []


def test_architecture_checker_rejects_domain_to_framework_dependency(tmp_path: Path) -> None:
    domain_directory = tmp_path / "example" / "domain"
    domain_directory.mkdir(parents=True)
    (domain_directory / "policy.py").write_text("from fastapi import FastAPI\n", encoding="utf-8")

    violations = check_architecture([tmp_path])

    assert len(violations) == 1
    assert "domain must not import framework module 'fastapi'" in str(violations[0])

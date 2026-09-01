"""Enforce Python dependency direction without importing application code."""

from __future__ import annotations

import argparse
import ast
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from pathlib import Path

LAYER_RANK = {"domain": 0, "application": 1, "adapter": 2}
FRAMEWORK_MODULES = {
    "fastapi",
    "httpx",
    "httpx2",
    "paho",
    "requests",
    "sqlalchemy",
    "starlette",
    "uvicorn",
}
EDGE_APPLICATION_MODULES = {"application", "ports"}
EDGE_ADAPTER_MODULES = {
    "cli",
    "filesystem_store",
    "http_reader",
    "profile",
    "shdr_decoder",
    "source_lock",
    "xml_catalog",
}


@dataclass(frozen=True, slots=True)
class Module:
    name: str
    path: Path
    layer: str | None


@dataclass(frozen=True, slots=True)
class Violation:
    path: Path
    line: int
    message: str

    def __str__(self) -> str:
        return f"{self.path}:{self.line}: {self.message}"


def discover_modules(source_roots: Iterable[Path]) -> dict[str, Module]:
    modules: dict[str, Module] = {}
    for source_root in source_roots:
        for path in source_root.rglob("*.py"):
            relative = path.relative_to(source_root)
            parts = list(relative.with_suffix("").parts)
            if parts[-1] == "__init__":
                parts.pop()
            if not parts:
                continue
            name = ".".join(parts)
            modules[name] = Module(name=name, path=path, layer=_layer_for(parts))
    return modules


def check_architecture(source_roots: Iterable[Path]) -> list[Violation]:
    modules = discover_modules(source_roots)
    violations: list[Violation] = []
    for module in modules.values():
        if module.layer is None:
            continue
        tree = ast.parse(module.path.read_text(encoding="utf-8"), filename=str(module.path))
        for imported_name, line in _imports(module.name, module.path, tree):
            imported_layer = _imported_layer(imported_name, modules)
            if module.layer == "domain" and _top_level(imported_name) in FRAMEWORK_MODULES:
                violations.append(
                    Violation(
                        module.path,
                        line,
                        f"domain must not import framework module {imported_name!r}",
                    )
                )
            if imported_layer is None:
                continue
            if LAYER_RANK[module.layer] < LAYER_RANK[imported_layer]:
                violations.append(
                    Violation(
                        module.path,
                        line,
                        f"{module.layer} must not depend on outer {imported_layer} module "
                        f"{imported_name!r}",
                    )
                )
    return violations


def _imports(module_name: str, path: Path, tree: ast.AST) -> Iterable[tuple[str, int]]:
    package = module_name if path.name == "__init__.py" else module_name.rpartition(".")[0]
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                yield alias.name, node.lineno
        elif isinstance(node, ast.ImportFrom):
            if node.level:
                parent_parts = package.split(".") if package else []
                kept_parts = parent_parts[: len(parent_parts) - node.level + 1]
                base = ".".join((*kept_parts, *(node.module or "").split("."))).strip(".")
            else:
                base = node.module or ""
            yield base, node.lineno


def _imported_layer(imported_name: str, modules: dict[str, Module]) -> str | None:
    candidates = (
        module.layer
        for name, module in modules.items()
        if imported_name == name or imported_name.startswith(f"{name}.")
    )
    return next((layer for layer in candidates if layer is not None), None)


def _layer_for(parts: list[str]) -> str | None:
    for layer in LAYER_RANK:
        if layer in parts:
            return layer
    if "source_acquisition" not in parts:
        return None
    module_name = parts[-1]
    if module_name in {"domain", "errors"}:
        return "domain"
    if module_name in EDGE_APPLICATION_MODULES:
        return "application"
    if module_name in EDGE_ADAPTER_MODULES:
        return "adapter"
    return None


def _top_level(module_name: str) -> str:
    return module_name.partition(".")[0]


def main(arguments: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_roots", nargs="+", type=Path)
    namespace = parser.parse_args(arguments)
    violations = check_architecture(namespace.source_roots)
    for violation in violations:
        print(violation)
    return 1 if violations else 0


if __name__ == "__main__":
    raise SystemExit(main())

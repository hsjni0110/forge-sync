"""Run every repository quality gate through one failure-transparent entry point."""

from __future__ import annotations

import subprocess
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).parents[1]


@dataclass(frozen=True, slots=True)
class VerificationCommand:
    label: str
    arguments: tuple[str, ...]


COMMANDS = (
    VerificationCommand(
        "Python dependencies", ("uv", "sync", "--locked", "--all-packages", "--all-groups")
    ),
    VerificationCommand("Python format", ("uv", "run", "ruff", "format", "--check", ".")),
    VerificationCommand("Python lint", ("uv", "run", "ruff", "check", ".")),
    VerificationCommand("Python types", ("uv", "run", "mypy")),
    VerificationCommand("Python tests", ("uv", "run", "pytest")),
    VerificationCommand("Edge package", ("uv", "build", "--package", "forgesync-edge-gateway")),
    VerificationCommand("AI package", ("uv", "build", "--package", "forgesync-ai-service")),
    VerificationCommand(
        "Virtual controller package",
        ("uv", "build", "--package", "forgesync-virtual-controller"),
    ),
    VerificationCommand(
        "Factory API", ("apps/factory-api/gradlew", "-p", "apps/factory-api", "check", "bootJar")
    ),
    VerificationCommand("Web dependencies", ("npm", "ci", "--prefix", "apps/factory-web")),
    VerificationCommand("Factory Web", ("npm", "--prefix", "apps/factory-web", "run", "verify")),
)

CommandRunner = Callable[[Sequence[str], Path], int]


def subprocess_runner(arguments: Sequence[str], working_directory: Path) -> int:
    return subprocess.run(arguments, cwd=working_directory, check=False).returncode


def run_verification(
    commands: Sequence[VerificationCommand] = COMMANDS,
    runner: CommandRunner = subprocess_runner,
) -> int:
    for command in commands:
        print(f"\n==> {command.label}", flush=True)
        exit_code = runner(command.arguments, REPOSITORY_ROOT)
        if exit_code != 0:
            rendered = " ".join(command.arguments)
            print(f"FAILED [{command.label}] exit={exit_code}: {rendered}", flush=True)
            return exit_code
    print("\nAll ForgeSync verification gates passed.", flush=True)
    return 0


def main() -> int:
    return run_verification()


if __name__ == "__main__":
    raise SystemExit(main())

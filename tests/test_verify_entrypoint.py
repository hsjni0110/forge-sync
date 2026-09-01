from collections.abc import Sequence
from pathlib import Path

import pytest

from tools.verify import VerificationCommand, run_verification


def test_root_verification_propagates_the_first_app_failure(
    capsys: pytest.CaptureFixture[str],
) -> None:
    commands = (
        VerificationCommand("Edge", ("edge-check",)),
        VerificationCommand("Factory API", ("api-check",)),
        VerificationCommand("Factory Web", ("web-check",)),
    )
    executed: list[tuple[str, ...]] = []

    def runner(arguments: Sequence[str], working_directory: Path) -> int:
        executed.append(tuple(arguments))
        assert working_directory.is_dir()
        return 7 if arguments[0] == "api-check" else 0

    exit_code = run_verification(commands, runner)

    assert exit_code == 7
    assert executed == [("edge-check",), ("api-check",)]
    output = capsys.readouterr().out
    assert "FAILED [Factory API] exit=7" in output

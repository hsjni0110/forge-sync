from pathlib import Path

REPOSITORY_ROOT = Path(__file__).parents[3]
BROKER_CONFIG = REPOSITORY_ROOT / "infra" / "mqtt" / "mosquitto.conf"


def test_broker_queue_is_bounded_above_the_pinned_replay_size() -> None:
    settings = dict(
        line.split(maxsplit=1)
        for line in BROKER_CONFIG.read_text(encoding="utf-8").splitlines()
        if line.startswith(("max_queued_messages ", "max_queued_bytes "))
    )

    assert int(settings["max_queued_messages"]) >= 52_996
    assert 0 < int(settings["max_queued_bytes"]) <= 128 * 1024 * 1024

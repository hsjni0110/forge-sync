# Replay Plan

This report verifies a deterministic schedule. It does not claim that messages were published.

| Field | Value |
|---|---|
| Canonical Processing Run | `sha256:0f1a8dfec2c252df98a344fb33131f40b01fcac88f1c4f3b31f7df4da9415bc4` |
| Canonical Observations SHA-256 | `2fc48ab581fb133ac9d9340e2f2c23058d7ea3b5002fedcf8900cb8c6e7aa340` |
| Replay Session | `61c7fe98-d1cd-4a2c-9ea8-24f72cc714db` |
| Speed | `10x` |
| Observation Count | 101644 |
| Source Range | `2016-10-05T05:27:55.740706Z` — `2016-10-05T19:15:07.025798Z` |
| Planned Replay Range | `2026-09-01T00:00:00Z` — `2026-09-01T01:22:43.128531Z` |
| Sequence Hash | `sha256:fd8877264f4a5210f6fb2a3e936d9ba77ad352f99de6ad7b3f8f0b924d328a13` |

The sequence hash covers canonical JSON lines containing only `replaySequence` and
`sourceEventKey`. Replay publication times are intentionally excluded, so speed changes do not
change event identity or ordering evidence.

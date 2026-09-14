# Replay Plan

This report verifies a deterministic schedule. It does not claim that messages were published.

| Field | Value |
|---|---|
| Canonical Processing Run | `sha256:839ad138d6da7b2d5439c118d6f3bd88ab820824ae9f460c8e1848e6c638109c` |
| Canonical Observations SHA-256 | `9dd102fef6fa381e396e545a621b2ea51f43f37f49862e5a30172e6bee614b34` |
| Replay Session | `61c7fe98-d1cd-4a2c-9ea8-24f72cc714db` |
| Speed | `10x` |
| Observation Count | 101693 |
| Source Range | `2016-10-05T05:27:55.740706Z` — `2016-10-05T19:15:07.025798Z` |
| Planned Replay Range | `2026-09-01T00:00:00Z` — `2026-09-01T01:22:43.128531Z` |
| Sequence Hash | `sha256:c7b72fdac53a546039247d1fffe1e686a756765244f86e9ecfdbb3c7fb27fb90` |

The sequence hash covers canonical JSON lines containing only `replaySequence` and
`sourceEventKey`. Replay publication times are intentionally excluded, so speed changes do not
change event identity or ordering evidence.

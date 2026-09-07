# Replay Plan

This report verifies a deterministic schedule. It does not claim that messages were published.

| Field | Value |
|---|---|
| Canonical Processing Run | `sha256:80ce6b099c6ee90c11dc04286030b7a2448108d7ef9f81152fb3f0e98a06b3ef` |
| Canonical Observations SHA-256 | `fcbe745107fe709e1ddb428e40b6295ca85718e6b942bdbf71c1990de4332509` |
| Replay Session | `61c7fe98-d1cd-4a2c-9ea8-24f72cc714db` |
| Speed | `10x` |
| Observation Count | 101644 |
| Source Range | `2016-10-05T05:27:55.740706Z` — `2016-10-05T19:15:07.025798Z` |
| Planned Replay Range | `2026-09-01T00:00:00Z` — `2026-09-01T01:22:43.128531Z` |
| Sequence Hash | `sha256:fd8877264f4a5210f6fb2a3e936d9ba77ad352f99de6ad7b3f8f0b924d328a13` |

The sequence hash covers canonical JSON lines containing only `replaySequence` and
`sourceEventKey`. Replay publication times are intentionally excluded, so speed changes do not
change event identity or ordering evidence.

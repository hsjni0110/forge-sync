# Replay Plan

This report verifies a deterministic schedule. It does not claim that messages were published.

| Field | Value |
|---|---|
| Canonical Processing Run | `sha256:bf5a3342dcfcc896153c5b69059de66479e8e19d9f225caa72477ec1dc6a2416` |
| Canonical Observations SHA-256 | `aee293dd19af046087773ccf8d4dde43ff0a8c21df5dcf7b65544e474799b17f` |
| Replay Session | `61c7fe98-d1cd-4a2c-9ea8-24f72cc714db` |
| Speed | `10x` |
| Observation Count | 52996 |
| Source Range | `2016-10-05T05:27:55.740706Z` — `2016-10-05T19:15:07.025798Z` |
| Planned Replay Range | `2026-09-01T00:00:00Z` — `2026-09-01T01:22:43.128531Z` |
| Sequence Hash | `sha256:c1e80699684c02963eab814702efe695ce306549b0eae017e369dbb7eeed9bb6` |

The sequence hash covers canonical JSON lines containing only `replaySequence` and
`sourceEventKey`. Replay publication times are intentionally excluded, so speed changes do not
change event identity or ordering evidence.

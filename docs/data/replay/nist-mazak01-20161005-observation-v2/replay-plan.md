# Replay Plan

This report verifies a deterministic schedule. It does not claim that messages were published.

| Field | Value |
|---|---|
| Canonical Processing Run | `sha256:b5709c2f64d2cd15a30b85bd2355bdc7e388ad058d883e7441536c2987c92cc7` |
| Canonical Observations SHA-256 | `ff224865d1623f0a95b847f21537abdd1a89968b3369e716abb2ca1afdeefc8c` |
| Replay Session | `61c7fe98-d1cd-4a2c-9ea8-24f72cc714db` |
| Speed | `10x` |
| Observation Count | 53939 |
| Source Range | `2016-10-05T05:27:55.740706Z` — `2016-10-05T19:15:07.025798Z` |
| Planned Replay Range | `2026-09-01T00:00:00Z` — `2026-09-01T01:22:43.128531Z` |
| Sequence Hash | `sha256:5e98c6cdab699344608fffdc5c114ce56a9915c03c4271e91194f114c5857b7e` |

The sequence hash covers canonical JSON lines containing only `replaySequence` and
`sourceEventKey`. Replay publication times are intentionally excluded, so speed changes do not
change event identity or ordering evidence.

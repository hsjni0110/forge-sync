# Replay Contracts

The browser uses Factory API as its only public control boundary. Replay lifecycle state is
exposed as `application/vnd.forgesync.replay-session.v1+json`; arbitrary filesystem paths are never
accepted. The active source is selected by a configured `sourceSetId` allowlist.

`ReplayCursor v1` identifies the last replayed Observation atomically applied to an Operational
Twin snapshot. Its `twinVersion` must equal the snapshot consistency version. Source time and replay
publication time remain separate.

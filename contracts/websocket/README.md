# WebSocket Contracts

The raw WebSocket endpoint `/api/v1/ws/machines/{machineId}/twin` emits a versioned
`TWIN_PATCH` after an Equipment Twin projection commits. Version `1.6.0` is defined by
[`v1/twin-patch.schema.json`](./v1/twin-patch.schema.json) and moves with the snapshot version it
carries.

The patch contains a complete authoritative Twin snapshot rather than a partial field mutation.
Consumers replace local state only when `baseVersion` equals the local version and
`targetVersion` is the next version. Duplicate or regressive targets are ignored; a gap, invalid
message, or reconnect requires REST resynchronization before subscribing again.

WebSocket delivery is best-effort. A bounded, ordered executor separates subscriber I/O from the
ingestion caller. The database projection and REST Twin API remain authoritative, and a delivery
failure or a full publication queue never rolls back a committed observation.

Because one patch carries a whole snapshot, the snapshot size is the patch size. A contract test
caps a fully populated snapshot at 64 KiB; Step 47 measures the achieved publish rate and records
the conditions with it.

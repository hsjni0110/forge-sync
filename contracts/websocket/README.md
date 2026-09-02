# WebSocket Contracts

The raw WebSocket endpoint `/api/v1/ws/machines/{machineId}/twin` emits a versioned
`TWIN_PATCH` after an Equipment Twin projection commits. Version `1.0.0` is defined by
[`v1/twin-patch.schema.json`](./v1/twin-patch.schema.json).

The patch contains a complete authoritative Twin snapshot rather than a partial field mutation.
Consumers replace local state only when `baseVersion` equals the local version and
`targetVersion` is the next version. Duplicate or regressive targets are ignored; a gap, invalid
message, or reconnect requires REST resynchronization before subscribing again.

WebSocket delivery is best-effort. The database projection and REST Twin API remain authoritative,
and a delivery failure never rolls back a committed observation.

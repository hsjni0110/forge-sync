# Observed Toolpath Contract

`GET /api/v1/machines/{machineId}/observed-toolpath` returns version `1.0.0` using media type
`application/vnd.forgesync.observed-toolpath.v1+json`. The caller supplies one Replay session, the selected
Machining Run sequence range, and the authoritative through-sequence watermark.

Each point contains held X/Y/Z observations and provenance for all three coordinates. Points are source
observations reduced to a 100 ms interval and a maximum of 2,048 newest points; renderer interpolation is never
returned as evidence. `observedEnvelope` is the bounding range of returned points, not a machine travel limit.
`OBSERVED_PATH` is an observed-position connection line and does not assert cutting, material removal, collision
safety, or OEM kinematics.

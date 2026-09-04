# Shared Fixtures

Fixtures consumed by more than one runtime belong here with provenance, checksum, derivation status,
and expected meaning.

`regression/mazak01-operational-baseline.json` pins the checked-in Twin, patch, and E2E scenario
bytes plus their established operational meaning. The ignored Canonical output is verified only by
the database/browser E2E publisher, which checks its declared SHA-256 before publishing.

`process-analytics/` contains versioned, manually reviewed derived Machining Run results. Its source
anchors point back to Canonical NIST observations and never represent Production results.

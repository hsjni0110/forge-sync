# Operational Twin v1 Fixtures

## `mazak01-operational-twin.json`

- Derivation status: `DERIVED_FIXTURE`
- Fixture SHA-256: `4ef0abb8a88ba90f01877921bbe0734945a95dd375ab4895c9a95deb3c13ec21`
- Source Artifact: NIST Mazak01
  `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf`
- Source locators: program line 131, execution line 1526, spindle speed line 1530, and tool
  number line 1555, as recorded in the checked-in Source Profile.
- Derivation: the four verified profile observations were assembled into the v1 REST contract by
  hand. `projectedAt`, `evaluatedAt`, and TwinVersion values are deterministic test projection
  metadata; they are not NIST source facts.
- Expected meaning: a contract-complete, FRESH Mazak01 snapshot with REAL:NIST field provenance.
  Empty business sections assert only that those Contexts are outside this fixture; they do not
  assert an absence of real production, Alarm, Maintenance, Intelligence, or spatial information.

## `mazak01-twin-patch.json`

- Derivation status: `DERIVED_FIXTURE`
- Fixture SHA-256: `ac879c8323852669fb8e704958962ffd5d2d8646ebfeb231b000f1c9b4632310`
- Derivation: the operational Twin fixture is wrapped unchanged in the WebSocket v1 whole-snapshot
  envelope with deterministic base/target versions.
- Expected meaning: a continuous patch from TwinVersion 3 to 4 for Mazak01. WebSocket transport is
  not asserted as source provenance; every field retains the nested snapshot provenance.

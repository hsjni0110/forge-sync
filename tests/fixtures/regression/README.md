# Mazak01 Operational Regression Baseline

- Derivation status: `PROJECT_AUTHORED_MANIFEST`
- Manifest SHA-256: `07815623de9ab086d6e7f03f4157eb1787cec063d9acc0ffc64c7bf2fc882085`
- Sources: the checked-in Operational Twin, whole-snapshot patch, and browser E2E scenario listed in
  `mazak01-operational-baseline.json`
- Expected meaning: Step 01~15의 established `Mazak01` Twin identity, version, execution, visual
  spindle selection, RPM, tool number, freshness, and non-physical visual speed mapping
- Canonical source: ignored Canonical output `aee293...9b17f`; its bytes are checked by the E2E
  publisher rather than the root unit suite

The manifest freezes behavior; it is not a new source of manufacturing truth. Update a pinned hash
only with the corresponding behavior or fixture change and its reviewed rationale.

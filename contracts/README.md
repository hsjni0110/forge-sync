# Versioned Contracts

Cross-runtime schemas live here and are versioned independently of language-specific DTOs.

- `observation-envelope/`: canonical SAMPLE, EVENT, and CONDITION envelopes
- `mqtt/`: MQTT 5 Observation delivery contract
- `websocket/`: Twin notification and patch contracts
- `twin/`: authoritative snapshot contracts

Contract definitions are introduced with their producing and consuming behavior, not as empty DTOs.

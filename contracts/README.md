# Versioned Contracts

Cross-runtime schemas live here and are versioned independently of language-specific DTOs.

- `observation-envelope/`: canonical SAMPLE, EVENT, and CONDITION envelopes
- `mqtt/`: MQTT 5 Observation delivery contract
- `replay/`: Replay Session lifecycle and authoritative Replay Cursor contracts
- `process-analytics/`: deterministic Machining Run segmentation results
- `tool-changes/`: cursor-bound observed tool-number transitions
- `websocket/`: Twin notification and patch contracts
- `twin/`: authoritative snapshot contracts

Contract definitions are introduced with their producing and consuming behavior, not as empty DTOs.

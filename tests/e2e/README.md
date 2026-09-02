# End-to-end Tests

Deterministic cross-runtime user journeys belong here. External network and wall-clock dependencies
must be replaced with pinned sources and controlled clocks.

Step 11 currently has Spring WebSocket integration tests and browser-state integration tests with
fake timers. Its browser-level disconnect, STALE, REST resync, and resubscribe journey remains
pending; the roadmap step must stay `IN_PROGRESS` until that scenario is automated here.

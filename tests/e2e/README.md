# End-to-end Tests

Deterministic cross-runtime user journeys belong here. External network and wall-clock dependencies
must be replaced with pinned sources and controlled clocks.

The shared `fixtures/` and `support/` directories hold cross-runtime inputs and publishers. Browser
specifications live under `apps/factory-web/tests/e2e` so their Node dependencies remain owned by
the Web package.

`machine-detail-replay.json` selects three unmodified records from the checksum-pinned canonical
NIST Observation output. The publisher verifies the whole NDJSON checksum, selected line,
DataItem, and expected value before adding only replay identity and publishing through the product
MQTT v5 QoS1 Adapter.

Run the full journey with Docker and installed Playwright Chromium:

```bash
npx --prefix apps/factory-web playwright install chromium
./scripts/verify-e2e
```

The script starts isolated PostgreSQL and Mosquitto containers plus Factory API and Factory Web,
then verifies replay updates, disconnect/STALE behavior, REST resynchronization, WebSocket
resubscription, keyboard focus, and semantic labels. It removes its containers, volumes, processes,
and temporary logs on exit.

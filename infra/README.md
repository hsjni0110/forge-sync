# Infrastructure

Local and deployment infrastructure belongs here. Infrastructure implements application ports and
must not become the owner of domain rules or source truth.

`mqtt/compose.yaml` runs the pinned Mosquitto used only by the explicit MQTT integration check. It
binds to `127.0.0.1:18883`, has no persistence, and permits anonymous access only for this local
test process.

`database/compose.yaml` runs the pinned PostgreSQL 17 + TimescaleDB image used by the explicit
database integration check. It binds to `127.0.0.1:15432`; the database and credentials are local
test values and its volume is removed after verification.

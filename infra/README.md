# Infrastructure

Local and deployment infrastructure belongs here. Infrastructure implements application ports and
must not become the owner of domain rules or source truth.

`mqtt/compose.yaml` runs the pinned Mosquitto used only by the explicit MQTT integration check. It
binds to `127.0.0.1:18883`, has no persistence, and permits anonymous access only for this local
test process.

# ForgeSync

ForgeSync is a real-data manufacturing operations digital twin. The repository preserves source
meaning and provenance while projecting the same authoritative Twin state into operational and
spatial views.

## Prerequisites

- Python 3.12 and [uv](https://docs.astral.sh/uv/)
- Java 21; Gradle is supplied by the Factory API wrapper
- Node.js 22.13+ and npm

## Verify the monorepo

From a clean checkout, run one command:

```bash
./scripts/verify
```

The command installs locked dependencies and runs Python format/lint/type/tests/builds, the Factory
API checks and bootable jar build, and the Factory Web lint/type/tests/architecture/build. It exits
non-zero at the first failed gate and identifies the failed subsystem.

The coding agent must run this command immediately before every push. Hosted GitHub workflow
verification is intentionally not configured at this stage.

The MQTT adapter integration check requires Docker and is intentionally separate:

```bash
./scripts/verify-mqtt
```

The PostgreSQL transaction and concurrent Inbox check also requires Docker and is separate:

```bash
./scripts/verify-database
```

The Machine Detail browser journey requires Docker and Playwright Chromium and is also separate:

```bash
npx --prefix apps/factory-web playwright install chromium
./scripts/verify-e2e
```

## Run applications

To open the local Machine Detail demo with one command:

```bash
./scripts/run-local
```

Open the URL printed by the script and press `Ctrl+C` when finished. The script starts and cleans
up its own PostgreSQL, Mosquitto, Factory API, and Factory Web processes.

```bash
# Existing immutable source acquisition CLI
uv run --package forgesync-edge-gateway forgesync-source --help

# Verify a deterministic replay schedule without publishing messages
uv run --package forgesync-edge-gateway forgesync-replay --help

# Start the local database required by Factory API ingestion
docker compose --file infra/database/compose.yaml up --detach --wait

# Factory API; health is GET http://localhost:8080/actuator/health
apps/factory-api/gradlew -p apps/factory-api bootRun

# Factory Web
npm --prefix apps/factory-web run dev

# Advisory-only AI service; health is GET http://localhost:8000/health
uv run --package forgesync-ai-service uvicorn forgesync_ai.main:app

# Simulator only; choose a different port when running beside the AI service
uv run --package forgesync-virtual-controller uvicorn \
  forgesync_virtual_controller.main:app --port 8001
```

Product scope and implementation constraints are documented in [docs/README.md](docs/README.md).

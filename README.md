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

## Run applications

```bash
# Existing immutable source acquisition CLI
uv run --package forgesync-edge-gateway forgesync-source --help

# Verify a deterministic replay schedule without publishing messages
uv run --package forgesync-edge-gateway forgesync-replay --help

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

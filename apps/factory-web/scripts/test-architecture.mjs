import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";

import { ESLint } from "eslint";

const appRoot = fileURLToPath(new URL("..", import.meta.url));
const fixture = fileURLToPath(
  new URL("../tests/architecture/fixtures/features/twin/domain/invalid.ts", import.meta.url),
);
const rendererFixture = fileURLToPath(
  new URL(
    "../tests/architecture/fixtures/features/factory3d/ui/invalid.ts",
    import.meta.url,
  ),
);
const eslint = new ESLint({ cwd: appRoot, ignore: false });
const [result, rendererResult] = await eslint.lintFiles([fixture, rendererFixture]);

assert.ok(result.errorCount > 0, "intentional domain-to-adapter import was accepted");
assert.ok(
  result.messages.some((message) => message.ruleId === "boundaries/dependencies"),
  "boundary rule did not report the intentional violation",
);
assert.ok(
  rendererResult.messages.some((message) => message.ruleId === "no-restricted-imports"),
  "3D renderer was allowed to import a Twin/backend response type directly",
);

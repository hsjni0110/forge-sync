import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";

import { ESLint } from "eslint";

const appRoot = fileURLToPath(new URL("..", import.meta.url));
const fixture = fileURLToPath(
  new URL("../tests/architecture/fixtures/features/twin/domain/invalid.ts", import.meta.url),
);
const eslint = new ESLint({ cwd: appRoot, ignore: false });
const [result] = await eslint.lintFiles([fixture]);

assert.ok(result.errorCount > 0, "intentional domain-to-adapter import was accepted");
assert.ok(
  result.messages.some((message) => message.ruleId === "boundaries/dependencies"),
  "boundary rule did not report the intentional violation",
);

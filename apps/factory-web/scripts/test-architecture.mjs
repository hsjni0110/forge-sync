import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
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

const proceduralFactory = await readFile(
  new URL("../src/features/factory3d/ui/model/createProceduralMachineModel.ts", import.meta.url),
  "utf8",
);
const runtimeBinding = await readFile(
  new URL("../src/features/factory3d/ui/model/MachineModelBinding.tsx", import.meta.url),
  "utf8",
);

assert.doesNotMatch(
  proceduralFactory,
  /MachineVisualState|MachineVisualPresentation|features\/twin/,
  "procedural geometry factory imported runtime or backend Twin state",
);
assert.doesNotMatch(
  runtimeBinding,
  /(?:Box|Cylinder|Plane|Sphere)Geometry|getObjectByName/,
  "runtime binding created geometry or searched semantic nodes by name",
);

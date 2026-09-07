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

const styles = await readFile(new URL("../src/styles.css", import.meta.url), "utf8");
const indexHtml = await readFile(new URL("../index.html", import.meta.url), "utf8");

assert.match(
  indexHtml,
  /<html lang="ko">/,
  "the Korean interface still declares an English document language",
);

assert.match(
  styles,
  /@font-face\s*\{[^}]*font-family:\s*Pretendard[^}]*url\("\/fonts\/PretendardVariable\.woff2"\)[^}]*\}/s,
  "Pretendard is not self-hosted, so the interface font depends on what the reader happens to have",
);
assert.doesNotMatch(
  styles,
  /font-family:\s*Inter/,
  "Inter is still declared even though no Inter file is shipped",
);

assert.match(
  styles,
  /word-break:\s*keep-all/,
  "Korean words may still break mid-word because keep-all is not applied",
);

const typeScale = [...styles.matchAll(/--fs-type-([a-z0-9-]+):/g)].map((match) => match[1]);
assert.ok(
  typeScale.length >= 6,
  `expected a shared type scale, found ${typeScale.length} type tokens`,
);
const spaceScale = [...styles.matchAll(/--fs-space-([a-z0-9-]+):/g)].map((match) => match[1]);
assert.ok(
  spaceScale.length >= 5,
  `expected a shared spacing scale, found ${spaceScale.length} spacing tokens`,
);

const literalFontSizes = [...styles.matchAll(/font-size:\s*([^;]+);/g)]
  .map((match) => match[1].trim())
  .filter((value) => !value.startsWith("var(--fs-type-") && value !== "inherit");
assert.deepEqual(
  literalFontSizes,
  [],
  `font-size must come from the type scale, found: ${literalFontSizes.join(", ")}`,
);

assert.match(
  styles,
  /\.metric-card strong[^{]*\{[^}]*font-variant-numeric:\s*tabular-nums/s,
  "live metric readouts still use proportional digits, so values jitter as they update",
);

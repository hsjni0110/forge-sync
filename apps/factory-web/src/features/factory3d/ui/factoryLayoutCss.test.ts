import { existsSync, readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

const factoryStylesPath = "src/styles/_factory.scss";
const styles = existsSync(factoryStylesPath) ? readFileSync(factoryStylesPath, "utf8") : "";

describe("factory 3D layout", () => {
  it("names the four desktop investigation areas", () => {
    expect(styles).toMatch(/grid-template-areas:\s*"detail scene inspector"\s*"transport scene inspector"/);
  });

  it("stacks critical state and transport before the scene on narrow screens", () => {
    expect(styles).toMatch(/grid-template-areas:\s*"detail"\s*"transport"\s*"scene"\s*"inspector"/);
    expect(styles).toContain(".scene-canvas-stage");
    expect(styles).toContain("height: 23rem");
  });
});

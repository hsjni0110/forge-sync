import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

const styles = readFileSync("src/styles.css", "utf8");

describe("factory 3D layout", () => {
  it("bounds the two scene-panel children without an unused intrinsic grid row", () => {
    expect(styles).toContain(
      ".factory-console .scene-panel { display: grid; grid-template-rows: auto minmax(0, 1fr); }",
    );
  });

  it("gives the canvas a definite height when split panels stack on a narrow screen", () => {
    expect(styles).toContain(".factory-console .scene-canvas-stage { height: 23rem; }");
  });
});

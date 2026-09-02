import { describe, expect, it } from "vitest";

import type { TwinPatch } from "./twin";
import { decidePatch } from "./versionGuard";

const patch = { baseVersion: 4, targetVersion: 5 } as TwinPatch;

describe("decidePatch", () => {
  it("applies only the next continuous version", () => {
    expect(decidePatch(4, patch)).toBe("APPLY");
  });

  it("ignores duplicate and regressive targets", () => {
    expect(decidePatch(5, patch)).toBe("IGNORE");
    expect(decidePatch(6, patch)).toBe("IGNORE");
  });

  it("requires REST resync for a version gap", () => {
    expect(
      decidePatch(3, { ...patch, baseVersion: 4, targetVersion: 5 }),
    ).toBe("RESYNC");
  });
});

import type { TwinPatch } from "./twin";

export type PatchDecision = "APPLY" | "IGNORE" | "RESYNC";

export function decidePatch(localVersion: number, patch: TwinPatch): PatchDecision {
  if (patch.targetVersion <= localVersion) {
    return "IGNORE";
  }
  if (
    patch.baseVersion !== localVersion ||
    patch.targetVersion !== localVersion + 1
  ) {
    return "RESYNC";
  }
  return "APPLY";
}

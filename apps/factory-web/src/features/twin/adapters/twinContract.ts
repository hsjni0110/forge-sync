import Ajv2020, { type ValidateFunction } from "ajv/dist/2020.js";

import twinSnapshotSchema from "../../../../../../contracts/twin/v1/twin-snapshot.schema.json";
import twinPatchSchema from "../../../../../../contracts/websocket/v1/twin-patch.schema.json";
import replayCursorSchema from "../../../../../../contracts/replay/v1/replay-cursor.schema.json";
import type { TwinPatch, TwinSnapshot } from "../domain/twin";
import type { TwinPatchDecoder } from "../application/ports";

const ajv = new Ajv2020({
  allErrors: true,
  strict: true,
  strictTypes: false,
  validateFormats: true,
});
ajv.addFormat("date-time", {
  type: "string",
  validate: (value: string) => Number.isFinite(Date.parse(value)),
});
ajv.addFormat("uuid", {
  type: "string",
  validate: (value: string) =>
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      value,
    ),
});
ajv.addSchema(replayCursorSchema);
ajv.addSchema(twinSnapshotSchema);
const validateSnapshot = ajv.getSchema<TwinSnapshot>(twinSnapshotSchema.$id);
const validatePatch = ajv.compile<TwinPatch>(twinPatchSchema);

function requireValidator<T>(
  validator: ValidateFunction<T> | undefined,
): ValidateFunction<T> {
  if (!validator) {
    throw new Error("Twin contract validator was not compiled");
  }
  return validator;
}

export function decodeTwinSnapshot(
  document: unknown,
  expectedMachineId?: string,
): TwinSnapshot {
  const validator = requireValidator(validateSnapshot);
  if (!validator(document)) {
    throw new Error("REST Twin snapshot does not satisfy contract v1");
  }
  if (
    (expectedMachineId !== undefined && document.machine.machineId !== expectedMachineId) ||
    document.state.freshness.projectedAt !== document.consistency.projectedAt ||
    document.replayCursor.twinVersion !== document.consistency.twinVersion ||
    document.state.freshness.freshMaxAgeMillis >
      document.state.freshness.laggingMaxAgeMillis
  ) {
    throw new Error("REST Twin snapshot identity or freshness contract is inconsistent");
  }
  return document;
}

export class AjvTwinPatchDecoder implements TwinPatchDecoder {
  decode(message: string, expectedMachineId: string): TwinPatch {
    let document: unknown;
    try {
      document = JSON.parse(message);
    } catch {
      throw new Error("Twin patch is not JSON");
    }
    if (!validatePatch(document)) {
      throw new Error("Twin patch does not satisfy contract v1");
    }
    const patch = document;
    decodeTwinSnapshot(patch.snapshot, expectedMachineId);
    if (
      patch.machineId !== expectedMachineId ||
      patch.snapshot.machine.machineId !== patch.machineId ||
      patch.snapshot.consistency.twinVersion !== patch.targetVersion ||
      patch.targetVersion !== patch.baseVersion + 1 ||
      patch.snapshot.consistency.projectedAt !== patch.projectedAt
    ) {
      throw new Error("Twin patch identity or version is inconsistent");
    }
    return patch;
  }
}

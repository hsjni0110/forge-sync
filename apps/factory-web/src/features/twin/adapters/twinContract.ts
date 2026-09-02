import Ajv2020, { type ValidateFunction } from "ajv/dist/2020.js";

import twinSnapshotSchema from "../../../../../../contracts/twin/v1/twin-snapshot.schema.json";
import twinPatchSchema from "../../../../../../contracts/websocket/v1/twin-patch.schema.json";
import type { TwinPatch, TwinSnapshot } from "../domain/twin";
import type { TwinPatchDecoder } from "../application/ports";

const ajv = new Ajv2020({
  allErrors: true,
  strict: true,
  strictTypes: false,
  validateFormats: false,
});
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

export function decodeTwinSnapshot(document: unknown): TwinSnapshot {
  const validator = requireValidator(validateSnapshot);
  if (!validator(document)) {
    throw new Error("REST Twin snapshot does not satisfy contract v1");
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

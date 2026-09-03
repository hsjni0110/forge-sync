import Ajv2020 from "ajv/dist/2020.js";

import factoryAssetManifestSchema from "../../../../../../contracts/factory-assets/v1/factory-asset-manifest.schema.json";
import type { FactoryAssetManifest } from "../domain/factoryAsset";

const ajv = new Ajv2020({
  allErrors: true,
  strict: true,
  strictTypes: false,
});
ajv.addFormat("uri-reference", {
  type: "string",
  validate: (value: string) => {
    if (/\s/.test(value)) {
      return false;
    }
    try {
      new URL(value, "https://forgesync.invalid");
      return true;
    } catch {
      return false;
    }
  },
});
const validateManifest = ajv.compile<FactoryAssetManifest>(factoryAssetManifestSchema);

export function decodeFactoryAssetManifest(document: unknown): FactoryAssetManifest {
  if (!validateManifest(document)) {
    throw new Error("Factory asset manifest does not satisfy contract v1");
  }
  return document;
}

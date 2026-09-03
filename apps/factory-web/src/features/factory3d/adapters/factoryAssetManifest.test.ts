import { describe, expect, it } from "vitest";

import defaultManifest from "./defaultFactoryAssetManifest.json";
import { decodeFactoryAssetManifest } from "./factoryAssetManifest";

describe("Factory asset manifest", () => {
  it("accepts the project procedural asset with explicit license evidence", () => {
    const manifest = decodeFactoryAssetManifest(structuredClone(defaultManifest));

    expect(manifest.assets[0]).toMatchObject({
      assetId: "cnc-generic-primitive-v1",
      representation: "PROCEDURAL",
      origin: "PROJECT_PROCEDURAL",
      license: { expression: "NOASSERTION", evidenceState: "TO_VERIFY" },
    });
  });

  it("rejects an unknown version and incomplete GLB provenance", () => {
    expect(() =>
      decodeFactoryAssetManifest({ ...structuredClone(defaultManifest), schemaVersion: "2.0.0" }),
    ).toThrow(/contract v1/);

    expect(() =>
      decodeFactoryAssetManifest({
        schemaVersion: "1.0.0",
        assets: [
          {
            assetId: "external-cnc",
            displayName: "External CNC",
            representation: "GLB",
            origin: "THIRD_PARTY",
            uri: "/assets/external-cnc.glb",
            license: {
              expression: "NOASSERTION",
              evidenceState: "TO_VERIFY",
              attribution: "Unknown",
            },
          },
        ],
      }),
    ).toThrow(/contract v1/);
  });
});

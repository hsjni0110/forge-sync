export type LicenseEvidenceState =
  | "VERIFIED"
  | "TO_VERIFY"
  | "BLOCKED"
  | "NOT_APPLICABLE";

export interface FactoryAssetLicense {
  expression: string;
  evidenceState: LicenseEvidenceState;
  attribution: string;
  licenseUri?: string;
}

interface FactoryAssetIdentity {
  assetId: string;
  displayName: string;
  license: FactoryAssetLicense;
}

export interface ProceduralFactoryAsset extends FactoryAssetIdentity {
  representation: "PROCEDURAL";
  origin: "PROJECT_PROCEDURAL";
  sourceLocator: string;
}

export interface GlbFactoryAsset extends FactoryAssetIdentity {
  representation: "GLB";
  origin: "THIRD_PARTY";
  uri: string;
  sha256: string;
  byteLength: number;
}

export type FactoryAsset = ProceduralFactoryAsset | GlbFactoryAsset;

export interface FactoryAssetManifest {
  schemaVersion: "1.0.0";
  assets: FactoryAsset[];
}

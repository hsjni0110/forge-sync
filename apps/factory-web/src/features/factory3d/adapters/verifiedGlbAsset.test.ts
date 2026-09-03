import { describe, expect, it, vi } from "vitest";

import type { GlbFactoryAsset } from "../domain/factoryAsset";
import { verifyGlbAssetBytes } from "./verifiedGlbAsset";

describe("verified GLB asset bytes", () => {
  it("accepts bytes matching the manifest identity", async () => {
    const bytes = new Uint8Array([1, 2, 3]).buffer;
    const digestSha256 = vi.fn(async () => asset.sha256);

    await expect(verifyGlbAssetBytes(asset, bytes, digestSha256)).resolves.toBe(bytes);
    expect(digestSha256).toHaveBeenCalledWith(bytes);
  });

  it("rejects a changed byte length before parsing", async () => {
    const digestSha256 = vi.fn(async () => asset.sha256);

    await expect(
      verifyGlbAssetBytes(asset, new Uint8Array([1, 2]).buffer, digestSha256),
    ).rejects.toThrow(/byte length/);
    expect(digestSha256).not.toHaveBeenCalled();
  });

  it("rejects bytes whose SHA-256 differs from the manifest", async () => {
    const bytes = new Uint8Array([1, 2, 3]).buffer;

    await expect(
      verifyGlbAssetBytes(asset, bytes, async () => "f".repeat(64)),
    ).rejects.toThrow(/SHA-256/);
  });
});

const asset: GlbFactoryAsset = {
  assetId: "verified-cnc",
  displayName: "Verified CNC",
  representation: "GLB",
  origin: "THIRD_PARTY",
  uri: "/assets/verified-cnc.glb",
  byteLength: 3,
  sha256: "0".repeat(64),
  license: {
    expression: "LicenseRef-Test",
    evidenceState: "VERIFIED",
    attribution: "Test fixture",
  },
};

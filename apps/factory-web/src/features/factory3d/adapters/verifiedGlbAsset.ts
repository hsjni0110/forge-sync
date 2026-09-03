import { GLTFLoader, type GLTF } from "three/addons/loaders/GLTFLoader.js";

import type { GlbFactoryAsset } from "../domain/factoryAsset";

type DigestSha256 = (bytes: ArrayBuffer) => Promise<string>;

const loadedModels = new Map<string, Promise<GLTF>>();

export function loadVerifiedGlbAsset(asset: GlbFactoryAsset): Promise<GLTF> {
  const cacheKey = `${asset.uri}:${asset.byteLength}:${asset.sha256}`;
  const cached = loadedModels.get(cacheKey);
  if (cached) {
    return cached;
  }

  const loading = fetchAndParseGlb(asset);
  loadedModels.set(cacheKey, loading);
  return loading;
}

export async function verifyGlbAssetBytes(
  asset: GlbFactoryAsset,
  bytes: ArrayBuffer,
  digestSha256: DigestSha256 = browserSha256,
): Promise<ArrayBuffer> {
  if (bytes.byteLength !== asset.byteLength) {
    throw new Error(`GLB byte length does not match manifest for ${asset.assetId}`);
  }
  if ((await digestSha256(bytes)) !== asset.sha256) {
    throw new Error(`GLB SHA-256 does not match manifest for ${asset.assetId}`);
  }
  return bytes;
}

async function fetchAndParseGlb(asset: GlbFactoryAsset): Promise<GLTF> {
  const resolvedUri = new URL(asset.uri, document.baseURI);
  const response = await fetch(resolvedUri);
  if (!response.ok) {
    throw new Error(`GLB request failed with status ${response.status}`);
  }

  const bytes = await verifyGlbAssetBytes(asset, await response.arrayBuffer());
  return new GLTFLoader().parseAsync(bytes, new URL(".", resolvedUri).href);
}

async function browserSha256(bytes: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (value) =>
    value.toString(16).padStart(2, "0"),
  ).join("");
}

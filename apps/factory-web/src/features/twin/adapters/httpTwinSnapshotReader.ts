import type { TwinSnapshotReader } from "../application/ports";
import type { TwinSnapshot } from "../domain/twin";
import { decodeTwinSnapshot } from "./twinContract";

const TWIN_MEDIA_TYPE = "application/vnd.forgesync.twin.v1+json";

export class HttpTwinSnapshotReader implements TwinSnapshotReader {
  constructor(private readonly apiBaseUrl = "") {}

  async loadSnapshot(machineId: string): Promise<TwinSnapshot> {
    const response = await fetch(
      `${this.apiBaseUrl}/api/v1/machines/${encodeURIComponent(machineId)}/twin`,
      { headers: { Accept: TWIN_MEDIA_TYPE } },
    );
    if (!response.ok) {
      throw new Error(`Twin snapshot request failed with status ${response.status}`);
    }
    return decodeTwinSnapshot(await response.json());
  }
}

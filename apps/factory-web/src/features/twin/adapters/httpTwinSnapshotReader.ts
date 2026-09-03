import type { TwinSnapshotReader } from "../application/ports";
import type { TwinSnapshot } from "../domain/twin";
import { TwinSnapshotLoadError } from "../application/errors";
import { decodeTwinSnapshot } from "./twinContract";

const TWIN_MEDIA_TYPE = "application/vnd.forgesync.twin.v1+json";

export class HttpTwinSnapshotReader implements TwinSnapshotReader {
  constructor(private readonly apiBaseUrl = "") {}

  async loadSnapshot(machineId: string): Promise<TwinSnapshot> {
    let response: Response;
    try {
      response = await fetch(
        `${this.apiBaseUrl}/api/v1/machines/${encodeURIComponent(machineId)}/twin`,
        { headers: { Accept: TWIN_MEDIA_TYPE } },
      );
    } catch (error) {
      throw new TwinSnapshotLoadError("NETWORK", "Twin snapshot network request failed", {
        cause: error,
      });
    }
    if (response.status === 404) {
      throw new TwinSnapshotLoadError("NOT_FOUND", `Machine ${machineId} was not found`);
    }
    if (response.status === 503) {
      throw new TwinSnapshotLoadError(
        "TEMPORARILY_UNAVAILABLE",
        "Twin snapshot is temporarily unavailable",
      );
    }
    if (!response.ok) {
      throw new TwinSnapshotLoadError(
        "NETWORK",
        `Twin snapshot request failed with status ${response.status}`,
      );
    }
    try {
      return decodeTwinSnapshot(await response.json(), machineId);
    } catch (error) {
      throw new TwinSnapshotLoadError("INVALID_CONTRACT", "Twin snapshot contract is invalid", {
        cause: error,
      });
    }
  }
}

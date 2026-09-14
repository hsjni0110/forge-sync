import type { ShiftOverview } from "../domain/shiftOverview";

export interface ShiftOverviewClient {
  load(
    machineId: string,
    replaySessionId: string,
    throughReplaySequence: number,
    signal?: AbortSignal,
  ): Promise<ShiftOverview>;
}

export class ShiftOverviewError extends Error {
  constructor(
    readonly code: "NETWORK" | "INSUFFICIENT_DATA" | "VERSION_MISMATCH",
    detail?: string,
  ) {
    super(detail ?? code);
  }
}

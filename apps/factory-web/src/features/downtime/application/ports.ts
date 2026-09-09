import type { DowntimeParetoReport } from "../domain/downtimePareto";

export interface DowntimeParetoClient {
  analyze(
    machineId: string,
    replaySessionId: string,
    throughReplaySequence: number,
    signal?: AbortSignal,
  ): Promise<DowntimeParetoReport>;
}

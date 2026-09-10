import type { OperationalEffectivenessReport } from "../domain/operationalEffectiveness";

export interface OperationalEffectivenessClient {
  analyze(
    machineId: string,
    replaySessionId: string,
    throughReplaySequence: number,
    signal?: AbortSignal,
  ): Promise<OperationalEffectivenessReport>;
}

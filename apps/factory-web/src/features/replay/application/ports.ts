import type { ReplaySessionState, ReplaySpeed } from "../domain/replay";

export interface ReplayControlClient {
  load(machineId: string): Promise<ReplaySessionState>;
  start(machineId: string, sourceSetId: string, speed: ReplaySpeed): Promise<ReplaySessionState>;
  pause(sessionId: string, revision: number): Promise<ReplaySessionState>;
  resume(sessionId: string, revision: number): Promise<ReplaySessionState>;
  changeSpeed(
    sessionId: string,
    revision: number,
    speed: ReplaySpeed,
  ): Promise<ReplaySessionState>;
  seek(
    sessionId: string,
    revision: number,
    sourceObservedAt: string,
    speed: ReplaySpeed,
  ): Promise<ReplaySessionState>;
}

import type { ObservedToolpathDocument } from "../domain/observedToolpath";

export interface ObservedToolpathRequest {
  machineId: string;
  replaySessionId: string;
  startSequence: number;
  endSequence: number;
  throughReplaySequence: number;
  resetKey?: string;
}

export interface ObservedToolpathClient {
  find(request: ObservedToolpathRequest, signal: AbortSignal): Promise<ObservedToolpathDocument>;
}

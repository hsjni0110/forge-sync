export type ReplayStatus =
  | "PREPARING"
  | "RUNNING"
  | "PAUSED"
  | "SEEKING"
  | "COMPLETED"
  | "FAILED";

export type ReplaySpeed = 1 | 10 | 100;

export interface ReplaySessionState {
  schemaVersion: "1.0.0";
  replaySessionId: string;
  machineId: string;
  sourceSetId: string;
  status: ReplayStatus;
  speedMultiplier: ReplaySpeed;
  revision: number;
  sourceRange: { startsAt: string; endsAt: string };
  publicationCursor?: {
    replaySequence: number;
    sourceObservedAt: string;
    replayPublishedAt: string;
  };
  failure?: { code: string; message: string; retryable: boolean };
}

import type { ToolChangeTimeline } from "../domain/toolChange";

export interface ToolChangeClient {
  find(machineId: string, replaySessionId: string, throughReplaySequence: number): Promise<ToolChangeTimeline>;
}

import type { Alarm, AlarmTimeline } from "../domain/alarm";

export interface AlarmClient {
  find(
    machineId: string,
    replaySessionId: string,
    throughReplaySequence: number,
    signal?: AbortSignal,
  ): Promise<AlarmTimeline>;

  acknowledge(alarmId: string, expectedRevision: number, operatorName: string): Promise<Alarm>;
}

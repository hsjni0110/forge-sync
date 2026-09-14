export type AlarmSeverity = "WARNING" | "CRITICAL";
export type AlarmStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED";

export interface Alarm {
  alarmId: string;
  machineId: string;
  replaySessionId: string;
  openedReplaySequence: number;
  openedAt: string;
  sourceDataItemId: string;
  componentId: string;
  conditionType: string;
  nativeCode: string;
  message: string | null;
  severity: AlarmSeverity;
  status: AlarmStatus;
  ruleVersion: string;
  revision: number;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  resolvedReplaySequence: number | null;
  resolvedAt: string | null;
  source: "EQUIPMENT_CONDITION";
}

export interface AlarmTimeline {
  schemaVersion: "1.0.0";
  machineId: string;
  replaySessionId: string;
  throughReplaySequence: number;
  alarms: Alarm[];
}

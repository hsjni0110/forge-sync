import Ajv2020 from "ajv/dist/2020.js";

import schema from "../../../../../../contracts/alarm/v1/alarm-timeline.schema.json";
import type { AlarmClient } from "../application/ports";
import type { Alarm, AlarmTimeline } from "../domain/alarm";

const ajv = new Ajv2020({ strict: true });
ajv.addFormat("date-time", { type: "string", validate: (value: string) => Number.isFinite(Date.parse(value)) });
ajv.addFormat("uuid", /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
const validateTimeline = ajv.compile<AlarmTimeline>(schema);

export class HttpAlarmClient implements AlarmClient {
  constructor(private readonly baseUrl: string) {}

  async find(
    machineId: string,
    replaySessionId: string,
    throughReplaySequence: number,
    signal?: AbortSignal,
  ): Promise<AlarmTimeline> {
    const query = new URLSearchParams({ replaySessionId, throughReplaySequence: String(throughReplaySequence) });
    const response = await fetch(
      `${this.baseUrl}/api/v1/machines/${encodeURIComponent(machineId)}/alarms?${query}`,
      { signal, headers: { Accept: "application/vnd.forgesync.alarms.v1+json" } },
    );
    if (!response.ok) throw new Error(`Alarm timeline failed (${response.status})`);
    const document: unknown = await response.json();
    if (!validateTimeline(document)) throw new Error("Alarm timeline contract is invalid");
    return document;
  }

  async acknowledge(
    alarmId: string,
    expectedRevision: number,
    operatorName: string,
  ): Promise<Alarm> {
    const response = await fetch(
      `${this.baseUrl}/api/v1/alarms/${encodeURIComponent(alarmId)}/acknowledge`,
      {
        method: "POST",
        headers: {
          Accept: "application/vnd.forgesync.alarms.v1+json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ expectedRevision, operatorName }),
      },
    );
    if (!response.ok) throw new Error(`Alarm acknowledgement failed (${response.status})`);
    const alarm: unknown = await response.json();
    const candidate = alarm as Partial<Alarm>;
    const envelope = {
      schemaVersion: "1.0.0",
      machineId: candidate.machineId,
      replaySessionId: candidate.replaySessionId,
      throughReplaySequence: candidate.openedReplaySequence,
      alarms: [alarm],
    };
    if (!validateTimeline(envelope)) throw new Error("Alarm acknowledgement contract is invalid");
    return alarm as Alarm;
  }
}

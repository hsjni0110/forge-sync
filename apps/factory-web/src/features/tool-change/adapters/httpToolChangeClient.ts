import Ajv2020 from "ajv/dist/2020.js";

import schema from "../../../../../../contracts/tool-changes/v1/tool-change-timeline.schema.json";
import type { ToolChangeClient } from "../application/ports";
import type { ToolChangeTimeline } from "../domain/toolChange";

const ajv = new Ajv2020({ strict: true });
ajv.addFormat("date-time", { type: "string", validate: (value: string) => Number.isFinite(Date.parse(value)) });
ajv.addFormat("uuid", /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
const validate = ajv.compile<ToolChangeTimeline>(schema);

export class HttpToolChangeClient implements ToolChangeClient {
  constructor(private readonly baseUrl: string) {}

  async find(machineId: string, replaySessionId: string, throughReplaySequence: number) {
    const query = new URLSearchParams({ replaySessionId, throughReplaySequence: String(throughReplaySequence) });
    const response = await fetch(`${this.baseUrl}/api/v1/machines/${encodeURIComponent(machineId)}/tool-changes?${query}`, {
      headers: { Accept: "application/vnd.forgesync.tool-changes.v1+json" },
    });
    if (!response.ok) throw new Error(`Tool change timeline failed (${response.status})`);
    const document: unknown = await response.json();
    if (!validate(document)) throw new Error("Tool change timeline contract is invalid");
    return document;
  }
}

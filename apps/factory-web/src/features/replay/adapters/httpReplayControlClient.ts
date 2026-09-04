import Ajv2020 from "ajv/dist/2020.js";

import replaySessionSchema from "../../../../../../contracts/replay/v1/replay-session.schema.json";
import type { ReplayControlClient } from "../application/ports";
import type { ReplaySessionState, ReplaySpeed } from "../domain/replay";

const ajv = new Ajv2020({ allErrors: true, strict: true, validateFormats: true });
ajv.addFormat("date-time", {
  type: "string",
  validate: (value: string) => Number.isFinite(Date.parse(value)),
});
ajv.addFormat("uuid", {
  type: "string",
  validate: (value: string) =>
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      value,
    ),
});
const validateSession = ajv.compile<ReplaySessionState>(replaySessionSchema);

export function decodeReplaySession(document: unknown): ReplaySessionState {
  if (!validateSession(document)) {
    throw new Error("Replay response does not satisfy contract v1");
  }
  if (Date.parse(document.sourceRange.startsAt) > Date.parse(document.sourceRange.endsAt)) {
    throw new Error("Replay source range is inconsistent");
  }
  return document;
}

export class HttpReplayControlClient implements ReplayControlClient {
  constructor(private readonly apiBaseUrl: string) {}

  load(machineId: string): Promise<ReplaySessionState> {
    return this.request(`/api/v1/machines/${encodeURIComponent(machineId)}/replay-session`);
  }

  start(machineId: string, sourceSetId: string, speed: ReplaySpeed) {
    return this.request("/api/v1/replay-sessions", "POST", {
      machineId,
      sourceSetId,
      speedMultiplier: speed,
    });
  }

  pause(sessionId: string, revision: number) {
    return this.command(sessionId, "pause", { expectedRevision: revision });
  }

  resume(sessionId: string, revision: number) {
    return this.command(sessionId, "resume", { expectedRevision: revision });
  }

  changeSpeed(sessionId: string, revision: number, speed: ReplaySpeed) {
    return this.request(`/api/v1/replay-sessions/${encodeURIComponent(sessionId)}/speed`, "PUT", {
      expectedRevision: revision,
      speedMultiplier: speed,
    });
  }

  seek(
    sessionId: string,
    revision: number,
    sourceObservedAt: string,
    speed: ReplaySpeed,
  ) {
    return this.command(sessionId, "seek", {
      expectedRevision: revision,
      sourceObservedAt,
      speedMultiplier: speed,
    });
  }

  private command(sessionId: string, action: string, body: object) {
    return this.request(
      `/api/v1/replay-sessions/${encodeURIComponent(sessionId)}/${action}`,
      "POST",
      body,
    );
  }

  private async request(path: string, method = "GET", body?: object) {
    const response = await fetch(`${this.apiBaseUrl}${path}`, {
      method,
      headers: {
        Accept: "application/vnd.forgesync.replay-session.v1+json",
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!response.ok) {
      throw new Error(`Replay request failed with ${response.status}`);
    }
    const document: unknown = await response.json();
    return decodeReplaySession(document);
  }
}

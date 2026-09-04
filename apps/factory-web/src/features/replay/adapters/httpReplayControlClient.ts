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

  async load(machineId: string): Promise<ReplaySessionState | undefined> {
    const response = await this.send(
      `/api/v1/machines/${encodeURIComponent(machineId)}/replay-session`,
    );
    if (!response.ok) {
      const failure = await replayRequestFailure(response);
      if (failure.status === 404 && failure.code === "REPLAY_SESSION_NOT_FOUND") {
        return undefined;
      }
      throw failure;
    }
    return decodeReplaySession(await response.json());
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
    const response = await this.send(path, method, body);
    if (!response.ok) {
      throw await replayRequestFailure(response);
    }
    const document: unknown = await response.json();
    return decodeReplaySession(document);
  }

  private send(path: string, method = "GET", body?: object) {
    return fetch(`${this.apiBaseUrl}${path}`, {
      method,
      headers: {
        Accept: "application/vnd.forgesync.replay-session.v1+json",
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  }
}

export class ReplayControlRequestError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
  ) {
    super(`Replay request failed with ${status}${code ? ` (${code})` : ""}`);
    this.name = "ReplayControlRequestError";
  }
}

async function replayRequestFailure(response: Response): Promise<ReplayControlRequestError> {
  let code: string | undefined;
  try {
    const problem: unknown = await response.json();
    if (isRecord(problem) && typeof problem.code === "string") {
      code = problem.code;
    }
  } catch {
    // A non-JSON error response still retains its HTTP status for presentation and diagnostics.
  }
  return new ReplayControlRequestError(response.status, code);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

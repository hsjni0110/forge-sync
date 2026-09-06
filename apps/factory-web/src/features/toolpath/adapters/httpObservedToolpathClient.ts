import Ajv2020 from "ajv/dist/2020.js";

import observedToolpathSchema from "../../../../../../contracts/toolpath/v1/observed-toolpath.schema.json";
import type { ObservedToolpathClient, ObservedToolpathRequest } from "../application/ports";
import type { ObservedToolpathDocument } from "../domain/observedToolpath";

const ajv = new Ajv2020({ allErrors: true, strict: true, strictTypes: false, validateFormats: false });
const validateToolpath = ajv.compile<ObservedToolpathDocument>(observedToolpathSchema);

export class HttpObservedToolpathClient implements ObservedToolpathClient {
  constructor(private readonly apiBaseUrl = "") {}

  async find(request: ObservedToolpathRequest, signal: AbortSignal): Promise<ObservedToolpathDocument> {
    const query = new URLSearchParams({
      replaySessionId: request.replaySessionId,
      startSequence: String(request.startSequence),
      endSequence: String(request.endSequence),
      throughReplaySequence: String(request.throughReplaySequence),
    });
    const response = await fetch(
      `${this.apiBaseUrl}/api/v1/machines/${encodeURIComponent(request.machineId)}/observed-toolpath?${query}`,
      { headers: { Accept: "application/vnd.forgesync.observed-toolpath.v1+json" }, signal },
    );
    if (!response.ok) throw new Error(`Observed toolpath request failed: ${response.status}`);
    const document: unknown = await response.json();
    if (!validateToolpath(document) || document.machineId !== request.machineId
      || document.replaySessionId !== request.replaySessionId
      || document.startSequence !== request.startSequence
      || document.endSequence > request.endSequence
      || document.endSequence > request.throughReplaySequence) {
      throw new Error("Observed toolpath contract mismatch");
    }
    return document;
  }
}

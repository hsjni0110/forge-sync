import { ProcessAnalysisError, type ProcessAnalysisClient, type ProcessingReferences } from "../application/ports";
import type { ProcessCursor, RunAnalysis } from "../domain/processAnalysis";
import { decodeRuns, decodeFeatures, decodeAssessments } from "./processContract";
import { mapProcessAnalysis } from "./mapProcessAnalysis";
import { requireMatch, validateRunIdentity, validateFeatureIdentity, validateAssessmentIdentity } from "./processIdentityGuard";

interface ProcessingRequest {
  resource: "machining-runs" | "cycle-features" | "anomaly-assessments";
  body: object;
  existingId?: string;
}

const QUERY_KEYS = {
  "machining-runs": "processingRunId", "cycle-features": "featureProcessingRunId",
  "anomaly-assessments": "assessmentProcessingRunId",
};

export class HttpProcessAnalysisClient implements ProcessAnalysisClient {
  constructor(private readonly apiBaseUrl: string) {}

  async analyze(machineId: string, cursor: ProcessCursor, signal: AbortSignal, existing?: ProcessingReferences): Promise<RunAnalysis> {
    const runs = decodeRuns(await this.request(machineId, signal, {
      resource: "machining-runs", existingId: existing?.processingId,
      body: { replaySessionId: cursor.replaySessionId, throughReplaySequence: cursor.replaySequence,
        segmentationRuleVersion: "1.0.0" },
    }));
    validateRunIdentity(runs, machineId, cursor);
    requireMatch(!existing || runs.processingRunId === existing.processingId);
    const features = decodeFeatures(await this.request(machineId, signal, {
      resource: "cycle-features", existingId: existing?.featureProcessingId,
      body: { machiningRunProcessingRunId: runs.processingRunId, cycleFeatureVersion: "1.0.0" },
    }));
    validateFeatureIdentity(features, runs);
    requireMatch(!existing || features.featureProcessingRunId === existing.featureProcessingId);
    const assessments = decodeAssessments(await this.request(machineId, signal, {
      resource: "anomaly-assessments", existingId: existing?.assessmentProcessingId,
      body: { cycleFeatureProcessingRunId: features.featureProcessingRunId,
        baselinePolicyVersion: "1.0.0", anomalyAssessmentVersion: "2.0.0" },
    }));
    validateAssessmentIdentity(assessments, features);
    requireMatch(!existing || assessments.assessmentProcessingRunId === existing.assessmentProcessingId);
    return mapProcessAnalysis(runs, features, assessments);
  }

  private async request(machineId: string, signal: AbortSignal, request: ProcessingRequest): Promise<unknown> {
    signal.throwIfAborted();
    const { resource, existingId, body } = request;
    if (existingId !== undefined) requireMatch(/^sha256:[0-9a-f]{64}$/.test(existingId));
    const suffix = existingId === undefined ? "/processing-runs"
      : `?${QUERY_KEYS[resource]}=${encodeURIComponent(existingId)}`;
    let response: Response;
    try {
      response = await fetch(`${this.apiBaseUrl}/api/v1/machines/${encodeURIComponent(machineId)}/${resource}${suffix}`, {
        method: existingId === undefined ? "POST" : "GET", signal,
        headers: { Accept: `application/vnd.forgesync.${resource}.v1+json`,
          ...(existingId === undefined ? { "Content-Type": "application/json" } : {}) },
        body: existingId === undefined ? JSON.stringify(body) : undefined,
      });
    } catch {
      signal.throwIfAborted();
      throw new ProcessAnalysisError("NETWORK");
    }
    if (!response.ok) {
      const problem: unknown = await response.json().catch(() => undefined);
      const inputMissing = typeof problem === "object" && problem !== null && "code" in problem &&
        problem.code === "MACHINING_RUN_INPUT_NOT_FOUND";
      throw new ProcessAnalysisError(resource === "machining-runs" && existingId === undefined &&
        response.status === 404 && inputMissing ? "INPUT_NOT_FOUND" : "NETWORK");
    }
    return response.json().catch(() => { throw new ProcessAnalysisError("VERSION_MISMATCH"); });
  }
}

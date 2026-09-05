import { afterEach, describe, expect, it, vi } from "vitest";
import runFixture from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-machining-runs.json";
import featureFixture from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-cycle-features.json";
import assessmentFixture from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-anomaly-assessments.json";
import { HttpProcessAnalysisClient } from "./httpProcessAnalysisClient";

afterEach(() => vi.unstubAllGlobals());
const client = new HttpProcessAnalysisClient("");
const cursor = { replaySessionId: runFixture.replaySessionId, replaySequence: 1509,
  sourceObservedAt: "2016-10-05T09:20:59.181Z", replayPublishedAt: "2026-09-05T00:00:00Z", twinVersion: 10 };

// DERIVED_FIXTURE: join the reviewed, independently scoped schema fixtures using their
// fixed contract identities. Values are illustrative and are not a production calculation.
function joinedDocuments() {
  const runs = structuredClone(runFixture);
  const features = structuredClone(featureFixture);
  const assessments = structuredClone(assessmentFixture);
  features.machiningRunProcessingRunId = runs.processingRunId;
  features.featureSets[0].machiningRunId = runs.machiningRuns[0].machiningRunId;
  features.featureSets[0].aggregationWindow.startedAt = runs.machiningRuns[0].startedAt;
  features.featureSets[0].aggregationWindow.endedAt = runs.machiningRuns[0].endedAt;
  assessments.machiningRunProcessingRunId = runs.processingRunId;
  assessments.cycleFeatureProcessingRunId = features.featureProcessingRunId;
  const assessment = assessments.assessments[0];
  assessment.machiningRunId = runs.machiningRuns[0].machiningRunId;
  assessment.targetFeatureSetId = features.featureSets[0].cycleFeatureSetId;
  assessment.baseline.targetFeatureSetId = assessment.targetFeatureSetId;
  assessment.lineage.inputCycleFeature.featureProcessingRunId = features.featureProcessingRunId;
  return { runs, features, assessments };
}

function serve(documents: ReturnType<typeof joinedDocuments>) {
  const fetch = vi.fn(async (url: string, options?: RequestInit) => {
    expect(options?.headers).toBeDefined();
    return new Response(JSON.stringify(url.includes("cycle-features") ? documents.features :
      url.includes("anomaly-assessments") ? documents.assessments : documents.runs));
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

describe("Process Analytics HTTP composition", () => {
  it("reads an explicitly referenced immutable bundle without creating processing results", async () => {
    const documents = joinedDocuments();
    const fetch = serve(documents);
    const references = { processingId: documents.runs.processingRunId,
      featureProcessingId: documents.features.featureProcessingRunId,
      assessmentProcessingId: documents.assessments.assessmentProcessingRunId };
    const result = await Reflect.apply(client.analyze, client, ["Mazak01", cursor, new AbortController().signal, references]);
    expect(result.processingId).toBe(references.processingId);
    expect(fetch.mock.calls.map(([url, options]) => [url, options?.method])).toEqual([
      [`/api/v1/machines/Mazak01/machining-runs?processingRunId=${encodeURIComponent(references.processingId)}`, "GET"],
      [`/api/v1/machines/Mazak01/cycle-features?featureProcessingRunId=${encodeURIComponent(references.featureProcessingId)}`, "GET"],
      [`/api/v1/machines/Mazak01/anomaly-assessments?assessmentProcessingRunId=${encodeURIComponent(references.assessmentProcessingId)}`, "GET"],
    ]);
  });

  it("rejects a completed run with missing feature output rather than claiming it is unfinished", async () => {
    const documents = joinedDocuments();
    documents.features.featureSets = [];
    documents.features.eligibleRunCount = 0;
    documents.assessments.assessments = [];
    serve(documents);
    await expect(client.analyze("Mazak01", cursor, new AbortController().signal)).rejects.toThrow("VERSION_MISMATCH");
  });

  it("rejects duplicate feature identities and boundaries beyond the watermark", async () => {
    const documents = joinedDocuments();
    documents.features.featureSets.push(documents.features.featureSets[0]);
    serve(documents);
    await expect(client.analyze("Mazak01", cursor, new AbortController().signal)).rejects.toThrow("VERSION_MISMATCH");
    documents.features.featureSets.pop();
    documents.runs.machiningRuns[0].endEvidence.replaySequence = cursor.replaySequence + 1;
    await expect(client.analyze("Mazak01", cursor, new AbortController().signal)).rejects.toThrow("VERSION_MISMATCH");
  });
});

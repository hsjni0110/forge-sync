import { describe, expect, it } from "vitest";
import runs from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-machining-runs.json";
import features from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-cycle-features.json";
import assessments from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-anomaly-assessments.json";
import { decodeRuns, decodeFeatures, decodeAssessments } from "./processContract";

describe("shared Process Analytics v1 contracts", () => {
  it("accepts the same reviewed fixtures as the Java producer and independent Python consumer", () => {
    expect(decodeRuns(runs)).toEqual(runs);
    expect(decodeFeatures(features)).toEqual(features);
    expect(decodeAssessments(assessments)).toEqual(assessments);
  });

  it.each([0.123456, 148.734, 74.833148])("accepts decimal %s without binary division errors", (value) => {
    const document = structuredClone(assessments);
    document.assessments[0].baseline.featureBaselines[0].targetValue = value;
    expect(() => decodeAssessments(document)).not.toThrow();
  });

  it("rejects unsupported versions, forged provenance and excessive decimal precision", () => {
    expect(() => decodeRuns({ ...runs, schemaVersion: "2.0.0" })).toThrow("VERSION_MISMATCH");
    const source = structuredClone(runs);
    source.machiningRuns[0].provenance.source.kind = "SIM";
    expect(() => decodeRuns(source)).toThrow("VERSION_MISMATCH");
    const decimal = structuredClone(assessments);
    decimal.assessments[0].baseline.featureBaselines[0].targetValue = 0.1234567;
    expect(() => decodeAssessments(decimal)).toThrow("VERSION_MISMATCH");
  });
});

import { describe, expect, it } from "vitest";
import {
  assessmentUnavailableExplanation, limitingFeatureBaseline, sampleProgressLabel,
} from "./processAnalysis";

describe("limitingFeatureBaseline", () => {
  it("returns undefined when no feature is pending a minimum sample count", () => {
    expect(limitingFeatureBaseline([
      { feature: "durationSeconds", sampleCount: 12, unavailableReason: null },
    ])).toBeUndefined();
    expect(limitingFeatureBaseline()).toBeUndefined();
  });

  it("prefers durationSeconds since it is always eligible for comparison", () => {
    const result = limitingFeatureBaseline([
      { feature: "metric|SPINDLE_SPEED|mean", sampleCount: 4, unavailableReason: "MINIMUM_SAMPLE_COUNT_NOT_MET" },
      { feature: "durationSeconds", sampleCount: 2, unavailableReason: "MINIMUM_SAMPLE_COUNT_NOT_MET" },
    ]);
    expect(result).toEqual({ feature: "durationSeconds", sampleCount: 2, unavailableReason: "MINIMUM_SAMPLE_COUNT_NOT_MET" });
  });

  it("falls back to the feature closest to the threshold when duration is unavailable for another reason", () => {
    const result = limitingFeatureBaseline([
      { feature: "durationSeconds", sampleCount: 0, unavailableReason: "TARGET_VALUE_UNAVAILABLE" },
      { feature: "cuttingSeconds", sampleCount: 1, unavailableReason: "MINIMUM_SAMPLE_COUNT_NOT_MET" },
      { feature: "idleSeconds", sampleCount: 3, unavailableReason: "MINIMUM_SAMPLE_COUNT_NOT_MET" },
    ]);
    expect(result?.feature).toBe("idleSeconds");
  });
});

describe("sampleProgressLabel", () => {
  it("is undefined unless the assessment is short on baseline samples", () => {
    expect(sampleProgressLabel(undefined)).toBeUndefined();
    expect(sampleProgressLabel({ status: "AVAILABLE", featureBaselines: [] })).toBeUndefined();
    expect(sampleProgressLabel({ status: "UNAVAILABLE", featureBaselines: [] })).toBeUndefined();
  });

  it("reports progress toward the minimum sample count", () => {
    expect(sampleProgressLabel({
      status: "INSUFFICIENT_DATA",
      featureBaselines: [{ feature: "durationSeconds", sampleCount: 2, unavailableReason: "MINIMUM_SAMPLE_COUNT_NOT_MET" }],
    })).toBe("2/5");
  });
});

describe("assessmentUnavailableExplanation", () => {
  it("says nothing extra once a comparison is available", () => {
    expect(assessmentUnavailableExplanation("155", { status: "AVAILABLE", featureBaselines: [] })).toBeUndefined();
  });

  it("distinguishes a missing program from a run with no comparable measurements", () => {
    expect(assessmentUnavailableExplanation(undefined, { status: "UNAVAILABLE", featureBaselines: [] }))
      .toMatch(/프로그램 정보가 확인되지 않아/);
    expect(assessmentUnavailableExplanation("155", { status: "UNAVAILABLE", featureBaselines: [] }))
      .toMatch(/이 가공 자체의 측정값이 부족해/);
  });

  it("names the program and the concrete sample count still needed", () => {
    const explanation = assessmentUnavailableExplanation("155", {
      status: "INSUFFICIENT_DATA",
      featureBaselines: [{ feature: "durationSeconds", sampleCount: 2, unavailableReason: "MINIMUM_SAMPLE_COUNT_NOT_MET" }],
    });
    expect(explanation).toContain("프로그램 155");
    expect(explanation).toContain("아직 2건");
    expect(explanation).toContain("3건 더 필요");
  });

  it("falls back to a generic message when no feature baseline explains the shortfall", () => {
    expect(assessmentUnavailableExplanation("155", { status: "INSUFFICIENT_DATA", featureBaselines: [] }))
      .toBe("같은 프로그램의 이전 가공이 아직 충분하지 않습니다.");
  });

  it("flags a partial comparison without claiming full availability", () => {
    expect(assessmentUnavailableExplanation("155", { status: "PARTIAL", featureBaselines: [] }))
      .toMatch(/일부 측정값은 비교했지만/);
  });
});

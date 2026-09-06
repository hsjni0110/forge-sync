import { describe, expect, it } from "vitest";

import { deriveTwinPresentation } from "./twinPresentationPolicy";

describe("Twin presentation policy", () => {
  it("presents completed and paused replay as intentional historical states", () => {
    expect(deriveTwinPresentation("STALE", "COMPLETED")).toMatchObject({
      freshnessLabel: "마지막 재생 데이터",
      notice: "재생이 완료되었습니다. 마지막 재생 데이터를 표시합니다.",
      noticeTone: "neutral",
      warnsAgainstRealtimeUse: false,
    });
    expect(deriveTwinPresentation("STALE", "PAUSED")).toMatchObject({
      freshnessLabel: "선택 시점 데이터",
      notice: "재생이 일시정지되었습니다. 선택 시점 데이터를 표시합니다.",
      noticeTone: "neutral",
      warnsAgainstRealtimeUse: false,
    });
  });

  it("keeps the safety warning for genuinely stale data", () => {
    expect(deriveTwinPresentation("STALE")).toMatchObject({
      freshnessLabel: "오래된 데이터",
      noticeTone: "danger",
      warnsAgainstRealtimeUse: true,
    });
  });
});

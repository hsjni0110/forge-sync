import { describe, expect, it } from "vitest";

import { timelineTicks } from "./shiftOverview";

describe("timelineTicks", () => {
  it("labels a whole day on round two-hour marks", () => {
    const ticks = timelineTicks("2016-10-05T05:27:55Z", "2016-10-05T19:15:07Z");

    expect(ticks.map((tick) => tick.label))
      .toEqual(["06:00", "08:00", "10:00", "12:00", "14:00", "16:00", "18:00"]);
    expect(ticks[0]?.percent).toBeCloseTo((32 * 60 + 5) / (13 * 3600 + 47 * 60 + 12) * 100);
  });

  it("switches to ten-minute marks when the view zooms into one stop", () => {
    const ticks = timelineTicks("2016-10-05T17:38:31Z", "2016-10-05T18:38:59Z");

    expect(ticks.map((tick) => tick.label))
      .toEqual(["17:40", "17:50", "18:00", "18:10", "18:20", "18:30"]);
  });

  it("returns no ticks for an empty or reversed range", () => {
    expect(timelineTicks("2016-10-05T09:00:00Z", "2016-10-05T09:00:00Z")).toEqual([]);
  });
});

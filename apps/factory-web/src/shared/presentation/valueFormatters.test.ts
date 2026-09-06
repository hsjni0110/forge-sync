import { describe, expect, it } from "vitest";

import { formatDecimal, formatUtcParts } from "./valueFormatters";

describe("presentation value formatters", () => {
  it("rounds display decimals and removes insignificant zeroes", () => {
    expect(formatDecimal(8.807549, { maximumFractionDigits: 1 })).toBe("8.8");
    expect(formatDecimal(8.85, { maximumFractionDigits: 1 })).toBe("8.9");
    expect(formatDecimal(45, { maximumFractionDigits: 2 })).toBe("45");
    expect(formatDecimal(0, { maximumFractionDigits: 2 })).toBe("0");
  });

  it("keeps UTC while presenting date and second-precision time separately", () => {
    expect(formatUtcParts("2026-09-06T04:44:39.496965Z")).toEqual({
      date: "2026-09-06",
      time: "04:44:39",
      zone: "UTC",
      exact: "2026-09-06T04:44:39.496965Z",
    });
  });
});

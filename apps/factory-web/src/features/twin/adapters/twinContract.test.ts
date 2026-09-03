import { describe, expect, it } from "vitest";

import twinPatchFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-twin-patch.json";
import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import { AjvTwinPatchDecoder, decodeTwinSnapshot } from "./twinContract";

function validPatch() {
  return structuredClone(twinPatchFixture);
}

describe("Twin contract decoders", () => {
  it("accepts the shared REST fixture and a consistent patch envelope", () => {
    expect(decodeTwinSnapshot(structuredClone(twinFixture)).machine.machineId).toBe(
      "Mazak01",
    );
    expect(
      new AjvTwinPatchDecoder().decode(JSON.stringify(validPatch()), "Mazak01")
        .targetVersion,
    ).toBe(4);
  });

  it("rejects an invalid schema and cross-field version mismatch", () => {
    const decoder = new AjvTwinPatchDecoder();
    expect(() => decoder.decode("{}", "Mazak01")).toThrow(/contract/);
    expect(() =>
      decoder.decode(
        JSON.stringify({ ...validPatch(), targetVersion: 5 }),
        "Mazak01",
      ),
    ).toThrow(/inconsistent/);
  });

  it("rejects a patch for another machine", () => {
    expect(() =>
      new AjvTwinPatchDecoder().decode(JSON.stringify(validPatch()), "OtherMachine"),
    ).toThrow(/inconsistent/);
  });

  it("rejects a REST snapshot for another machine or with malformed time", () => {
    expect(() =>
      decodeTwinSnapshot(structuredClone(twinFixture), "OtherMachine"),
    ).toThrow(/identity/);

    const malformed = structuredClone(twinFixture);
    malformed.state.freshness.evaluatedAt = "not-a-dateZ";
    expect(() => decodeTwinSnapshot(malformed, "Mazak01")).toThrow(/contract/);
  });

  it("rejects an inverted freshness window", () => {
    const invalid = structuredClone(twinFixture);
    invalid.state.freshness.freshMaxAgeMillis = 20_000;
    invalid.state.freshness.laggingMaxAgeMillis = 10_000;
    expect(() => decodeTwinSnapshot(invalid, "Mazak01")).toThrow(/freshness/);

    const invalidPatch = validPatch();
    invalidPatch.snapshot.state.freshness.freshMaxAgeMillis = 20_000;
    expect(() =>
      new AjvTwinPatchDecoder().decode(JSON.stringify(invalidPatch), "Mazak01"),
    ).toThrow(/freshness/);
  });
});

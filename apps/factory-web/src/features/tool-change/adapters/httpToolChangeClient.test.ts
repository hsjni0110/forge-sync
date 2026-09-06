import { afterEach, describe, expect, it, vi } from "vitest";

import fixture from "../../../../../../tests/fixtures/tool-changes/v1/mazak01-tool-changes.json";
import { HttpToolChangeClient } from "./httpToolChangeClient";

afterEach(() => vi.unstubAllGlobals());

describe("HttpToolChangeClient", () => {
  it("accepts the shared timeline contract and sends cursor identity", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(fixture), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await new HttpToolChangeClient("").find(
      "Mazak01", fixture.replaySessionId, fixture.throughReplaySequence,
    );

    expect(result.toolChanges[0]).toMatchObject({ fromToolNumber: 4, toToolNumber: 7 });
    expect(String(fetchMock.mock.calls[0][0])).toContain("throughReplaySequence=42");
  });

  it("rejects missing provenance instead of exposing an untraceable marker", async () => {
    const invalid = structuredClone(fixture) as Record<string, unknown>;
    const changes = invalid.toolChanges as Array<Record<string, unknown>>;
    delete changes[0].provenance;
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify(invalid), { status: 200 }),
    ));

    await expect(new HttpToolChangeClient("").find("Mazak01", fixture.replaySessionId, 42))
      .rejects.toThrow(/contract/);
  });
});

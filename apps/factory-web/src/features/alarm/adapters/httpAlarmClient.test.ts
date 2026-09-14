import { afterEach, describe, expect, it, vi } from "vitest";

import fixture from "../../../../../../tests/fixtures/alarm/v1/mazak01-alarm-timeline.json";
import { HttpAlarmClient } from "./httpAlarmClient";

afterEach(() => vi.unstubAllGlobals());

describe("HttpAlarmClient", () => {
  it("loads the shared alarm identity at the requested replay cursor", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(fixture), { status: 200 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await new HttpAlarmClient("").find(
      "Mazak01", fixture.replaySessionId, fixture.throughReplaySequence,
    );

    expect(result.alarms[0].alarmId).toBe(fixture.alarms[0].alarmId);
    expect(String(fetchMock.mock.calls[0][0])).toContain("throughReplaySequence=42");
  });

  it("acknowledges with the operator and rejects an invalid response contract", async () => {
    const acknowledged = { ...fixture.alarms[0], status: "ACKNOWLEDGED", revision: 1,
      acknowledgedBy: "김 작업자", acknowledgedAt: "2026-09-13T01:00:00Z" };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(acknowledged), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...acknowledged, alarmId: 7 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpAlarmClient("");

    await expect(client.acknowledge(fixture.alarms[0].alarmId, 0, "김 작업자"))
      .resolves.toMatchObject({ status: "ACKNOWLEDGED", acknowledgedBy: "김 작업자" });
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "POST" });
    await expect(client.acknowledge(fixture.alarms[0].alarmId, 0, "김 작업자"))
      .rejects.toThrow(/contract/);
  });
});

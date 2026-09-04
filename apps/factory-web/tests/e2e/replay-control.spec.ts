import { expect, test } from "@playwright/test";

const apiBaseUrl = process.env.FORGESYNC_API_BASE_URL ?? "http://127.0.0.1:18080";

test("browser Replay start creates an authoritative session and Twin", async ({ page, request }) => {
  await page.goto("/factory");

  await page.getByRole("button", { name: "Replay 시작" }).click();
  await expect(page.getByText("재생 중", { exact: true })).toBeVisible();

  await expect
    .poll(async () => {
      const response = await request.get(
        `${apiBaseUrl}/api/v1/machines/Mazak01/replay-session`,
        { headers: { Accept: "application/vnd.forgesync.replay-session.v1+json" } },
      );
      return response.ok() ? await response.json() : undefined;
    })
    .toMatchObject({ machineId: "Mazak01", status: "RUNNING" });
  const sessionResponse = await request.get(
    `${apiBaseUrl}/api/v1/machines/Mazak01/replay-session`,
    { headers: { Accept: "application/vnd.forgesync.replay-session.v1+json" } },
  );
  const session = await sessionResponse.json();

  await expect
    .poll(async () => {
      const response = await request.get(`${apiBaseUrl}/api/v1/machines/Mazak01/twin`, {
        headers: { Accept: "application/vnd.forgesync.twin.v1+json" },
      });
      if (!response.ok()) return undefined;
      const twin = await response.json();
      return twin.replayCursor?.replaySessionId;
    })
    .toBe(session.replaySessionId);
});

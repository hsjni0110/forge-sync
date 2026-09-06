import { expect, test } from "@playwright/test";

const apiBaseUrl = process.env.FORGESYNC_API_BASE_URL ?? "http://127.0.0.1:18080";

test("browser Replay start creates an authoritative session and Twin", async ({ page, request }) => {
  test.setTimeout(180_000);
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

  await page.getByRole("button", { name: "일시정지", exact: true }).click();
  await expect(page.getByText("일시정지됨", { exact: true })).toBeVisible();
  // Scrub the browser range input to just after the reviewed READY boundary.
  // The source fixture and original boundary evidence are pinned in the run contract fixture.
  // Dragging the scrubber and releasing (native "change") seeks immediately —
  // there is no separate confirm step.
  await page.getByRole("slider", { name: "Replay timeline" }).evaluate((input) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
    setter?.call(input, String(Date.parse("2016-10-05T09:21:00.740Z")));
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
  });
  const analysis = page.locator(".process-analysis");
  await expect(analysis).toHaveAttribute("data-process-session", /.+/, { timeout: 60_000 });
  const twinResponse = await request.get(`${apiBaseUrl}/api/v1/machines/Mazak01/twin`, {
    headers: { Accept: "application/vnd.forgesync.twin.v1+json" },
  });
  const twin = await twinResponse.json();
  expect(twin.replayCursor.replaySessionId).not.toBe(session.replaySessionId);
  await expect(analysis).toHaveAttribute("data-process-version", String(twin.consistency.twinVersion));
  await expect(analysis).toHaveAttribute("data-process-session", twin.replayCursor.replaySessionId);
  await expect(page.locator(".floating-machine-label")).toContainText(`Twin v${twin.consistency.twinVersion}`);
  await expect(page.locator(".machine-summary .machine-version")).toHaveText(`v${twin.consistency.twinVersion}`);
  await expect(page.getByRole("region", { name: "CURRENT RUN · 현재 가공" })).toContainText("현재 가공 없음");
  await expect(page.getByRole("option", { name: /Tool Mount · 활성 공구 4 · OBSERVED · 형상 미확인/ }))
    .toHaveCount(1);

  await page.getByRole("button", { name: "2D", exact: true }).click();
  const run = page.getByRole("button", { name: /가공 선택 · 155 · 2016-10-05T09:18:30\.447Z/ });
  await run.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("region", { name: "PROCESS · 공정 특징" })).toContainText("148.734 초");
  const evidence = page.locator("summary").filter({ hasText: "가공의 관측 근거" });
  await evidence.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByText("OBSERVED · REAL:NIST", { exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "ANOMALY · 이전 가공과의 차이" })).toContainText("고장 판정이 아닙니다");
  await page.getByRole("button", { name: "SPLIT", exact: true }).click();
  await page.getByText("모델 정보", { exact: true }).click();
  await expect(page.getByText(/PGM 155의 관측 위치 \d+점을 연결한 경로/)).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(/실제 절삭 흔적이나 기계 이동 한계가 아닙니다/)).toBeVisible();
  await page.getByRole("button", { name: "2D", exact: true }).click();

  // Selecting the run for detail is independent of seek: the run above is already
  // selected, so the explicit navigation action starts a new session and must discard
  // the completed result once only the start of that run has been observed.
  await expect(analysis).toHaveAttribute("data-process-session", twin.replayCursor.replaySessionId);
  await page.getByRole("button", { name: "가공 시작 시점으로 이동", exact: true }).click();
  await expect(analysis).not.toHaveAttribute("data-process-session", twin.replayCursor.replaySessionId);
  await expect(page.getByRole("region", { name: "CURRENT RUN · 현재 가공" })).toContainText("종료 근거 미확정", { timeout: 60_000 });
  await expect(page.getByRole("region", { name: "PROCESS · 공정 특징" })).toContainText("완료된 가공만 분석 가능");

  // The reviewed 09:21 cursor still has the initial observed tool, so it correctly has
  // no transition marker. At the source-range end, the full observed history contains
  // changes and exercises marker resynchronization against the new seek session.
  await page.getByRole("button", { name: "끝으로 이동", exact: true }).click();
  await expect(page.locator(".tool-change-marker").first()).toBeVisible({ timeout: 60_000 });
});

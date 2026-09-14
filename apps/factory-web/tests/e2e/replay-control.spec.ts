import { expect, test } from "@playwright/test";

const apiBaseUrl = process.env.FORGESYNC_API_BASE_URL ?? "http://127.0.0.1:18080";

test("browser Replay start creates an authoritative session and Twin", async ({ page, request }) => {
  test.setTimeout(900_000);
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
  await page.getByRole("button", { name: "100x", exact: true }).click();
  await expect(page.getByRole("button", { name: "100x", exact: true })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
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
  await expect(analysis).toHaveAttribute("data-process-session", /.+/, { timeout: 120_000 });
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
  const productionContext = page.getByRole("region", { name: "생산 문맥" });
  await expect(productionContext).toContainText("ProductionResult · 원천에서 관측되지 않음", {
    timeout: 60_000,
  });
  await expect(productionContext).toContainText("생산 완료나 양품·불량 결과로 확정하지 않습니다");
  const toolLoadTrend = page.getByRole("region", { name: "공구별 부하 추세" });
  await expect(toolLoadTrend).toContainText("DERIVED · REAL:NIST", { timeout: 60_000 });
  await expect(toolLoadTrend).toContainText("마모량이나 잔여 수명이 아닙니다");
  await expect(toolLoadTrend).toContainText("Machine FAULT나 Alarm을 만들지 않습니다");
  await expect(page.getByRole("option", { name: /Tool Mount · 활성 공구 4 · OBSERVED · 형상 미확인/ }))
    .toHaveCount(1);

  await page.getByRole("button", { name: "평면 보기", exact: true }).click();
  const run = page.getByRole("button", { name: /가공 선택 · 155 · 2016-10-05T09:18:30\.447Z/ });
  await run.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("region", { name: "PROCESS · 공정 특징" })).toContainText("148.734 초");
  const evidence = page.locator("summary").filter({ hasText: "가공의 관측 근거" });
  await evidence.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByText("OBSERVED · REAL:NIST", { exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "ANOMALY · 이전 가공과의 차이" })).toContainText("고장 판정이 아닙니다");
  await page.getByRole("button", { name: "평면과 입체 함께 보기", exact: true }).click();
  const currentAction = page.getByRole("region", { name: "현재 동작" });
  await expect(currentAction).toContainText("현재 재생 가공 · 없음");
  await expect(currentAction).toContainText(/선택 경로 · PGM 155 · \d+점 · OBSERVED_PATH/, {
    timeout: 60_000,
  });
  await page.getByText("모델 정보", { exact: true }).click();
  await expect(page.getByText(/PGM 155의 관측 위치 \d+점을 연결한 경로/)).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(/실제 절삭 흔적이나 기계 이동 한계가 아닙니다/)).toBeVisible();
  await page.getByRole("button", { name: "평면 보기", exact: true }).click();

  // Selecting the run for detail is independent of seek: the run above is already
  // selected, so the explicit navigation action starts a new session and must discard
  // the completed result once only the start of that run has been observed.
  await expect(analysis).toHaveAttribute("data-process-session", twin.replayCursor.replaySessionId);
  await page.getByRole("button", { name: "가공 시작 시점으로 이동", exact: true }).click();
  await expect(analysis).not.toHaveAttribute("data-process-session", twin.replayCursor.replaySessionId);
  await expect(page.getByRole("region", { name: "CURRENT RUN · 현재 가공" })).toContainText(
    "종료 근거 미확정",
    { timeout: 120_000 },
  );
  await expect(page.getByRole("region", { name: "PROCESS · 공정 특징" })).toContainText("완료된 가공만 분석 가능");

  // Exercise the first reviewed alarm interval before the source-range-end seek. The end
  // publishes more than 100,000 observations, so issuing another seek while that ingestion
  // catches up would not identify the replacement session deterministically.
  const beforeAlarmSeekResponse = await request.get(
    `${apiBaseUrl}/api/v1/machines/Mazak01/twin`,
    { headers: { Accept: "application/vnd.forgesync.twin.v1+json" } },
  );
  const beforeAlarmSeek = await beforeAlarmSeekResponse.json();
  await page.getByRole("slider", { name: "Replay timeline" }).evaluate((input) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
    // The range preserves the source start's .740 millisecond offset. This normalizes to
    // 09:03:58.740Z: after WARNING 406 at .872 and before its NORMAL at 09:03:59.755Z.
    setter?.call(input, String(Date.parse("2016-10-05T09:03:59.000Z")));
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
  });
  await expect.poll(async () => {
    const twinResponse = await request.get(`${apiBaseUrl}/api/v1/machines/Mazak01/twin`, {
      headers: { Accept: "application/vnd.forgesync.twin.v1+json" },
    });
    if (!twinResponse.ok()) return undefined;
    const currentTwin = await twinResponse.json();
    if (currentTwin.replayCursor.replaySessionId === beforeAlarmSeek.replayCursor.replaySessionId) {
      return undefined;
    }
    const query = new URLSearchParams({
      replaySessionId: currentTwin.replayCursor.replaySessionId,
      throughReplaySequence: String(currentTwin.replayCursor.replaySequence),
    });
    const response = await request.get(`${apiBaseUrl}/api/v1/machines/Mazak01/alarms?${query}`, {
      headers: { Accept: "application/vnd.forgesync.alarms.v1+json" },
    });
    if (!response.ok()) return undefined;
    const timeline = await response.json();
    return timeline.alarms.find((alarm: { status: string }) => alarm.status !== "RESOLVED");
  }, { timeout: 240_000 }).not.toBeUndefined();
  const currentTwinResponse = await request.get(`${apiBaseUrl}/api/v1/machines/Mazak01/twin`);
  const currentTwinAtAlarm = await currentTwinResponse.json();
  const alarmQuery = new URLSearchParams({
    replaySessionId: currentTwinAtAlarm.replayCursor.replaySessionId,
    throughReplaySequence: String(currentTwinAtAlarm.replayCursor.replaySequence),
  });
  const alarmResponse = await request.get(
    `${apiBaseUrl}/api/v1/machines/Mazak01/alarms?${alarmQuery}`,
    { headers: { Accept: "application/vnd.forgesync.alarms.v1+json" } },
  );
  const alarmTimeline = await alarmResponse.json();
  const activeAlarm = alarmTimeline.alarms.find((alarm: { status: string }) => alarm.status !== "RESOLVED");
  expect(activeAlarm).toBeTruthy();
  await page.getByRole("button", { name: "평면과 입체 함께 보기", exact: true }).click();
  const alarmCard = page
    .locator(`[data-alarm-id="${activeAlarm.alarmId}"]`)
    .filter({ has: page.getByText("Alarm ID") });
  await expect(alarmCard).toBeVisible();
  await expect(page.locator(".floating-machine-label")).toHaveAttribute("data-alarm-id", activeAlarm.alarmId);
  await alarmCard.getByLabel("작업자 이름").fill("E2E 작업자");
  await alarmCard.getByRole("button", { name: "알람 확인" }).click();
  await expect(alarmCard).toContainText("확인 작업자 · E2E 작업자");

  // Data Quality uses the same replay/cursor scope but keeps source, runtime, and derived
  // measurements separate. Missing completeness evidence must remain explicitly unevaluated.
  await page.goto("/data-quality");
  await expect(page.getByRole("heading", { name: "데이터 품질", exact: true })).toBeVisible();
  await expect(page.getByText("종합 품질 점수 없음")).toBeVisible();
  await expect(page.getByText("87.67%")).toBeVisible({ timeout: 60_000 });
  const runtimeQuality = page.locator(".quality-section").filter({ hasText: "현재 Replay 수신" });
  await expect(runtimeQuality).not.toContainText("Replay 수신 범위가 없어", { timeout: 60_000 });
  await expect(page.getByRole("link", { name: "원본 줄 보기" }).first()).toHaveAttribute(
    "href",
    /#L\d+$/,
  );

  // The Dashboard timeline must reuse the same acknowledged Alarm identity shown in 2D and 3D.
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "교대조 개요" })).toBeVisible();
  await expect(page.getByRole("region", { name: "교대조 핵심 지표" })).toContainText("가동률", {
    timeout: 60_000,
  });
  await expect(page.getByRole("region", { name: "교대조 핵심 지표" })).toContainText("전체");
  await expect(page.getByRole("region", { name: "설비 상태 구간" })).toBeVisible();
  await expect(page.locator(`[data-kind="ALARM"][data-alarm-id="${activeAlarm.alarmId}"]`))
    .toBeVisible();

  // The Dashboard Pareto reuses the authoritative seek path whose current-run convergence was
  // verified above. Its first-ranked interval must move the cursor and shared 2D/3D Twin version.
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "정지 사유 Pareto" })).toBeVisible({
    timeout: 60_000,
  });
  const firstDowntime = page.locator(".downtime-list button").first();
  const downtimeStartedAt = await firstDowntime.getAttribute("data-started-at");
  expect(downtimeStartedAt).toBeTruthy();
  await firstDowntime.click();
  await expect
    .poll(async () => {
      const response = await request.get(`${apiBaseUrl}/api/v1/machines/Mazak01/twin`, {
        headers: { Accept: "application/vnd.forgesync.twin.v1+json" },
      });
      return response.ok() ? (await response.json()).replayCursor.sourceObservedAt : undefined;
    }, { timeout: 60_000 })
    .toBe(downtimeStartedAt);
  await expect
    .poll(async () => {
      const response = await request.get(
        `${apiBaseUrl}/api/v1/machines/Mazak01/replay-session`,
        { headers: { Accept: "application/vnd.forgesync.replay-session.v1+json" } },
      );
      return response.ok() ? (await response.json()).status : undefined;
    }, { timeout: 120_000 })
    .toBe("PAUSED");
  await page.goto("/factory");
  await expect
    .poll(async () => {
      const visual = await page.locator(".floating-machine-label").textContent();
      const summary = await page.locator(".machine-summary .machine-version").textContent();
      const visualVersion = visual?.match(/Twin v(\d+)/)?.[1];
      const summaryVersion = summary?.match(/v(\d+)/)?.[1];
      return visualVersion !== undefined && visualVersion === summaryVersion;
    })
    .toBe(true);

  // The reviewed downtime cursor still has the initial observed tool. At the source-range end,
  // the full observed history contains changes and exercises marker resynchronization. Keep this
  // final large seek after the cursor-dependent Dashboard checks so they cannot read its partial
  // ingestion state.
  await page.getByRole("button", { name: "끝으로 이동", exact: true }).click();
  await expect(page.locator(".tool-change-marker").first()).toBeVisible({ timeout: 60_000 });
});

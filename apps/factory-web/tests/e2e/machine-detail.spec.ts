import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";

const repositoryRoot = process.env.FORGESYNC_REPOSITORY_ROOT;
const apiBaseUrl = process.env.FORGESYNC_API_BASE_URL ?? "http://127.0.0.1:18080";

function publish(step: "initial" | "live-update" | "offline-update") {
  if (!repositoryRoot) {
    throw new Error("FORGESYNC_REPOSITORY_ROOT is required");
  }
  execFileSync(
    "uv",
    ["run", "python", "tests/e2e/support/publish_observation.py", step],
    { cwd: repositoryRoot, env: process.env, stdio: "inherit" },
  );
}

test("replay updates the accessible 2D detail and REST resync converges after disconnect", async ({
  context,
  page,
  request,
}) => {
  publish("initial");
  await expect
    .poll(async () => (await request.get(`${apiBaseUrl}/api/v1/machines/Mazak01/twin`)).status())
    .toBe(200);

  await page.clock.install({ time: new Date() });
  await page.goto("/machines/Mazak01");
  await expect(page.getByRole("heading", { name: "Mazak01" })).toBeVisible();
  await expect(page.getByText("49 rpm")).toBeVisible();
  await expect(page.getByText(/실제 데이터 · NIST/).first()).toBeVisible();
  await expect(page.getByLabel("트윈 연결 상태").getByText("실시간 연결됨")).toBeVisible();

  publish("live-update");
  await expect(page.getByText("10 rpm")).toBeVisible();
  await expect(page.getByText("데이터 버전 2")).toBeVisible();

  await context.setOffline(true);
  await expect(page.getByText(/마지막으로 받은 값을 표시합니다/)).toBeVisible();
  publish("offline-update");
  await page.clock.fastForward(10_001);
  await expect(page.getByRole("alert")).toContainText("오래된 데이터입니다");
  await expect(page.getByText("10 rpm")).toBeVisible();

  await page.clock.setFixedTime(new Date());
  await context.setOffline(false);
  await page.clock.fastForward(10_000);
  await expect(page.getByText("1873 rpm")).toBeVisible();
  await expect(page.getByText("데이터 버전 3")).toBeVisible();
  await expect(page.getByLabel("트윈 연결 상태").getByText("실시간 연결됨")).toBeVisible();

  await page.keyboard.press("Home");
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "본문으로 바로가기" })).toBeFocused();
  for (const section of ["기본 정보", "현재 상태", "측정값", "데이터 품질", "데이터 출처"]) {
    await expect(page.getByRole("heading", { name: section })).toBeVisible();
  }
});

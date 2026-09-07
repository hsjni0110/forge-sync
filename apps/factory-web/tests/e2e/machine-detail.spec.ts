import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";

const repositoryRoot = process.env.FORGESYNC_REPOSITORY_ROOT;
const apiBaseUrl = process.env.FORGESYNC_API_BASE_URL ?? "http://127.0.0.1:18080";

function publish(
  step: "initial" | "live-update" | "stopped" | "reactivated" | "offline-update",
) {
  if (!repositoryRoot) {
    throw new Error("FORGESYNC_REPOSITORY_ROOT is required");
  }
  execFileSync(
    "uv",
    ["run", "python", "tests/e2e/support/publish_observation.py", step],
    { cwd: repositoryRoot, env: process.env, stdio: "inherit" },
  );
}

test("replay, REST resync, and 3D failure keep the accessible detail authoritative", async ({
  context,
  page,
  request,
}) => {
  publish("initial");
  await expect
    .poll(async () => (await request.get(`${apiBaseUrl}/api/v1/machines/Mazak01/twin`)).status())
    .toBe(200);

  await page.clock.install({ time: new Date() });
  await page.goto("/factory");
  await expect(
    page.getByRole("button", {
      name: /Mazak01 Twin v2 가동 중 49 RPM 시각 회전 켜짐 선택됨/,
    }),
  ).toBeVisible();
  await expect(page.getByText("49 rpm", { exact: true })).toBeVisible();

  publish("live-update");
  await expect(page.getByText("10 rpm", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", {
      name: /Mazak01 Twin v3 가동 중 10 RPM 시각 회전 켜짐 선택됨/,
    }),
  ).toBeVisible();

  publish("stopped");
  await expect(
    page.getByRole("button", {
      name: /Mazak01 Twin v4 정지됨 10 RPM 시각 회전 꺼짐 선택됨/,
    }),
  ).toBeVisible();

  publish("reactivated");
  await expect(
    page.getByRole("button", {
      name: /Mazak01 Twin v5 가동 중 10 RPM 시각 회전 켜짐 선택됨/,
    }),
  ).toBeVisible();

  await context.setOffline(true);
  await expect(page.getByText(/마지막 값을 표시합니다/)).toBeVisible();
  publish("offline-update");
  await page.clock.fastForward(10_001);
  await expect(page.getByRole("alert")).toContainText(
    "현재 설비의 실시간 상태로 판단하지 마세요",
  );
  await expect(
    page.getByLabel("트윈 연결 상태").getByText("오래된 데이터"),
  ).toHaveCount(1);
  await expect(page.getByText("10 rpm", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", {
      name: /Mazak01 Twin v5 오래된 데이터 10 RPM 시각 회전 꺼짐 선택됨/,
    }),
  ).toBeVisible();

  await page.clock.setFixedTime(new Date());
  await context.setOffline(false);
  await page.clock.fastForward(10_000);
  await expect(page.getByText("1,873 rpm", { exact: true }).first()).toBeVisible();
  await expect(
    page.getByRole("button", {
      name: /Mazak01 Twin v6 가동 중 1873 RPM 시각 회전 켜짐 선택됨/,
    }),
  ).toBeVisible();
  await expect(page.getByLabel("트윈 연결 상태").getByText("실시간 연결됨")).toBeVisible();

  await page.getByRole("button", { name: "평면 보기", exact: true }).click();
  await expect(page.getByText("데이터 버전 6")).toBeVisible();
  await expect(page.getByText("1,873 rpm", { exact: true }).first()).toBeVisible();
  await page.getByText(/원본 추적 정보 .*건 · .*개 출처/).click();
  await expect(page.getByText(/실제 데이터 · NIST/).first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Mazak01", exact: true })).toBeVisible();
  for (const section of ["기본 정보", "현재 상태", "측정값", "데이터 품질", "데이터 출처"]) {
    await expect(page.getByRole("heading", { name: section })).toBeVisible();
  }

  await page.goto("/factory");
  await expect(page.getByRole("heading", { name: "Factory Scene" })).toBeVisible();
  await expect(page.getByRole("button", { name: "평면과 입체 함께 보기" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await expect(page.locator("canvas")).toBeVisible();
  await expect(page.getByText("1,873 rpm", { exact: true })).toBeVisible();
  const selectedMachineLabel = page.getByRole("button", {
    name: /Mazak01 Twin v6 가동 중 1873 RPM 시각 회전 켜짐 선택됨/,
  });
  await expect(selectedMachineLabel).toBeVisible();
  await selectedMachineLabel.click();
  await expect(selectedMachineLabel).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".machine-summary .machine-version")).toHaveText("v6");

  await page.addInitScript(() => {
    const originalGetContext = HTMLCanvasElement.prototype.getContext;
    Object.defineProperty(HTMLCanvasElement.prototype, "getContext", {
      configurable: true,
      value(this: HTMLCanvasElement, contextId: string, options?: unknown) {
        if (["webgl", "webgl2", "experimental-webgl"].includes(contextId)) {
          return null;
        }
        return Reflect.apply(originalGetContext, this, [contextId, options]);
      },
    });
  });
  await page.reload();
  await expect(page.getByText("3D를 사용할 수 없습니다")).toBeVisible();
  await expect(page.getByText("1,873 rpm")).toBeVisible();
  await expect(page.getByText(/2D 화면에서 계속 확인할 수 있습니다/)).toBeVisible();
});

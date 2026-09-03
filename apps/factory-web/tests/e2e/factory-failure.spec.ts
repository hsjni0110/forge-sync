import { expect, test } from "@playwright/test";

import twinFixture from "../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json" with {
  type: "json",
};

test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/machines/Mazak01/twin", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/vnd.forgesync.twin.v1+json",
      body: JSON.stringify(twinFixture),
    });
  });
});

test("available WebGL keeps the 3D factory scene visible", async ({ page }) => {
  await page.goto("/factory");

  await expect(page.getByRole("button", { name: "SPLIT" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await expect(page.getByText("3D 장면 준비됨")).toBeAttached();
  await expect(page.getByLabel("시뮬레이션 공장 3D 화면").locator("canvas")).toBeVisible();
  await expect(page.getByText("3D를 사용할 수 없습니다")).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Mazak01", exact: true })).toBeVisible();
  const machineLabel = page.getByRole("button", {
    name: /Mazak01 Twin v4 오래된 데이터 49 RPM 시각 회전 꺼짐 선택됨/,
  });
  await expect(machineLabel).toBeVisible();
  await machineLabel.click();
  await expect(machineLabel).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".machine-summary .machine-version")).toHaveText("v4");

  await page.getByRole("button", { name: "2D" }).click();
  await expect(page.getByRole("heading", { name: "데이터 품질" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "데이터 출처" })).toBeVisible();
});

test("WebGL failure keeps the 2D factory detail usable", async ({ page }) => {
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
  await page.goto("/factory");

  await expect(page.getByRole("button", { name: "SPLIT" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await expect(page.getByText("3D를 사용할 수 없습니다")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Mazak01", exact: true })).toBeVisible();
  await expect(page.getByText("49 rpm")).toBeVisible();
  await expect(page.getByText(/2D 화면에서 계속 확인할 수 있습니다/)).toBeVisible();
});

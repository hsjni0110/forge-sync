import { expect, test, type Page } from "@playwright/test";

import twinFixture from "../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json" with {
  type: "json",
};

const viewports = [
  { name: "desktop", width: 1440, height: 900 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "mobile", width: 390, height: 844 },
] as const;

const themes = [
  { preference: "LIGHT", carbon: "white" },
  { preference: "DARK", carbon: "g100" },
] as const;

for (const viewport of viewports) {
  for (const theme of themes) {
    test(`${viewport.name} ${theme.preference.toLowerCase()} keeps the workstation readable`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.addInitScript((preference) => {
        window.localStorage.setItem("forgesync-theme", preference);
      }, theme.preference);
      await stubFactoryApi(page);
      await page.goto("/factory");

      await expect(page.locator(".site-shell")).toHaveAttribute("data-carbon-theme", theme.carbon);
      await expect(page.getByRole("navigation", { name: "ForgeSync navigation" })).toBeVisible();
      await expect(page.getByRole("heading", { name: "Mazak01", exact: true })).toBeVisible();
      await expect(page.getByRole("heading", { name: "공간 투영" })).toBeVisible();
      await expect(page.getByRole("complementary", { name: "3D 카메라와 부품 검사" }))
        .toBeVisible();

      if (viewport.name === "desktop" && theme.preference === "LIGHT") {
        await page.getByRole("button", { name: "평면 보기" }).click();
        await expect(page.locator(".metric-card")).toHaveCount(0);
        await page.getByRole("button", { name: "평면과 입체 함께 보기" }).click();
      }

      const horizontalOverflow = await page.evaluate(
        () => document.documentElement.scrollWidth - window.innerWidth,
      );
      expect(horizontalOverflow).toBe(0);

      if (viewport.width <= 768) {
        const order = await page.locator(
          ".factory-detail-summary, .factory-transport, .scene-panel, .factory-inspector",
        ).evaluateAll((elements) => elements.map((element) => element.className));
        expect(order).toEqual([
          "factory-detail-summary",
          "factory-transport",
          "scene-panel",
          "factory-inspector",
        ]);
      }

      const selectors = [
        ".site-rail",
        ".factory-transport",
        ".camera-button-grid",
        ".inspection-part-select",
      ];
      const boxes = await Promise.all(selectors.map(async (selector) => {
        const box = await page.locator(selector).boundingBox();
        expect(box, `${selector} should have a measurable box`).not.toBeNull();
        return { selector, box: box! };
      }));
      for (let left = 0; left < boxes.length; left += 1) {
        for (let right = left + 1; right < boxes.length; right += 1) {
          expect(overlaps(boxes[left]!.box, boxes[right]!.box),
            `${boxes[left]!.selector} overlaps ${boxes[right]!.selector}`).toBe(false);
        }
      }
    });
  }
}

async function stubFactoryApi(page: Page) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/v1/machines/Mazak01/twin") {
      await route.fulfill({
        status: 200,
        contentType: "application/vnd.forgesync.twin.v1+json",
        body: JSON.stringify(twinFixture),
      });
      return;
    }
    await route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
  });
}

function overlaps(
  left: { x: number; y: number; width: number; height: number },
  right: { x: number; y: number; width: number; height: number },
) {
  return left.x < right.x + right.width
    && left.x + left.width > right.x
    && left.y < right.y + right.height
    && left.y + left.height > right.y;
}

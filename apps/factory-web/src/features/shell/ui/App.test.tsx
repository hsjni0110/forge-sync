import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { TwinSessionFactory } from "../../twin/application/ports";
import { App } from "./App";

afterEach(cleanup);

describe("App", () => {
  it("renders the ForgeSync runtime shell", () => {
    render(
      <MemoryRouter>
        <App
          twinSessionFactory={() => {
            throw new Error("Home route must not create a Twin session");
          }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "ForgeSync" })).toBeTruthy();
    expect(screen.getByText(/설비의 현재 상태와 데이터 출처/)).toBeTruthy();
  });

  it("rejects an invalid machine identifier before creating a session", () => {
    const twinSessionFactory = vi.fn<TwinSessionFactory>();
    render(
      <MemoryRouter initialEntries={["/machines/not%20valid"]}>
        <App twinSessionFactory={twinSessionFactory} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "설비를 찾을 수 없습니다" })).toBeTruthy();
    expect(twinSessionFactory).not.toHaveBeenCalled();
  });

  it("links to the isolated factory route from the application shell", () => {
    render(
      <MemoryRouter>
        <App
          twinSessionFactory={() => {
            throw new Error("Navigation rendering must not create a Twin session");
          }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "공장 보기" }).getAttribute("href")).toBe(
      "/factory",
    );
  });
});

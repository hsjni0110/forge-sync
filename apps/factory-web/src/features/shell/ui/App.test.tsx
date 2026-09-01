import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { App } from "./App";

describe("App", () => {
  it("renders the ForgeSync runtime shell", () => {
    render(<App />);

    expect(screen.getByRole("heading", { name: "ForgeSync" })).toBeTruthy();
    expect(screen.getByText(/versioned Twin contracts/)).toBeTruthy();
  });
});

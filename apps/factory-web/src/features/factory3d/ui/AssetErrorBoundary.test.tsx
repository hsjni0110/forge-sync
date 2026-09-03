import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AssetErrorBoundary } from "./AssetErrorBoundary";

afterEach(cleanup);

describe("AssetErrorBoundary", () => {
  it.each(["GLB request returned 404", "GLB payload is invalid"])(
    "shows the generic primitive when %s",
    (message) => {
      const onError = vi.fn();
      const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

      render(
        <AssetErrorBoundary
          fallback={<div data-testid="generic-machine-primitive" />}
          onError={onError}
        >
          <BrokenAsset message={message} />
        </AssetErrorBoundary>,
      );

      expect(screen.getByTestId("generic-machine-primitive")).toBeTruthy();
      expect(onError).toHaveBeenCalledOnce();
      consoleError.mockRestore();
    },
  );
});

function BrokenAsset({ message }: { message: string }): never {
  throw new Error(message);
}

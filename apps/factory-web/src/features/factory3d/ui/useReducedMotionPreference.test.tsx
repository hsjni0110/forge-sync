import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useReducedMotionPreference } from "./useReducedMotionPreference";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("useReducedMotionPreference", () => {
  it("uses the OS preference until the operator explicitly overrides it", () => {
    const media = new FakeMediaQueryList(true);
    vi.stubGlobal("matchMedia", vi.fn(() => media));
    const { result } = renderHook(() => useReducedMotionPreference());

    expect(result.current.isReducedMotion).toBe(true);
    act(() => result.current.toggleReducedMotion());
    expect(result.current.isReducedMotion).toBe(false);

    act(() => media.change(false));
    expect(result.current.isReducedMotion).toBe(false);
  });

  it("tracks OS changes when there is no explicit override", () => {
    const media = new FakeMediaQueryList(false);
    vi.stubGlobal("matchMedia", vi.fn(() => media));
    const { result } = renderHook(() => useReducedMotionPreference());

    act(() => media.change(true));
    expect(result.current.isReducedMotion).toBe(true);
  });
});

class FakeMediaQueryList {
  readonly media = "(prefers-reduced-motion: reduce)";
  readonly onchange = null;
  private listener: ((event: MediaQueryListEvent) => void) | undefined;

  constructor(public matches: boolean) {}

  addEventListener(_type: string, listener: (event: MediaQueryListEvent) => void) {
    this.listener = listener;
  }

  removeEventListener(_type: string, listener: (event: MediaQueryListEvent) => void) {
    if (this.listener === listener) {
      this.listener = undefined;
    }
  }

  change(matches: boolean) {
    this.matches = matches;
    this.listener?.({ matches } as MediaQueryListEvent);
  }
}

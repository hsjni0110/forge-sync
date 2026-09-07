// jsdom has no ResizeObserver. The scene measures overlay coverage with one, so tests need a
// stand-in that reports nothing rather than throwing.
class NoopResizeObserver implements ResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

if (!("ResizeObserver" in globalThis)) {
  Object.defineProperty(globalThis, "ResizeObserver", {
    value: NoopResizeObserver,
    writable: true,
  });
}

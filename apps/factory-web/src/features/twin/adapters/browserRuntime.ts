import type { Clock, Timer } from "../application/ports";

export const browserClock: Clock = {
  nowMillis: () => Date.now(),
};

export const browserTimer: Timer = {
  schedule: (callback, delayMillis) => window.setTimeout(callback, delayMillis),
  cancel: (timerId) => window.clearTimeout(timerId),
};

import { useCallback, useEffect, useState } from "react";

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

export function useReducedMotionPreference(): {
  isReducedMotion: boolean;
  toggleReducedMotion: () => void;
} {
  const [osPreference, setOsPreference] = useState(readOsPreference);
  const [operatorOverride, setOperatorOverride] = useState<boolean>();
  const isReducedMotion = operatorOverride ?? osPreference;

  useEffect(() => {
    if (typeof window.matchMedia !== "function") {
      return;
    }
    const mediaQuery = window.matchMedia(REDUCED_MOTION_QUERY);
    const updatePreference = (event: MediaQueryListEvent) => {
      setOsPreference(event.matches);
    };
    setOsPreference(mediaQuery.matches);
    mediaQuery.addEventListener("change", updatePreference);
    return () => mediaQuery.removeEventListener("change", updatePreference);
  }, []);

  const toggleReducedMotion = useCallback(() => {
    setOperatorOverride(!isReducedMotion);
  }, [isReducedMotion]);

  return { isReducedMotion, toggleReducedMotion };
}

function readOsPreference(): boolean {
  return (
    typeof window.matchMedia === "function" &&
    window.matchMedia(REDUCED_MOTION_QUERY).matches
  );
}

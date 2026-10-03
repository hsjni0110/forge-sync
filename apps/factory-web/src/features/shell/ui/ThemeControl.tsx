import { Button } from "@carbon/react";
import { useEffect, useState } from "react";

export type ThemePreference = "SYSTEM" | "LIGHT" | "DARK";
export type CarbonTheme = "white" | "g100";

const STORAGE_KEY = "forgesync-theme";
const DARK_SCHEME_QUERY = "(prefers-color-scheme: dark)";

const PREFERENCE_LABELS: Record<ThemePreference, string> = {
  SYSTEM: "시스템",
  LIGHT: "밝게",
  DARK: "어둡게",
};

export function useThemePreference() {
  const [preference, setPreference] = useState<ThemePreference>(readPreference);
  const [systemIsDark, setSystemIsDark] = useState(readSystemPreference);

  useEffect(() => {
    const mediaQuery = readMediaQuery();
    if (mediaQuery === undefined) {
      return;
    }

    const updateSystemPreference = (event: MediaQueryListEvent) => {
      setSystemIsDark(event.matches);
    };
    mediaQuery.addEventListener("change", updateSystemPreference);
    return () => mediaQuery.removeEventListener("change", updateSystemPreference);
  }, []);

  useEffect(() => {
    persistPreference(preference);
  }, [preference]);

  const theme: CarbonTheme =
    preference === "DARK" || (preference === "SYSTEM" && systemIsDark)
      ? "g100"
      : "white";

  return { preference, setPreference, theme };
}

export function ThemeControl({
  preference,
  onPreferenceChange,
}: {
  preference: ThemePreference;
  onPreferenceChange: (preference: ThemePreference) => void;
}) {
  return (
    <Button
      aria-label="화면 테마"
      className="theme-control"
      kind="ghost"
      onClick={() => onPreferenceChange(nextPreference(preference))}
      size="sm"
      type="button"
    >
      <span>화면 테마</span>
      <strong>{PREFERENCE_LABELS[preference]}</strong>
    </Button>
  );
}

function nextPreference(preference: ThemePreference): ThemePreference {
  if (preference === "SYSTEM") {
    return "LIGHT";
  }
  if (preference === "LIGHT") {
    return "DARK";
  }
  return "SYSTEM";
}

function readPreference(): ThemePreference {
  try {
    const storedPreference = window.localStorage.getItem(STORAGE_KEY);
    return storedPreference === "LIGHT" || storedPreference === "DARK"
      ? storedPreference
      : "SYSTEM";
  } catch {
    return "SYSTEM";
  }
}

function persistPreference(preference: ThemePreference) {
  try {
    if (preference === "SYSTEM") {
      window.localStorage.removeItem(STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(STORAGE_KEY, preference);
  } catch {
    // Browser privacy settings can disable storage; the in-memory preference still works.
  }
}

function readSystemPreference() {
  return readMediaQuery()?.matches ?? false;
}

function readMediaQuery(): MediaQueryList | undefined {
  try {
    return window.matchMedia(DARK_SCHEME_QUERY);
  } catch {
    return undefined;
  }
}

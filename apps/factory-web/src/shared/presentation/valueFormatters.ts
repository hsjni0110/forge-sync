export interface UtcParts {
  date: string;
  time: string;
  zone: "UTC";
  exact: string;
}

export function formatDecimal(
  value: number,
  options: { maximumFractionDigits?: number } = {},
): string {
  return new Intl.NumberFormat("ko-KR", {
    maximumFractionDigits: options.maximumFractionDigits ?? 2,
    minimumFractionDigits: 0,
  }).format(value);
}

export function formatUtcParts(value: string): UtcParts {
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) {
    return { date: "확인할 수 없음", time: "--:--:--", zone: "UTC", exact: value };
  }
  return {
    date: `${instant.getUTCFullYear()}-${pad(instant.getUTCMonth() + 1)}-${pad(instant.getUTCDate())}`,
    time: `${pad(instant.getUTCHours())}:${pad(instant.getUTCMinutes())}:${pad(instant.getUTCSeconds())}`,
    zone: "UTC",
    exact: value,
  };
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

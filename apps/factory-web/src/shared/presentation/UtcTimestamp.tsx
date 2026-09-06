import { formatUtcParts } from "./valueFormatters";

export function UtcTimestamp({ value, compact = false }: { value: string; compact?: boolean }) {
  const formatted = formatUtcParts(value);
  return (
    <time
      className={`utc-timestamp${compact ? " utc-timestamp-compact" : ""}`}
      dateTime={value}
      title={formatted.exact}
      aria-label={`${formatted.date} ${formatted.time} UTC`}
    >
      <span className="utc-date">{formatted.date}</span>
      <span className="utc-clock">{formatted.time}</span>
      <span className="utc-zone">UTC</span>
    </time>
  );
}

import type {
  OperationalContextTone,
  OperationalContextValue,
} from "./OperationalContext";
import { useOperationalContextSnapshot } from "./OperationalContext";

export function OperationalContextBar() {
  const snapshot = useOperationalContextSnapshot();
  return (
    <section className="operational-context" aria-label="운영 상황" aria-live="polite">
      <div className="operational-machine">
        <span>선택 설비</span>
        <strong>{snapshot.machineId}</strong>
      </div>
      <ContextFact label="연결" value={snapshot.connection} />
      <ContextFact label="최신성" value={snapshot.freshness} />
      <ContextFact label="재생" value={snapshot.replay} />
    </section>
  );
}

function ContextFact({
  label,
  value,
}: {
  label: string;
  value: OperationalContextValue;
}) {
  return (
    <div className="operational-fact" data-tone={value.tone}>
      <StatusIcon tone={value.tone} />
      <span>{label}</span>
      <strong>{value.label}</strong>
    </div>
  );
}

function StatusIcon({ tone }: { tone: OperationalContextTone }) {
  if (tone === "positive") {
    return (
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <path d="m3 8.2 3 3L13 4.8" />
      </svg>
    );
  }
  if (tone === "warning") {
    return (
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <path d="M8 2 14 13H2L8 2Z" /><path d="M8 6v3.5M8 11.5v.5" />
      </svg>
    );
  }
  if (tone === "critical") {
    return (
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <path d="M3 3l10 10M13 3 3 13" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <circle cx="8" cy="8" r="5.5" /><path d="M8 7v4M8 5v.5" />
    </svg>
  );
}

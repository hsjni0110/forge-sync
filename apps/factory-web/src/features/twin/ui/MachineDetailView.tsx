import { useId, type ReactNode } from "react";

import type { TwinLiveState } from "../application/TwinLiveSession";
import type {
  MachineDetailCondition,
  MachineDetailMetric,
  MachineDetailViewModel,
} from "../application/machineDetailViewModel";
import { mapTwinToMachineDetail } from "../application/machineDetailViewModel";
import {
  deriveTwinPresentation,
  type ReplayLifecycleStatus,
} from "../application/twinPresentationPolicy";
import { TwinConnectionStatus } from "./TwinConnectionStatus";
import { UtcTimestamp } from "../../../shared/presentation/UtcTimestamp";

const NORMAL_LEVEL = "정상";
/** Values an operator reads as "what is the machine doing right now", not as a raw channel. */
const CURRENT_WORK_KEYS = ["program", "tool", "controller-mode", "power-state", "part-count"];
const CHANNEL_READING_PREFIXES = ["spindle-", "axis-", "load-", "temp-"];
const CHANNEL_READING_KEYS = ["b-axis", "feedrate"];
const CONDITION_SEVERITY_ORDER = ["고장", "확인할 수 없음", "주의", NORMAL_LEVEL];

interface MachineDetailViewProps {
  machineId: string;
  state: TwinLiveState;
  retryNow: () => void;
  layout?: "FULL" | "COMPACT";
  replayStatus?: ReplayLifecycleStatus;
}

export function MachineDetailView({
  machineId,
  state,
  retryNow,
  layout = "FULL",
  replayStatus,
}: MachineDetailViewProps) {
  if (!state.snapshot && state.connectionStatus === "LOADING") {
    return <MachineDetailLoading machineId={machineId} />;
  }
  if (!state.snapshot && state.failure === "NOT_FOUND") {
    return (
      <MachineDetailMessage
        title="설비를 찾을 수 없습니다"
        detail={`${machineId} 설비의 트윈 데이터가 아직 없습니다.`}
      />
    );
  }
  if (!state.snapshot) {
    return (
      <MachineDetailMessage
        title="설비 정보를 불러올 수 없습니다"
        detail="잠시 후 다시 시도해 주세요. 이전 데이터가 있다면 연결 복구 후 자동으로 표시됩니다."
        retryNow={retryNow}
      />
    );
  }

  const detail = mapTwinToMachineDetail(
    state.snapshot,
    state.freshness ?? state.snapshot.state.freshness.value,
  );
  const isRecovering =
    state.connectionStatus === "RECONNECTING" ||
    state.connectionStatus === "RESYNCING" ||
    state.connectionStatus === "UNAVAILABLE";
  const presentation = deriveTwinPresentation(state.freshness, replayStatus);

  if (layout === "COMPACT") {
    return (
      <MachineOperationalSummary
        detail={detail}
        state={state}
        isRecovering={isRecovering}
        replayStatus={replayStatus}
        presentation={presentation}
      />
    );
  }

  return (
    <article className="machine-detail">
      <header className="machine-header">
        <div>
          <p className="eyebrow">설비 디지털 트윈</p>
          <h1>{detail.machineId}</h1>
          <p className="machine-version">데이터 버전 {detail.twinVersion}</p>
        </div>
        <p>
          마지막 반영 시각 <UtcTimestamp value={detail.projectedAt} />
        </p>
      </header>

      <MachineHero detail={detail} />

      <TwinConnectionStatus state={state} replayStatus={replayStatus} />
      {isRecovering && (
        <div className="notice notice-warning" role="status">
          서버에 다시 연결하고 있습니다. 연결되기 전까지 마지막으로 받은 값을 표시합니다.
        </div>
      )}
      {presentation.notice && (
        <div
          className={`notice ${presentation.noticeTone === "danger" ? "notice-danger" : "notice-neutral"}`}
          role={presentation.warnsAgainstRealtimeUse ? "alert" : "status"}
        >
          {presentation.notice}
        </div>
      )}

      <div className="detail-grid">
        <DetailSection title="지금 작업" wide>
          <div className="metric-grid">
            {detail.metrics
              .filter((metric) => CURRENT_WORK_KEYS.includes(metric.key))
              .map((metric) => (
                <div className="metric-card" key={metric.key}>
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                </div>
              ))}
            <div className="metric-card">
              <span>주축 속도</span>
              <strong>{detail.spindleSummary.value}</strong>
              <small>{detail.spindleSummary.detail}</small>
            </div>
          </div>
        </DetailSection>

        <DetailSection title="현재 상태">
          <DefinitionList
            entries={[
              ...presentedStates(detail.states, replayStatus).map(({ label, value }) => [label, value] as const),
              ["조회 시 데이터 경과 시간", formatAge(detail.freshness.ageMillis)],
            ]}
          />
        </DetailSection>

        <DetailSection title="측정값" wide>
          <p className="empty-state">채널별 원본 측정값입니다. 요약은 위 "지금 작업"을 참고하세요.</p>
          <div className="metric-grid">
            {detail.metrics
              .filter(isChannelReading)
              .map((metric) => (
              <div className="metric-card" key={metric.key}>
                <span>{metric.label}</span>
                <strong>{metric.value}</strong>
                {metric.detail && <small>{metric.detail}</small>}
              </div>
            ))}
          </div>
        </DetailSection>

        <DetailSection title="상태 신호">
          {detail.conditions.length === 0 ? (
            <p className="empty-state">현재 들어온 상태 신호가 없습니다.</p>
          ) : (
            <ConditionSummary conditions={detail.conditions} />
          )}
        </DetailSection>

        <DetailSection title="데이터 품질">
          <DefinitionList
            entries={[["데이터 구성", consistencyLabel(detail.consistency, replayStatus)]]}
          />
          <h3>현재 없는 핵심 정보</h3>
          {detail.missingFields.length === 0 ? (
            <p className="empty-state">빠진 핵심 정보가 없습니다.</p>
          ) : (
            <ul>
              {detail.missingFields.map((field) => (
                <li key={field}>{field}</li>
              ))}
            </ul>
          )}
          <p className="section-note">
            데이터 유효성, 순서, 중복 여부 같은 상세 품질 정보는 아직 제공하지 않습니다.
          </p>
        </DetailSection>

        <DetailSection title="데이터 출처" wide>
          {detail.provenance.length === 0 ? (
            <p className="empty-state">표시할 데이터 출처가 없습니다.</p>
          ) : (
            <details className="provenance-disclosure">
              <summary>
                원본 추적 정보 {detail.provenance.length}건 · {detail.provenanceGroups.length}개 출처
              </summary>
              <div className="provenance-groups">
                {detail.provenanceGroups.map((group) => (
                  <section key={group.key} className="provenance-group">
                    <h3>{group.sourceLabel} · {group.items.length}건</h3>
                    <p>원본 데이터 묶음 · {group.sourceSetId}</p>
                    <div className="provenance-list">
                      {group.items.map((item) => (
                        <article key={item.key} className="provenance-card">
                          <header><h4>{item.field}</h4></header>
                          <DefinitionList entries={[
                            ["원본 항목 ID", item.sourceDataItemId],
                            ["변환 규칙 버전", item.mappingVersion],
                            ["원본 파일 식별자", item.artifactId],
                            ["원본 레코드 위치", item.rawRecordId],
                          ]} />
                        </article>
                      ))}
                    </div>
                  </section>
                ))}
              </div>
            </details>
          )}
        </DetailSection>

        <DetailSection title="기본 정보">
          <DefinitionList
            entries={[
              ["설비 ID", detail.machineId],
              ["데이터 버전", String(detail.twinVersion)],
              ["데이터 형식 버전", state.snapshot.schemaVersion],
            ]}
          />
        </DetailSection>
      </div>
    </article>
  );
}

function MachineHero({ detail }: { detail: MachineDetailViewModel }) {
  const severity = !detail.primaryCondition
    ? "normal"
    : detail.primaryCondition.level === "고장"
      ? "fault"
      : "warning";
  return (
    <section className="machine-hero" data-severity={severity} aria-label="설비 가동 상태">
      <p className="eyebrow">설비 상태</p>
      <div className="machine-hero-top">
        <span className="machine-hero-dot" aria-hidden="true" />
        <strong className="machine-hero-title">{detail.executionState}</strong>
      </div>
      {detail.primaryCondition && (
        <p className="machine-hero-condition">
          동시 관측 · {detail.primaryCondition.conditionType} · {detail.primaryCondition.level}
          {detail.primaryCondition.message ? ` · ${detail.primaryCondition.message}` : ""}
        </p>
      )}
    </section>
  );
}

function formatAge(millis: number): string {
  const totalSeconds = Math.max(0, Math.round(millis / 1000));
  if (totalSeconds < 60) return `${totalSeconds}초 전`;
  const totalMinutes = Math.round(totalSeconds / 60);
  if (totalMinutes < 60) return `${totalMinutes}분 전`;
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return minutes === 0 ? `${hours}시간 전` : `${hours}시간 ${minutes}분 전`;
}

function MachineOperationalSummary({
  detail,
  state,
  isRecovering,
  replayStatus,
  presentation,
}: {
  detail: ReturnType<typeof mapTwinToMachineDetail>;
  state: TwinLiveState;
  isRecovering: boolean;
  replayStatus?: ReplayLifecycleStatus;
  presentation: ReturnType<typeof deriveTwinPresentation>;
}) {
  return (
    <article className="machine-summary">
      <header className="summary-header">
        <div>
          <p className="eyebrow">선택 설비</p>
          <h2>{detail.machineId}</h2>
        </div>
        <span className="machine-version">v{detail.twinVersion}</span>
      </header>

      <TwinConnectionStatus state={state} layout="COMPACT" replayStatus={replayStatus} />
      {(isRecovering || presentation.notice) && (
        <div
          className={`notice ${presentation.noticeTone === "danger" ? "notice-danger" : isRecovering ? "notice-warning" : "notice-neutral"}`}
          role={presentation.warnsAgainstRealtimeUse ? "alert" : "status"}
        >
          {presentation.notice ?? "서버 연결을 복구하는 동안 마지막 값을 표시합니다."}
        </div>
      )}

      <section className="summary-section" aria-labelledby="summary-state-title">
        <h2 id="summary-state-title">운영 상태</h2>
        <div className="summary-state-grid">
          {presentedStates(detail.states, replayStatus).map(({ label, value }) => (
            <div key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
      </section>

      <section className="summary-section" aria-labelledby="summary-metrics-title">
        <h2 id="summary-metrics-title">핵심 측정값</h2>
        <div className="summary-metric-grid">
          {detail.metrics.map((metric) => (
            <div key={metric.key} className="summary-metric">
              <span>{metric.label}</span>
              <strong>{metric.value}</strong>
            </div>
          ))}
        </div>
      </section>

      <footer className="summary-footer">
        <span>최근 반영 · <UtcTimestamp value={detail.projectedAt} compact /></span>
        <span className="summary-footer-hint">상단 2D 보기에서 전체 상세를 확인할 수 있습니다.</span>
      </footer>
    </article>
  );
}

function MachineDetailLoading({ machineId }: { machineId: string }) {
  return (
    <section className="page-message" aria-busy="true" aria-live="polite">
      <p className="eyebrow">설비 디지털 트윈</p>
      <h1>{machineId} 정보를 불러오는 중입니다</h1>
      <p>서버에서 최신 설비 정보를 확인하고 있습니다.</p>
    </section>
  );
}

function MachineDetailMessage({
  title,
  detail,
  retryNow,
}: {
  title: string;
  detail: string;
  retryNow?: () => void;
}) {
  return (
    <section className="page-message" role="alert">
      <p className="eyebrow">설비 디지털 트윈</p>
      <h1>{title}</h1>
      <p>{detail}</p>
      {retryNow && (
        <button type="button" className="button-quiet" onClick={retryNow}>
          다시 시도
        </button>
      )}
    </section>
  );
}

function isChannelReading(metric: MachineDetailMetric): boolean {
  return (
    CHANNEL_READING_PREFIXES.some((prefix) => metric.key.startsWith(prefix)) ||
    CHANNEL_READING_KEYS.includes(metric.key)
  );
}

function DetailSection({
  title,
  wide = false,
  children,
}: {
  title: string;
  wide?: boolean;
  children: ReactNode;
}) {
  const headingId = useId();
  return (
    <section
      className={`detail-section${wide ? " detail-section-wide" : ""}`}
      aria-labelledby={headingId}
    >
      <h2 id={headingId}>{title}</h2>
      {children}
    </section>
  );
}

function DefinitionList({ entries }: { entries: ReadonlyArray<readonly [string, string]> }) {
  return (
    <dl className="definition-list">
      {entries.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function consistencyLabel(value: string, replayStatus?: ReplayLifecycleStatus): string {
  if ((replayStatus === "COMPLETED" || replayStatus === "PAUSED") && value === "STALE") {
    return "재생 기준 데이터";
  }
  return {
    CONSISTENT: "정상",
    PARTIAL: "일부 정보 부족",
    DEGRADED: "품질 저하",
    STALE: "오래된 데이터",
  }[value] ?? value;
}

function presentedStates(
  states: Array<{ label: string; value: string }>,
  replayStatus?: ReplayLifecycleStatus,
): Array<{ label: string; value: string }> {
  if (replayStatus !== "COMPLETED" && replayStatus !== "PAUSED") return states;
  return states.map((state) =>
    state.label === "네트워크 연결" ? { ...state, value: "Replay 연결됨" } : state,
  );
}

function ConditionSummary({ conditions }: { conditions: MachineDetailCondition[] }) {
  const counts = CONDITION_SEVERITY_ORDER
    .map((level) => ({ level, count: conditions.filter((condition) => condition.level === level).length }))
    .filter(({ count }) => count > 0);
  const attention = conditions.filter((condition) => condition.level !== NORMAL_LEVEL);
  const normal = conditions.filter((condition) => condition.level === NORMAL_LEVEL);
  return (
    <>
      <ul className="condition-summary">
        {counts.map(({ level, count }) => (
          <li key={level} data-level={level}>{level} {count}</li>
        ))}
      </ul>
      {attention.length > 0 ? (
        <ul className="condition-list">
          {attention.map((condition) => <ConditionItem key={condition.key} condition={condition} />)}
        </ul>
      ) : (
        <p className="empty-state">주의가 필요한 상태 신호가 없습니다.</p>
      )}
      {normal.length > 0 && (
        <details className="provenance-disclosure">
          <summary>정상 상태 신호 모두 보기 ({normal.length}개)</summary>
          <ul className="condition-list">
            {normal.map((condition) => <ConditionItem key={condition.key} condition={condition} />)}
          </ul>
        </details>
      )}
    </>
  );
}

function ConditionItem({ condition }: { condition: MachineDetailCondition }) {
  return (
    <li>
      <strong>{condition.conditionType}</strong>
      <span>{condition.level}</span>
      <small>{condition.componentId}</small>
      {condition.message && <p>{condition.message}</p>}
    </li>
  );
}

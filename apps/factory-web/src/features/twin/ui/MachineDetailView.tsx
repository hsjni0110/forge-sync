import type { ReactNode } from "react";

import type { TwinLiveState } from "../application/TwinLiveSession";
import { mapTwinToMachineDetail } from "../application/machineDetailViewModel";
import { TwinConnectionStatus } from "./TwinConnectionStatus";

interface MachineDetailViewProps {
  machineId: string;
  state: TwinLiveState;
  retryNow: () => void;
}

export function MachineDetailView({
  machineId,
  state,
  retryNow,
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
  const isStale = state.freshness === "STALE";

  return (
    <article className="machine-detail">
      <header className="machine-header">
        <div>
          <p className="eyebrow">설비 디지털 트윈</p>
          <h1>{detail.machineId}</h1>
          <p className="machine-version">데이터 버전 {detail.twinVersion}</p>
        </div>
        <p>
          마지막 반영 시각 <UtcTime value={detail.projectedAt} />
        </p>
      </header>

      <TwinConnectionStatus state={state} />
      {isRecovering && (
        <div className="notice notice-warning" role="status">
          서버에 다시 연결하고 있습니다. 연결되기 전까지 마지막으로 받은 값을 표시합니다.
        </div>
      )}
      {isStale && (
        <div className="notice notice-danger" role="alert">
          오래된 데이터입니다. 현재 설비의 실시간 상태로 판단하지 마세요.
        </div>
      )}

      <div className="detail-grid">
        <DetailSection title="기본 정보">
          <DefinitionList
            entries={[
              ["설비 ID", detail.machineId],
              ["데이터 버전", String(detail.twinVersion)],
              ["데이터 형식 버전", state.snapshot.schemaVersion],
            ]}
          />
        </DetailSection>

        <DetailSection title="현재 상태">
          <DefinitionList
            entries={[
              ...detail.states.map(({ label, value }) => [label, value] as const),
              ["데이터 최신 상태", freshnessLabel(state.freshness ?? detail.freshness.value)],
              ["조회 시 데이터 경과 시간", `${detail.freshness.ageMillis}ms`],
            ]}
          />
        </DetailSection>

        <DetailSection title="측정값" wide>
          <div className="metric-grid">
            {detail.metrics.map((metric) => (
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
            <ul className="condition-list">
              {detail.conditions.map((condition) => (
                <li key={condition.key}>
                  <strong>{condition.conditionType}</strong>
                  <span>{condition.level}</span>
                  <small>{condition.componentId}</small>
                  {condition.message && <p>{condition.message}</p>}
                </li>
              ))}
            </ul>
          )}
        </DetailSection>

        <DetailSection title="데이터 품질">
          <DefinitionList
            entries={[
              ["데이터 구성", consistencyLabel(detail.consistency)],
              ["데이터 최신 상태", freshnessLabel(state.freshness ?? detail.freshness.value)],
            ]}
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
            <div className="provenance-list">
              {detail.provenance.map((item) => (
                <article key={item.key} className="provenance-card">
                  <header>
                    <h3>{item.field}</h3>
                    <span className="source-badge">{item.sourceLabel}</span>
                  </header>
                  <DefinitionList
                    entries={[
                      ["원본 데이터 묶음", item.sourceSetId],
                      ["원본 항목 ID", item.sourceDataItemId],
                      ["변환 규칙 버전", item.mappingVersion],
                      ["원본 파일 식별자", item.artifactId],
                      ["원본 레코드 위치", item.rawRecordId],
                    ]}
                  />
                </article>
              ))}
            </div>
          )}
        </DetailSection>
      </div>
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
        <button type="button" onClick={retryNow}>
          다시 시도
        </button>
      )}
    </section>
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
  return (
    <section className={`detail-section${wide ? " detail-section-wide" : ""}`}>
      <h2>{title}</h2>
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

function UtcTime({ value }: { value: string }) {
  return <time dateTime={value}>{value.replace("T", " ").replace("Z", " UTC")}</time>;
}

function freshnessLabel(value: string): string {
  return {
    FRESH: "최신",
    LAGGING: "조금 지연됨",
    STALE: "오래된 데이터",
  }[value] ?? value;
}

function consistencyLabel(value: string): string {
  return {
    CONSISTENT: "정상",
    PARTIAL: "일부 정보 부족",
    DEGRADED: "품질 저하",
    STALE: "오래된 데이터",
  }[value] ?? value;
}

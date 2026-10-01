import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { InlineNotification, SkeletonText } from "@carbon/react";

import type { ReplayControlClient } from "../../replay/application/ports";
import type { ReplayStatus } from "../../replay/domain/replay";
import { useReplayController } from "../../replay/ui/useReplayController";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import { mapTwinToMachineDetail } from "../../twin/application/machineDetailViewModel";
import type { TwinSessionFactory } from "../../twin/application/ports";
import { TwinConnectionStatus } from "../../twin/ui/TwinConnectionStatus";
import { useTwinLiveSession } from "../../twin/ui/useTwinLiveSession";
import { deriveTwinPresentation } from "../../twin/application/twinPresentationPolicy";
import { UtcTimestamp } from "../../../shared/presentation/UtcTimestamp";
import type { DowntimeParetoClient } from "../../downtime/application/ports";
import type { DowntimeParetoReport } from "../../downtime/domain/downtimePareto";
import { DowntimeParetoPanel } from "../../downtime/ui/DowntimeParetoPanel";
import type { OperationalEffectivenessClient } from "../../effectiveness/application/ports";
import type { OperationalEffectivenessReport } from "../../effectiveness/domain/operationalEffectiveness";
import { OperationalEffectivenessPanel } from "../../effectiveness/ui/OperationalEffectivenessPanel";
import type { ShiftOverviewClient } from "../../shift-overview/application/ports";
import { ShiftOverviewError } from "../../shift-overview/application/ports";
import type { ShiftOverview } from "../../shift-overview/domain/shiftOverview";
import { ShiftOverviewPanel } from "../../shift-overview/ui/ShiftOverviewPanel";
import type { AlarmClient } from "../../alarm/application/ports";
import type { Alarm } from "../../alarm/domain/alarm";
import {
  useOperationalContextPublisher,
  type OperationalContextValue,
} from "../../shell/ui/OperationalContext";
import { PROCESS_GLOSSARY } from "../../../shared/presentation/processGlossary";

const MACHINE_ID = "Mazak01";

const REPLAY_STATUS_LABELS: Record<ReplayStatus, string> = {
  PREPARING: "준비 중",
  RUNNING: "재생 중",
  PAUSED: "일시정지됨",
  SEEKING: "이동 중",
  COMPLETED: "재생 완료",
  FAILED: "재생 실패",
};

export function DashboardRoute({
  twinSessionFactory,
  replayControlClient,
  downtimeParetoClient,
  operationalEffectivenessClient,
  shiftOverviewClient,
  alarmClient,
}: {
  twinSessionFactory: TwinSessionFactory;
  replayControlClient?: ReplayControlClient;
  downtimeParetoClient?: DowntimeParetoClient;
  operationalEffectivenessClient?: OperationalEffectivenessClient;
  shiftOverviewClient?: ShiftOverviewClient;
  alarmClient?: AlarmClient;
}) {
  const createSession = useCallback(
    () => twinSessionFactory(MACHINE_ID),
    [twinSessionFactory],
  );
  const { state, retryNow } = useTwinLiveSession(createSession);
  const replay = useReplayController(MACHINE_ID, replayControlClient);
  const [downtimeReport, setDowntimeReport] = useState<DowntimeParetoReport>();
  const [downtimeFailure, setDowntimeFailure] = useState(false);
  const [effectivenessReport, setEffectivenessReport] = useState<OperationalEffectivenessReport>();
  const [effectivenessFailure, setEffectivenessFailure] = useState(false);
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [shiftLoad, setShiftLoad] = useState<{
    status: "IDLE" | "LOADING" | "READY" | "FAILED";
    report?: ShiftOverview;
    failure?: "NETWORK" | "INSUFFICIENT_DATA" | "VERSION_MISMATCH";
    diagnostic?: string;
  }>({ status: "IDLE" });
  const replaySession = replay.authoritativeSession;
  const snapshot = state.snapshot;
  useOperationalContextPublisher({
    machineId: MACHINE_ID,
    connection: connectionContext(state.connectionStatus),
    freshness: freshnessContext(state.freshness),
    replay: replayContext(replay.session?.status),
  });
  const isShiftCursorPending = Boolean(
    shiftOverviewClient
      && snapshot
      && replaySession
      && ["PAUSED", "COMPLETED"].includes(replaySession.status)
      && replaySession.replaySessionId === snapshot.replayCursor.replaySessionId
      && replaySession.publicationCursor
      && snapshot.replayCursor.replaySequence < replaySession.publicationCursor.replaySequence,
  );

  useEffect(() => {
    const snapshot = state.snapshot;
    if (!shiftOverviewClient || !snapshot || !replaySession
      || !["PAUSED", "COMPLETED"].includes(replaySession.status)
      || replaySession.replaySessionId !== snapshot.replayCursor.replaySessionId
      || (replaySession.publicationCursor
        && snapshot.replayCursor.replaySequence < replaySession.publicationCursor.replaySequence)) return;
    const throughReplaySequence = replaySession.publicationCursor?.replaySequence
      ?? snapshot.replayCursor.replaySequence;
    const abort = new AbortController();
    setShiftLoad({ status: "LOADING" });
    void shiftOverviewClient.load(MACHINE_ID, snapshot.replayCursor.replaySessionId,
      throughReplaySequence, abort.signal)
      .then((report) => { if (!abort.signal.aborted) setShiftLoad({ status: "READY", report }); })
      .catch((failure: unknown) => {
        if (abort.signal.aborted) return;
        setShiftLoad({ status: "FAILED", failure: failure instanceof ShiftOverviewError
          ? failure.code : "NETWORK", diagnostic: failure instanceof Error ? failure.message : undefined });
      });
    return () => abort.abort();
  }, [replaySession, shiftOverviewClient, state.snapshot]);

  useEffect(() => {
    if (!alarmClient || !snapshot || !replaySession
      || !["PAUSED", "COMPLETED"].includes(replaySession.status)
      || replaySession.replaySessionId !== snapshot.replayCursor.replaySessionId) return;
    const abort = new AbortController();
    const throughReplaySequence = replaySession.publicationCursor?.replaySequence
      ?? snapshot.replayCursor.replaySequence;
    void alarmClient.find(MACHINE_ID, snapshot.replayCursor.replaySessionId,
      throughReplaySequence, abort.signal)
      .then((timeline) => { if (!abort.signal.aborted) setAlarms(timeline.alarms); })
      .catch(() => undefined);
    return () => abort.abort();
  }, [alarmClient, replaySession, snapshot]);

  useEffect(() => {
    const snapshot = state.snapshot;
    if (
      !downtimeParetoClient ||
      !snapshot ||
      !replaySession ||
      !["PAUSED", "COMPLETED"].includes(replaySession.status) ||
      replaySession.replaySessionId !== snapshot.replayCursor.replaySessionId
    ) {
      return;
    }
    const abort = new AbortController();
    setDowntimeFailure(false);
    void downtimeParetoClient
      .analyze(
        MACHINE_ID,
        snapshot.replayCursor.replaySessionId,
        snapshot.replayCursor.replaySequence,
        abort.signal,
      )
      .then((report) => {
        if (!abort.signal.aborted) setDowntimeReport(report);
      })
      .catch(() => {
        if (!abort.signal.aborted) setDowntimeFailure(true);
      });
    return () => abort.abort();
  }, [downtimeParetoClient, replaySession, state.snapshot]);

  useEffect(() => {
    const snapshot = state.snapshot;
    if (!operationalEffectivenessClient || !snapshot || !replaySession
      || !["PAUSED", "COMPLETED"].includes(replaySession.status)
      || replaySession.replaySessionId !== snapshot.replayCursor.replaySessionId) return;
    const abort = new AbortController();
    setEffectivenessFailure(false);
    void operationalEffectivenessClient.analyze(MACHINE_ID,
      snapshot.replayCursor.replaySessionId, snapshot.replayCursor.replaySequence, abort.signal)
      .then((report) => { if (!abort.signal.aborted) setEffectivenessReport(report); })
      .catch(() => { if (!abort.signal.aborted) setEffectivenessFailure(true); });
    return () => abort.abort();
  }, [operationalEffectivenessClient, replaySession, state.snapshot]);

  const seekToDowntime = (startedAt: string) => {
    if (!replayControlClient) return;
    void replay.run("SEEKING", (current) =>
      replayControlClient.seek(
        current.replaySessionId,
        current.revision,
        startedAt,
        current.speedMultiplier,
      ),
    );
  };

  return (
    <section className="dashboard-page">
      <header className="dashboard-header">
        <p className="eyebrow">제조 운영 디지털 트윈</p>
        <h1>교대조 개요</h1>
        <p>{MACHINE_ID} 전체 관측 구간의 가동 상태와 주요 손실을 확인하세요.</p>
      </header>

      {state.snapshot ? (
        shiftOverviewClient ? (
          <TwinConnectionStatus state={state} replayStatus={replay.session?.status} />
        ) : (
          <DashboardSummary
            state={state}
            snapshot={state.snapshot}
            replayStatus={replay.session?.status}
            replaySpeed={replay.session?.speedMultiplier}
          />
        )
      ) : (
        <DashboardPlaceholder state={state} retryNow={retryNow} />
      )}

      {shiftOverviewClient && (
        <AnalysisReadiness
          isCursorPending={isShiftCursorPending}
          load={shiftLoad}
          replayStatus={replaySession?.status}
        />
      )}

      {shiftLoad.report && !isShiftCursorPending ? (
        <>
          <ShiftOverviewPanel report={shiftLoad.report} freshness={state.freshness}
            replayStatus={replaySession?.status} onSeek={seekToDowntime} alarms={alarms} />
          <DowntimeParetoPanel report={shiftLoad.report.pareto} onSelect={seekToDowntime} />
        </>
      ) : null}

      {!shiftOverviewClient && downtimeReport ? (
        <DowntimeParetoPanel report={downtimeReport} onSelect={seekToDowntime} />
      ) : downtimeFailure ? (
        <p className="downtime-unavailable" role="status">
          정지 시간 분석을 불러오지 못했습니다.
        </p>
      ) : null}

      {!shiftOverviewClient && effectivenessReport ? (
        <OperationalEffectivenessPanel report={effectivenessReport} />
      ) : effectivenessFailure ? (
        <p className="effectiveness-unavailable" role="status">운영 효과 분석을 불러오지 못했습니다.</p>
      ) : null}

      <div className="dashboard-actions">
        <Link className="primary-link" to="/factory">
          시점 상세 보기
        </Link>
        <p className="dashboard-provenance">
          공장 배치는 {PROCESS_GLOSSARY.SIMULATED.label}, 설비 측정값은 실측 관찰값입니다.
          <small data-evidence="SIMULATED_LAYOUT REAL:NIST">
            배치 SIMULATED · 측정 REAL · NIST Mazak01
          </small>
        </p>
      </div>
    </section>
  );
}

function AnalysisReadiness({
  isCursorPending,
  load,
  replayStatus,
}: {
  isCursorPending: boolean;
  load: {
    status: "IDLE" | "LOADING" | "READY" | "FAILED";
    report?: ShiftOverview;
    failure?: "NETWORK" | "INSUFFICIENT_DATA" | "VERSION_MISMATCH";
    diagnostic?: string;
  };
  replayStatus?: ReplayStatus;
}) {
  let content: ReactNode;

  if (isCursorPending) {
    content = <p>재생 데이터가 화면에 반영되기를 기다리는 중입니다.</p>;
  } else if (load.status === "LOADING") {
    content = (
      <div className="analysis-readiness-loading" aria-busy="true">
        <SkeletonText width="12rem" />
        <p>교대조 분석을 불러오는 중입니다.</p>
      </div>
    );
  } else if (load.status === "FAILED") {
    content = (
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        role="alert"
        title="분석을 준비하지 못했습니다"
        subtitle={shiftFailureMessage(load.failure)}
        data-shift-diagnostic={load.diagnostic}
      />
    );
  } else if (load.report) {
    content = (
      <div className="analysis-readiness-ready">
        <CheckIcon />
        <div>
          <strong>교대조 분석 준비됨</strong>
          <p>화면과 분석이 같은 재생 버전을 사용합니다.</p>
        </div>
      </div>
    );
  } else if (replayStatus && !["PAUSED", "COMPLETED"].includes(replayStatus)) {
    content = (
      <InlineNotification
        kind="info"
        lowContrast
        hideCloseButton
        title="분석 대기 중"
        subtitle="과거 데이터 재생을 일시정지하거나 완료하면 같은 버전의 교대조 분석을 표시합니다."
      />
    );
  } else {
    content = <p>재생 세션과 분석 구간을 선택하면 조사할 수 있습니다.</p>;
  }

  return (
    <section className="analysis-readiness" aria-label="분석 가능 상태" aria-live="polite">
      <span className="analysis-readiness-label">분석 가능 상태</span>
      {content}
    </section>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M13.4 4.3 6.7 11 2.9 7.2l.9-.9 2.9 2.9 5.8-5.8.9.9Z" />
    </svg>
  );
}

function connectionContext(status: TwinLiveState["connectionStatus"]): OperationalContextValue {
  return {
    LOADING: { label: "불러오는 중", tone: "neutral" },
    LIVE: { label: "연결됨", tone: "positive" },
    RECONNECTING: { label: "다시 연결 중", tone: "warning" },
    RESYNCING: { label: "상태 동기화 중", tone: "warning" },
    UNAVAILABLE: { label: "연결할 수 없음", tone: "critical" },
  }[status] as OperationalContextValue;
}

function freshnessContext(freshness: TwinLiveState["freshness"]): OperationalContextValue {
  if (freshness === "FRESH") return { label: "최신", tone: "positive" };
  if (freshness === "LAGGING") return { label: "반영 지연", tone: "warning" };
  if (freshness === "STALE") return { label: "오래된 데이터", tone: "critical" };
  return { label: "최신성 확인 중", tone: "neutral" };
}

function replayContext(status: ReplayStatus | undefined): OperationalContextValue {
  if (status === undefined) {
    return { label: `${PROCESS_GLOSSARY.REPLAY.label} 시작 전`, tone: "neutral" };
  }
  const tone = status === "FAILED" ? "critical"
    : status === "RUNNING" ? "positive"
      : status === "SEEKING" || status === "PREPARING" ? "warning" : "neutral";
  return {
    label: `${PROCESS_GLOSSARY.REPLAY.label} ${REPLAY_STATUS_LABELS[status]}`,
    tone,
  };
}

function shiftFailureMessage(failure?: "NETWORK" | "INSUFFICIENT_DATA" | "VERSION_MISMATCH"): string {
  if (failure === "VERSION_MISMATCH") {
    return "분석 버전이 일치하지 않습니다. 재생 위치를 다시 맞춘 뒤 확인해 주세요.";
  }
  if (failure === "INSUFFICIENT_DATA") return "교대조를 분석할 관측 데이터가 아직 충분하지 않습니다.";
  return "교대조 분석을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.";
}

function DashboardSummary({
  state,
  snapshot,
  replayStatus,
  replaySpeed,
}: {
  state: TwinLiveState;
  snapshot: NonNullable<TwinLiveState["snapshot"]>;
  replayStatus?: ReplayStatus;
  replaySpeed?: number;
}) {
  const detail = mapTwinToMachineDetail(
    snapshot,
    state.freshness ?? snapshot.state.freshness.value,
  );
  const attentionSignals = detail.conditions.filter(
    (condition) => condition.level !== "정상",
  ).length;
  const isRecovering =
    state.connectionStatus === "RECONNECTING" ||
    state.connectionStatus === "RESYNCING" ||
    state.connectionStatus === "UNAVAILABLE";
  const presentation = deriveTwinPresentation(state.freshness, replayStatus);

  return (
    <>
      <TwinConnectionStatus state={state} replayStatus={replayStatus} />
      {presentation.notice ? (
        <div
          className={`notice ${presentation.noticeTone === "danger" ? "notice-danger" : "notice-neutral"}`}
          role={presentation.warnsAgainstRealtimeUse ? "alert" : "status"}
        >
          {presentation.notice}
        </div>
      ) : isRecovering ? (
        <div className="notice notice-warning" role="status">
          서버에 다시 연결하고 있습니다. 연결되기 전까지 마지막으로 받은 값을 표시합니다.
        </div>
      ) : null}

      <div className="metric-grid dashboard-kpis">
        <Kpi label="가동 상태" value={detail.executionState} />
        <Kpi
          label="주축 속도"
          value={detail.spindleSummary.value}
          detail={detail.spindleSummary.detail}
        />
        <Kpi
          label="주의가 필요한 상태 신호"
          value={attentionSignals === 0 ? "없음" : `${attentionSignals}건`}
          detail={`상태 신호 ${detail.conditions.length}건 중`}
        />
        <Kpi
          label={PROCESS_GLOSSARY.REPLAY.label}
          value={replayStatus ? REPLAY_STATUS_LABELS[replayStatus] : "시작 전"}
          detail={replayStatus && replaySpeed ? `${replaySpeed}x 배속` : undefined}
        />
        <Kpi
          label="트윈 데이터 버전"
          value={`v${detail.twinVersion}`}
          detailNode={<span>마지막 반영 <UtcTimestamp value={detail.projectedAt} compact /></span>}
        />
      </div>
    </>
  );
}

function Kpi({
  label,
  value,
  detail,
  detailNode,
}: {
  label: string;
  value: string;
  detail?: string;
  detailNode?: ReactNode;
}) {
  return (
    <div className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
      {(detail || detailNode) && <small>{detailNode ?? detail}</small>}
    </div>
  );
}

function DashboardPlaceholder({
  state,
  retryNow,
}: {
  state: TwinLiveState;
  retryNow: () => void;
}) {
  if (state.connectionStatus === "LOADING") {
    return (
      <section className="dashboard-placeholder" aria-busy="true" aria-live="polite">
        <p>{MACHINE_ID} 설비 상태를 불러오는 중입니다.</p>
      </section>
    );
  }
  return (
    <section className="dashboard-placeholder" role="alert">
      <p>
        {state.failure === "NOT_FOUND"
          ? `${MACHINE_ID} 설비의 트윈 데이터가 아직 없습니다.`
          : "설비 상태를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."}
      </p>
      {state.failure !== "NOT_FOUND" && (
        <button type="button" className="button-quiet" onClick={retryNow}>
          다시 시도
        </button>
      )}
    </section>
  );
}

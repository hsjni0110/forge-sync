import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";

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
}: {
  twinSessionFactory: TwinSessionFactory;
  replayControlClient?: ReplayControlClient;
  downtimeParetoClient?: DowntimeParetoClient;
  operationalEffectivenessClient?: OperationalEffectivenessClient;
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
  const replaySession = replay.authoritativeSession;

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
        <h1>ForgeSync</h1>
        <p>{MACHINE_ID} 설비의 현재 상태와 데이터 최신성을 한눈에 확인하세요.</p>
      </header>

      {state.snapshot ? (
        <DashboardSummary
          state={state}
          snapshot={state.snapshot}
          replayStatus={replay.session?.status}
          replaySpeed={replay.session?.speedMultiplier}
        />
      ) : (
        <DashboardPlaceholder state={state} retryNow={retryNow} />
      )}

      {downtimeReport ? (
        <DowntimeParetoPanel report={downtimeReport} onSelect={seekToDowntime} />
      ) : downtimeFailure ? (
        <p className="downtime-unavailable" role="status">
          정지 시간 분석을 불러오지 못했습니다.
        </p>
      ) : null}

      {effectivenessReport ? (
        <OperationalEffectivenessPanel report={effectivenessReport} />
      ) : effectivenessFailure ? (
        <p className="effectiveness-unavailable" role="status">운영 효과 분석을 불러오지 못했습니다.</p>
      ) : null}

      <div className="dashboard-actions">
        <Link className="primary-link" to="/factory">
          운영 뷰 열기
        </Link>
        <p className="dashboard-provenance">
          공장 배치는 SIMULATED, 설비 측정값은 REAL&nbsp;·&nbsp;NIST Mazak01 replay 입니다.
        </p>
      </div>
    </section>
  );
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
          label="Replay"
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

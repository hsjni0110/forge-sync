import { useEffect, useState } from "react";

import type { ReplayControlClient } from "../application/ports";
import type { ReplaySessionState, ReplayStatus } from "../domain/replay";
import type { Freshness, TwinSnapshot } from "../../twin/domain/twin";
import { useReplayController, type ReplayController } from "./useReplayController";

interface ReplayControlsProps {
  machineId: string;
  client: ReplayControlClient;
  snapshot?: TwinSnapshot;
  freshness?: Freshness;
  onStatusChange?: (status: ReplayStatus | undefined) => void;
  onSessionChange?: (session: ReplaySessionState | undefined) => void;
  controller?: ReplayController;
}

export function ReplayControls(props: ReplayControlsProps) {
  return props.controller ? <ReplayControlsView {...props} controller={props.controller} />
    : <StandaloneReplayControls {...props} />;
}

function StandaloneReplayControls(props: ReplayControlsProps) {
  const controller = useReplayController(props.machineId, props.client);
  return <ReplayControlsView {...props} controller={controller} />;
}

function ReplayControlsView({
  client, snapshot, freshness, onStatusChange, onSessionChange, controller,
}: ReplayControlsProps & { controller: ReplayController }) {
  const { session, isLoading, error, hasLoadFailure, run, start, reload, isCommandPending } = controller;
  const [seekMillis, setSeekMillis] = useState(0);
  useEffect(() => {
    onStatusChange?.(session?.status);
    onSessionChange?.(controller.authoritativeSession);
  }, [session?.status, controller.authoritativeSession, onStatusChange, onSessionChange]);
  useEffect(() => {
    if (session) setSeekMillis(Date.parse(snapshot?.replayCursor.sourceObservedAt ?? session.sourceRange.startsAt));
  }, [session?.replaySessionId, snapshot?.replayCursor.sourceObservedAt]);
  return (
    <section className="replay-controls" aria-label="Replay 시간 제어">
      <div className="replay-heading">
        <strong className="replay-badge">REPLAY</strong>
        <span aria-live="polite">{session ? statusLabel(session.status) : "시작 전"}</span>
      </div>
      {!session ? (
        <button
          type="button"
          disabled={isLoading}
          onClick={() =>
            hasLoadFailure ? void reload() : void start()
          }
        >
          {isLoading ? "Replay 확인 중" : hasLoadFailure ? "Replay 상태 다시 확인" : "Replay 시작"}
        </button>
      ) : (
        <>
          <div className="replay-actions">
            {session.status === "RUNNING" ? (
              <button
                type="button"
                disabled={isCommandPending}
                onClick={() => void run("PAUSED", (current) => client.pause(current.replaySessionId, current.revision))}
              >
                일시정지
              </button>
            ) : (
              <button
                type="button"
                disabled={isCommandPending || !(["PAUSED"] as ReplayStatus[]).includes(session.status)}
                onClick={() => void run("RUNNING", (current) => client.resume(current.replaySessionId, current.revision))}
              >
                재생
              </button>
            )}
            <div role="group" aria-label="Replay 배속">
              {([1, 10, 100] as const).map((speed) => (
                <button
                  type="button"
                  key={speed}
                  aria-pressed={session.speedMultiplier === speed}
                  disabled={isCommandPending || !(["RUNNING", "PAUSED"] as ReplayStatus[]).includes(session.status)}
                  onClick={() => void run(session.status, (current) => client.changeSpeed(current.replaySessionId, current.revision, speed))}
                >
                  {speed}x
                </button>
              ))}
            </div>
          </div>
          <label className="replay-timeline">
            <span>Replay timeline</span>
            <input
              type="range"
              min={Date.parse(session.sourceRange.startsAt)}
              max={Date.parse(session.sourceRange.endsAt)}
              step={1000}
              value={seekMillis}
              disabled={isCommandPending || session.status === "SEEKING"}
              onChange={(event) => setSeekMillis(Number(event.currentTarget.value))}
            />
          </label>
          <button
            type="button"
            disabled={isCommandPending || session.status === "SEEKING"}
            onClick={() => void run("SEEKING", (current) => client.seek(
              current.replaySessionId,
              current.revision,
              new Date(seekMillis).toISOString(),
              current.speedMultiplier,
            ))}
          >
            선택 시점으로 이동
          </button>
          <dl className="replay-times">
            <div><dt>Source Time</dt><dd>{formatTime(snapshot?.replayCursor.sourceObservedAt)}</dd></div>
            <div><dt>Replay Time</dt><dd>{formatTime(snapshot?.replayCursor.replayPublishedAt)}</dd></div>
            <div><dt>Twin Freshness</dt><dd>{freshness ?? "확인 중"}</dd></div>
          </dl>
        </>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  );
}

function statusLabel(status: ReplayStatus): string {
  return {
    PREPARING: "준비 중",
    RUNNING: "재생 중",
    PAUSED: "일시정지됨",
    SEEKING: "선택 시점으로 이동 중",
    COMPLETED: "재생 완료",
    FAILED: "재생 실패",
  }[status];
}

function formatTime(value: string | undefined): string {
  return value ? new Date(value).toLocaleString("ko-KR", { timeZone: "UTC" }) : "아직 없음";
}

import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";

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
  const rangeRef = useRef<HTMLInputElement>(null);
  const commitSeekRef = useRef<(millis: number) => void>(() => undefined);

  useEffect(() => {
    onStatusChange?.(session?.status);
    onSessionChange?.(controller.authoritativeSession);
  }, [session?.status, controller.authoritativeSession, onStatusChange, onSessionChange]);
  useEffect(() => {
    if (session) setSeekMillis(Date.parse(snapshot?.replayCursor.sourceObservedAt ?? session.sourceRange.startsAt));
  }, [session?.replaySessionId, snapshot?.replayCursor.sourceObservedAt]);

  commitSeekRef.current = (millis: number) => {
    if (!session) return;
    const sourceObservedAt = new Date(millis).toISOString();
    setSeekMillis(millis);
    void run("SEEKING", (current) => client.seek(current.replaySessionId, current.revision, sourceObservedAt, current.speedMultiplier));
  };
  const seekTo = (isoTime: string) => commitSeekRef.current(Date.parse(isoTime));

  useEffect(() => {
    const element = rangeRef.current;
    if (!element) return;
    const handleChange = (event: Event) => commitSeekRef.current(Number((event.target as HTMLInputElement).value));
    element.addEventListener("change", handleChange);
    return () => element.removeEventListener("change", handleChange);
  }, [!!session]);

  const startMs = session ? Date.parse(session.sourceRange.startsAt) : 0;
  const endMs = session ? Date.parse(session.sourceRange.endsAt) : 0;
  const totalMs = Math.max(0, endMs - startMs);
  const clampedMillis = Math.min(Math.max(seekMillis, startMs), endMs);
  const progressPercent = totalMs > 0 ? ((clampedMillis - startMs) / totalMs) * 100 : 0;
  const timelineStyle = { "--replay-progress": `${progressPercent}%` } as CSSProperties;

  return (
    <section className="replay-controls" aria-label="Replay 시간 제어">
      <div className="replay-heading">
        <strong className="replay-badge">REPLAY</strong>
        <span className="replay-status-pill" data-status={session?.status ?? "NONE"} aria-live="polite">
          <span className="replay-status-dot" aria-hidden="true" />
          {session ? statusLabel(session.status) : "시작 전"}
        </span>
        {session && (
          <span className="replay-timecode">
            {formatElapsed(clampedMillis - startMs)}
            <small>/ {formatElapsed(totalMs)} 경과</small>
          </span>
        )}
      </div>
      {!session ? (
        <button
          type="button"
          className="button-quiet"
          disabled={isLoading}
          onClick={() =>
            hasLoadFailure ? void reload() : void start()
          }
        >
          {isLoading ? "Replay 확인 중" : hasLoadFailure ? "Replay 상태 다시 확인" : "Replay 시작"}
        </button>
      ) : (
        <>
          <div className="replay-transport">
            <div className="replay-transport-cluster">
              <button
                type="button"
                className="replay-icon-btn"
                aria-label="처음으로 이동"
                disabled={isCommandPending || session.status === "SEEKING"}
                onClick={() => seekTo(session.sourceRange.startsAt)}
              >
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M7 6v12M17 6l-9 6 9 6V6z" fill="currentColor" />
                </svg>
              </button>
              {session.status === "RUNNING" ? (
                <button
                  type="button"
                  className="replay-icon-btn replay-play-btn"
                  aria-label="일시정지"
                  disabled={isCommandPending}
                  onClick={() => void run("PAUSED", (current) => client.pause(current.replaySessionId, current.revision))}
                >
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M7 5h4v14H7zM13 5h4v14h-4z" fill="currentColor" />
                  </svg>
                </button>
              ) : (
                <button
                  type="button"
                  className="replay-icon-btn replay-play-btn"
                  aria-label="재생"
                  disabled={isCommandPending || !(["PAUSED"] as ReplayStatus[]).includes(session.status)}
                  onClick={() => void run("RUNNING", (current) => client.resume(current.replaySessionId, current.revision))}
                >
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M8 5v14l11-7L8 5z" fill="currentColor" />
                  </svg>
                </button>
              )}
              <button
                type="button"
                className="replay-icon-btn"
                aria-label="끝으로 이동"
                disabled={isCommandPending || session.status === "SEEKING"}
                onClick={() => seekTo(session.sourceRange.endsAt)}
              >
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M17 6v12M7 6l9 6-9 6V6z" fill="currentColor" />
                </svg>
              </button>
            </div>
            <div className="replay-speed-toggle" role="group" aria-label="Replay 배속">
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
          <div className="replay-timeline">
            <span>Replay timeline</span>
            <input
              ref={rangeRef}
              type="range"
              aria-label="Replay timeline"
              min={startMs}
              max={endMs}
              step={1000}
              value={seekMillis}
              disabled={isCommandPending || session.status === "SEEKING"}
              onChange={(event) => setSeekMillis(Number(event.currentTarget.value))}
              style={timelineStyle}
            />
            <div className="replay-timeline-labels">
              <span>{formatTime(session.sourceRange.startsAt)}</span>
              <span>{formatTime(session.sourceRange.endsAt)}</span>
            </div>
          </div>
          <dl className="replay-times">
            <div><dt>Source Time</dt><dd>{formatTime(snapshot?.replayCursor.sourceObservedAt)}</dd></div>
            <div><dt>Replay Time</dt><dd>{formatTime(snapshot?.replayCursor.replayPublishedAt)}</dd></div>
            <div>
              <dt>Twin Freshness</dt>
              <dd><span className="freshness-chip" data-freshness={freshness ?? "UNKNOWN"}>{freshness ?? "확인 중"}</span></dd>
            </div>
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
    SEEKING: "이동 중",
    COMPLETED: "재생 완료",
    FAILED: "재생 실패",
  }[status];
}

function formatTime(value: string | undefined): string {
  return value ? new Date(value).toLocaleString("ko-KR", { timeZone: "UTC" }) : "아직 없음";
}

function formatElapsed(millis: number): string {
  const totalSeconds = Math.max(0, Math.floor(millis / 1000));
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (value: number) => String(value).padStart(2, "0");
  return days > 0 ? `${days}일 ${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

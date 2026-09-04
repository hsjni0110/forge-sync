import { useEffect, useState } from "react";

import type { ReplayControlClient } from "../application/ports";
import type { ReplaySessionState, ReplayStatus } from "../domain/replay";
import type { Freshness, TwinSnapshot } from "../../twin/domain/twin";

const SOURCE_SET_ID = "nist-mazak01-20161005";

export function ReplayControls({
  machineId,
  client,
  snapshot,
  freshness,
  onStatusChange,
}: {
  machineId: string;
  client: ReplayControlClient;
  snapshot?: TwinSnapshot;
  freshness?: Freshness;
  onStatusChange?: (status: ReplayStatus | undefined) => void;
}) {
  const [session, setSession] = useState<ReplaySessionState>();
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [seekMillis, setSeekMillis] = useState(0);

  useEffect(() => {
    let active = true;
    setIsLoading(true);
    client
      .load(machineId)
      .then((loaded) => {
        if (active) setSession(loaded);
      })
      .catch(() => {
        if (active) setSession(undefined);
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [client, machineId]);

  useEffect(() => {
    onStatusChange?.(session?.status);
  }, [onStatusChange, session?.status]);

  useEffect(() => {
    if (!session) return;
    const cursorMillis = Date.parse(
      snapshot?.replayCursor.sourceObservedAt ?? session.sourceRange.startsAt,
    );
    setSeekMillis(cursorMillis);
  }, [session?.replaySessionId, snapshot?.replayCursor.sourceObservedAt]);

  useEffect(() => {
    if (session?.status !== "SEEKING") return;
    const timer = window.setTimeout(() => {
      void client.load(machineId).then(setSession).catch(() => setError("재생 상태를 확인할 수 없습니다."));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [client, machineId, session]);

  const run = async (
    optimisticStatus: ReplayStatus,
    action: (current: ReplaySessionState) => Promise<ReplaySessionState>,
  ) => {
    if (!session) return;
    const previous = session;
    setSession({ ...previous, status: optimisticStatus });
    setError(undefined);
    try {
      setSession(await action(previous));
    } catch {
      try {
        setSession(await client.load(machineId));
      } catch {
        setSession(previous);
      }
      setError("서버가 명령을 받지 않았습니다. 권위 상태로 되돌렸습니다.");
    }
  };

  const start = async () => {
    setIsLoading(true);
    setError(undefined);
    try {
      setSession(await client.start(machineId, SOURCE_SET_ID, 10));
    } catch {
      setError("Replay를 시작할 수 없습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <section className="replay-controls" aria-label="Replay 시간 제어">
      <div className="replay-heading">
        <strong className="replay-badge">REPLAY</strong>
        <span aria-live="polite">{session ? statusLabel(session.status) : "시작 전"}</span>
      </div>
      {!session ? (
        <button type="button" disabled={isLoading} onClick={() => void start()}>
          {isLoading ? "Replay 확인 중" : "Replay 시작"}
        </button>
      ) : (
        <>
          <div className="replay-actions">
            {session.status === "RUNNING" ? (
              <button
                type="button"
                onClick={() => void run("PAUSED", (current) => client.pause(current.replaySessionId, current.revision))}
              >
                일시정지
              </button>
            ) : (
              <button
                type="button"
                disabled={!(["PAUSED"] as ReplayStatus[]).includes(session.status)}
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
                  disabled={!(["RUNNING", "PAUSED"] as ReplayStatus[]).includes(session.status)}
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
              disabled={session.status === "SEEKING"}
              onChange={(event) => setSeekMillis(Number(event.currentTarget.value))}
            />
          </label>
          <button
            type="button"
            disabled={session.status === "SEEKING"}
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

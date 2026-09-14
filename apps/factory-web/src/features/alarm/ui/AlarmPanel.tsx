import { useState } from "react";

import type { Alarm } from "../domain/alarm";

export function AlarmPanel({
  alarms,
  onAcknowledge,
}: {
  alarms: Alarm[];
  onAcknowledge: (alarm: Alarm, operatorName: string) => Promise<void>;
}) {
  const [operatorName, setOperatorName] = useState("");
  const [pendingAlarmId, setPendingAlarmId] = useState<string>();
  const [failure, setFailure] = useState(false);
  const ordered = [...alarms].sort((left, right) => {
    if (left.status === "RESOLVED" && right.status !== "RESOLVED") return 1;
    if (left.status !== "RESOLVED" && right.status === "RESOLVED") return -1;
    return right.openedReplaySequence - left.openedReplaySequence;
  });

  return (
    <section className="alarm-panel" aria-labelledby="alarm-panel-title">
      <div className="alarm-panel-heading">
        <div>
          <p className="eyebrow">CONDITION → ALARM</p>
          <h2 id="alarm-panel-title">알람</h2>
        </div>
        <span>{alarms.filter(({ status }) => status !== "RESOLVED").length}건 활성</span>
      </div>
      {ordered.length === 0 ? (
        <p className="alarm-empty">이 재생 시점까지 생성된 알람이 없습니다.</p>
      ) : (
        <ul className="alarm-list">
          {ordered.map((alarm) => (
            <li key={alarm.alarmId} data-alarm-id={alarm.alarmId} data-severity={alarm.severity}>
              <div className="alarm-title">
                <strong>
                  <span aria-hidden="true">{alarm.severity === "CRITICAL" ? "!" : "▲"}</span>{" "}
                  {alarm.severity === "CRITICAL" ? "긴급 알람" : "주의 알람"}
                </strong>
                <span>{statusLabel(alarm.status)}</span>
              </div>
              <p>{alarm.message ?? `${alarm.conditionType} 상태`}</p>
              <dl>
                <div><dt>원천</dt><dd>Condition에서 생성</dd></div>
                <div><dt>코드</dt><dd>{alarm.nativeCode}</dd></div>
                <div><dt>Alarm ID</dt><dd>{alarm.alarmId}</dd></div>
              </dl>
              {alarm.acknowledgedBy && <p>확인 작업자 · {alarm.acknowledgedBy}</p>}
              {alarm.status === "OPEN" && (
                <div className="alarm-acknowledge">
                  <label>
                    작업자 이름
                    <input value={operatorName} onChange={(event) => setOperatorName(event.target.value)} />
                  </label>
                  <button type="button" disabled={!operatorName.trim() || pendingAlarmId !== undefined}
                    onClick={() => {
                      setPendingAlarmId(alarm.alarmId);
                      setFailure(false);
                      void onAcknowledge(alarm, operatorName.trim())
                        .catch(() => setFailure(true))
                        .finally(() => setPendingAlarmId(undefined));
                    }}>
                    알람 확인
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {failure && <p role="alert">알람 확인을 저장하지 못했습니다. 새로고침 후 다시 시도해 주세요.</p>}
    </section>
  );
}

function statusLabel(status: Alarm["status"]): string {
  if (status === "ACKNOWLEDGED") return "확인됨";
  if (status === "RESOLVED") return "해제됨";
  return "열림";
}

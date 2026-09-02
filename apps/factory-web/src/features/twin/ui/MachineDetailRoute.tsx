import { useCallback } from "react";
import { useParams } from "react-router-dom";

import type { TwinSessionFactory } from "../application/ports";
import { MachineDetailView } from "./MachineDetailView";
import { useTwinLiveSession } from "./useTwinLiveSession";

const MACHINE_ID = /^[A-Za-z0-9._-]{1,64}$/;

export function MachineDetailRoute({ sessionFactory }: { sessionFactory: TwinSessionFactory }) {
  const machineId = useParams().machineId ?? "";
  if (!MACHINE_ID.test(machineId)) {
    return (
      <section className="page-message" role="alert">
        <h1>설비를 찾을 수 없습니다</h1>
        <p>주소에 입력된 설비 ID 형식이 올바르지 않습니다.</p>
      </section>
    );
  }
  return <ConnectedMachineDetail machineId={machineId} sessionFactory={sessionFactory} />;
}

function ConnectedMachineDetail({
  machineId,
  sessionFactory,
}: {
  machineId: string;
  sessionFactory: TwinSessionFactory;
}) {
  const createSession = useCallback(() => sessionFactory(machineId), [machineId, sessionFactory]);
  const { state, retryNow } = useTwinLiveSession(createSession);
  return <MachineDetailView machineId={machineId} state={state} retryNow={retryNow} />;
}

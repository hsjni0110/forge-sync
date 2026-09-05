import { useCallback } from "react";
import { useParams } from "react-router-dom";

import type { TwinSessionFactory } from "../application/ports";
import type { ReplayControlClient } from "../../replay/application/ports";
import { useReplayController } from "../../replay/ui/useReplayController";
import { ProcessAnalysisPanel } from "../../process-analytics/ui/ProcessAnalysisPanel";
import { ReplayControls } from "../../replay/ui/ReplayControls";
import { useReplayDrivenTwinBootstrap } from "../../replay/ui/useReplayDrivenTwinBootstrap";
import { MachineDetailView } from "./MachineDetailView";
import { useTwinLiveSession } from "./useTwinLiveSession";

const MACHINE_ID = /^[A-Za-z0-9._-]{1,64}$/;

export function MachineDetailRoute({
  sessionFactory,
  replayControlClient,
}: {
  sessionFactory: TwinSessionFactory;
  replayControlClient?: ReplayControlClient;
}) {
  const machineId = useParams().machineId ?? "";
  return <MachineDetailPanel machineId={machineId} sessionFactory={sessionFactory} replayControlClient={replayControlClient} />;
}

export function MachineDetailPanel({
  machineId,
  sessionFactory,
  replayControlClient,
  layout = "FULL",
}: {
  machineId: string;
  sessionFactory: TwinSessionFactory;
  replayControlClient?: ReplayControlClient;
  layout?: "FULL" | "COMPACT";
}) {
  if (!MACHINE_ID.test(machineId)) {
    return (
      <section className="page-message" role="alert">
        <h1>설비를 찾을 수 없습니다</h1>
        <p>주소에 입력된 설비 ID 형식이 올바르지 않습니다.</p>
      </section>
    );
  }
  return (
    <ConnectedMachineDetail
      machineId={machineId}
      sessionFactory={sessionFactory}
      replayControlClient={replayControlClient}
      layout={layout}
    />
  );
}

function ConnectedMachineDetail({
  machineId,
  sessionFactory,
  replayControlClient,
  layout,
}: {
  machineId: string;
  sessionFactory: TwinSessionFactory;
  replayControlClient?: ReplayControlClient;
  layout: "FULL" | "COMPACT";
}) {
  const replay = useReplayController(machineId, replayControlClient);
  const createSession = useCallback(() => sessionFactory(machineId), [machineId, sessionFactory]);
  const { state, retryNow } = useTwinLiveSession(createSession);
  useReplayDrivenTwinBootstrap(replay.session?.status, state, retryNow);
  return (
    <>
      {replayControlClient && (
        <ReplayControls
          machineId={machineId}
          client={replayControlClient}
          snapshot={state.snapshot}
          freshness={state.freshness}
          controller={replay}
        />
      )}
      <MachineDetailView
        machineId={machineId}
        state={state}
        retryNow={retryNow}
        layout={layout}
      />
      {replayControlClient && <ProcessAnalysisPanel machineId={machineId} session={replay.authoritativeSession} twinState={state}
        retryTwin={retryNow} reloadReplay={replay.reload} seek={(at) => void replay.run("SEEKING", (current) =>
          replayControlClient.seek(current.replaySessionId, current.revision, at, current.speedMultiplier))} />}
    </>
  );
}

import { useEffect, useState } from "react";

import type { ObservedToolpathClient, ObservedToolpathRequest } from "../application/ports";
import type { ObservedToolpathDocument } from "../domain/observedToolpath";

export function useObservedToolpath(
  client: ObservedToolpathClient | undefined,
  request: ObservedToolpathRequest | undefined,
) {
  const [document, setDocument] = useState<ObservedToolpathDocument>();
  const [message, setMessage] = useState("가공을 선택하면 관측 경로를 표시합니다.");
  useEffect(() => {
    setDocument(undefined);
    if (!client || !request) {
      setMessage("가공을 선택하면 관측 경로를 표시합니다.");
      return;
    }
    const controller = new AbortController();
    setMessage("선택한 가공의 관측 경로를 불러오는 중입니다.");
    void client.find(request, controller.signal).then((next) => {
      if (next.availability === "AVAILABLE") {
        setDocument(next);
        setMessage("");
      } else {
        setMessage(next.reason ?? "XYZ 관측값이 부족해 경로를 표시할 수 없습니다.");
      }
    }).catch((error: unknown) => {
      if (!(error instanceof DOMException && error.name === "AbortError")) {
        setMessage("관측 경로를 불러오지 못했습니다. 2D 설비 값은 계속 사용할 수 있습니다.");
      }
    });
    return () => controller.abort();
  }, [client, request?.machineId, request?.replaySessionId, request?.startSequence,
    request?.endSequence, request?.throughReplaySequence, request?.resetKey]);
  return { document, message };
}

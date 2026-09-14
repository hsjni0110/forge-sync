import { useEffect, useState } from "react";
import type { RunAnalysis } from "../../process-analytics/domain/processAnalysis";
import { HttpObservedProductionContextClient } from "../adapters/httpObservedProductionContextClient";
import type { ObservedProductionContext } from "../domain/observedProductionContext";

const browserClient = new HttpObservedProductionContextClient(import.meta.env.VITE_API_BASE_URL ?? "");

export function ObservedProductionContextPanel({ machineId, analysis, compact = false,
  client = browserClient,
}: {
  machineId: string;
  analysis: RunAnalysis;
  compact?: boolean;
  client?: Pick<HttpObservedProductionContextClient, "find">;
}) {
  const [report, setReport] = useState<ObservedProductionContext>();
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setReport(undefined);
    setFailed(false);
    void client.find(machineId, analysis.processingId, controller.signal)
      .then((value) => { if (!controller.signal.aborted) setReport(value); })
      .catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => controller.abort();
  }, [analysis.processingId, client, machineId]);
  if (!report) return <section className="detail-section" aria-label="생산 문맥">
    <h2>PRODUCTION CONTEXT · 생산 문맥</h2>
    <p role="status">{failed ? "생산 문맥을 불러올 수 없습니다." : "관측 생산 문맥 계산 중"}</p>
  </section>;
  return <ObservedProductionContextView report={report} compact={compact} />;
}

export function ObservedProductionContextView({ report, compact = false }: {
  report: ObservedProductionContext;
  compact?: boolean;
}) {
  const main = report.programSummaries[0];
  const subprogramObserved = report.programIntervals.some(
    (interval) => interval.kind === "SUBPROGRAM" && interval.availability === "AVAILABLE",
  );
  return <section className="detail-section production-context" aria-label="생산 문맥">
    <h2>PRODUCTION CONTEXT · 생산 문맥</h2>
    <p className="run-detail-badges"><span className="provenance-tag">OBSERVED + DERIVED</span></p>
    {main ? <dl className="definition-list">
      <div><dt>프로그램</dt><dd>{main.programName}</dd></div>
      <div><dt>가공 구간</dt><dd>{main.runCount}건 · 완료 근거 {main.completedRunCount}건</dd></div>
      {!compact && <div><dt>가공시간</dt><dd>합계 {seconds(main.totalDurationSeconds)} · 평균 {seconds(main.meanDurationSeconds)} · 중앙값 {seconds(main.medianDurationSeconds)}</dd></div>}
      <div><dt>하위 프로그램</dt><dd>{subprogramObserved ? "관측됨" : "이름 확인 불가"}</dd></div>
      <div><dt>부품 카운트 증가</dt><dd>{report.partCount.netIncrease === undefined ? "계산 근거 부족" : `${report.partCount.netIncrease}개`}</dd></div>
    </dl> : <p>프로그램이 확인된 가공 구간이 없습니다.</p>}
    {report.unassignedRunCount > 0 && <p>프로그램 미확인 가공 {report.unassignedRunCount}건은 다른 프로그램에 넣지 않았습니다.</p>}
    <p className="section-note">부품 카운트와 가공 구간은 시간상 겹침만 표시하며 생산 완료나 양품·불량 결과로 확정하지 않습니다.</p>
    <p className="section-note">ProductionResult · {report.productionResultStatus === "NOT_OBSERVED" ? "원천에서 관측되지 않음" : report.productionResultStatus}</p>
  </section>;
}

function seconds(value: number | undefined) {
  return value === undefined ? "계산 불가" : `${Number(value.toFixed(1))}초`;
}

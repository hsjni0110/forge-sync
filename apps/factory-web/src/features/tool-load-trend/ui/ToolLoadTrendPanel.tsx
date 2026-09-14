import { useEffect, useState } from "react";
import type { RunAnalysis } from "../../process-analytics/domain/processAnalysis";
import { HttpToolLoadTrendClient } from "../adapters/httpToolLoadTrendClient";
import type { ToolLoadTrendGroup, ToolLoadTrendReport } from "../domain/toolLoadTrend";

const browserClient = new HttpToolLoadTrendClient(import.meta.env.VITE_API_BASE_URL ?? "");

export function ToolLoadTrendPanel({ machineId, analysis, compact = false,
  client = browserClient,
}: {
  machineId: string;
  analysis: RunAnalysis;
  compact?: boolean;
  client?: Pick<HttpToolLoadTrendClient, "find">;
}) {
  const [report, setReport] = useState<ToolLoadTrendReport>();
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
  if (!report) return <section className="detail-section" aria-label="공구별 부하 추세">
    <h2>TOOL LOAD TREND · 공구별 부하 추세</h2>
    <p role="status">{failed ? "공구별 부하 추세를 불러올 수 없습니다." : "부하 추세 계산 중"}</p>
  </section>;
  return <ToolLoadTrendView report={report} compact={compact} />;
}

export function ToolLoadTrendView({ report, compact = false }: {
  report: ToolLoadTrendReport;
  compact?: boolean;
}) {
  const groups = compact ? report.groups.slice(0, 3) : report.groups;
  return <section className="detail-section tool-load-trend" aria-label="공구별 부하 추세">
    <h2>TOOL LOAD TREND · 공구별 부하 추세</h2>
    <p className="run-detail-badges">
      <span className="provenance-tag">DERIVED · {report.provenance.sourceKind}:{report.provenance.provider}</span>
    </p>
    {groups.length === 0 ? <p>프로그램·공구·부하 채널을 함께 확인할 수 있는 구간이 없습니다.</p>
      : <div className="tool-load-groups">{groups.map((group) =>
        <ToolLoadGroup key={`${group.programName}:${group.toolNumber}:${group.sourceDataItemId}`} group={group} />)}
      </div>}
    {compact && report.groups.length > groups.length &&
      <p className="section-note">상위 {groups.length}개 채널 표시 · 전체 {report.groups.length}개</p>}
    <p className="section-note">이 값은 같은 프로그램·공구·채널의 관측 부하 변화 대리지표이며, 마모량이나 잔여 수명이 아닙니다.</p>
    <p className="section-note">이 추세만으로 Machine FAULT나 Alarm을 만들지 않습니다.</p>
    {!compact && <details className="provenance-disclosure"><summary>계산 정책과 원천</summary>
      <dl className="definition-list">
        <div><dt>구간 점</dt><dd>{report.policy.pointFormula}</dd></div>
        <div><dt>coverage</dt><dd>{report.policy.coverageFormula}</dd></div>
        <div><dt>기준선</dt><dd>초기 {report.policy.baselinePointCount}개 유효 구간의 중앙값</dd></div>
        <div><dt>편차</dt><dd>{report.policy.deviationFormula}</dd></div>
        <div><dt>추세선</dt><dd>{report.policy.slopeFormula}</dd></div>
        <div><dt>정책 버전</dt><dd>{report.policyVersion}</dd></div>
      </dl>
    </details>}
  </section>;
}

function ToolLoadGroup({ group }: { group: ToolLoadTrendGroup }) {
  const available = group.status === "AVAILABLE" && group.baselineMedianLoad !== undefined
    && group.latestDeviationPercent !== undefined;
  return <article className="tool-load-group" data-status={group.status}>
    <header><strong>PGM {group.programName} · 공구 {group.toolNumber}</strong>
      <span>{available ? "추세 계산됨" : "근거 부족"}</span></header>
    <p>{group.componentId} · {group.sourceDataItemId}</p>
    {available
      ? <p>기준선 {formatNumber(group.baselineMedianLoad!)}% · 최근 편차 {formatSigned(group.latestDeviationPercent!)}%</p>
      : <p>추세 계산 안 함 · coverage {Math.round(group.coverageRatio * 100)}%</p>}
    <p className="section-note">유효 구간 {group.eligiblePointCount}/{group.candidatePointCount} · 구간당 원본 최소 3개</p>
  </article>;
}

function formatNumber(value: number) {
  return Number(value.toFixed(1));
}

function formatSigned(value: number) {
  const rounded = Number(value.toFixed(1));
  return rounded > 0 ? `+${rounded}` : String(rounded);
}

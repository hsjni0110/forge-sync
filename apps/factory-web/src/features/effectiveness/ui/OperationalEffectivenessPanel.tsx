import type { OperationalEffectivenessReport } from "../domain/operationalEffectiveness";

export function OperationalEffectivenessPanel({
  report,
}: {
  report: OperationalEffectivenessReport;
}) {
  return (
    <section className="effectiveness-panel" aria-labelledby="effectiveness-title">
      <div className="effectiveness-heading">
        <div>
          <p className="eyebrow">근거가 있는 구성요소만 표시</p>
          <h2 id="effectiveness-title">운영 효과</h2>
        </div>
        <p>종합 OEE 제공 불가</p>
      </div>
      <div className="effectiveness-grid">
        <ComponentCard title="가동률" value={percent(report.availability.percent)}
          badge="파생" detail="관측 구간에서 계산됨" />
        <ComponentCard title="성능" value={performanceValue(report)} badge={
          report.performance.provenance === "ASSUMED" ? "가정" : "파생"
        } detail={performanceDetail(report)} />
        <ComponentCard title="생산량" value={throughputValue(report)} badge="관측"
          detail={throughputDetail(report)} />
        <ComponentCard title="품질" value="제공 불가" badge="원천 없음"
          detail="품질 원천 데이터 없음" />
      </div>
      <p className="effectiveness-policy">
        품질(양품·불량) 데이터가 없어 Availability × Performance × Quality 형태의 종합 OEE 숫자는 계산하지 않습니다.
      </p>
    </section>
  );
}

function ComponentCard({ title, value, badge, detail }: {
  title: string; value: string; badge: string; detail: string;
}) {
  return <article className="effectiveness-card">
    <div><h3>{title}</h3><span>{badge}</span></div>
    <strong>{value}</strong>
    <p>{detail}</p>
  </article>;
}

function percent(value: number | undefined): string {
  return value === undefined ? "제공 불가" : `${Number(value.toFixed(1))}%`;
}

function performanceValue(report: OperationalEffectivenessReport): string {
  return percent(report.performance.percent);
}

function performanceDetail(report: OperationalEffectivenessReport): string {
  if (report.performance.reason === "MINIMUM_SAMPLE_COUNT_NOT_MET") {
    return `표본 부족 (${report.performance.sampleCount}/5)`;
  }
  if (report.performance.provenance === "ASSUMED") return "외부 가정 기준 사이클과 비교";
  if (report.performance.referenceSeconds !== undefined) {
    return `이전 동일 프로그램 중앙값 ${Number(report.performance.referenceSeconds.toFixed(1))}초 기준`;
  }
  return "완료 사이클 근거 부족";
}

function throughputValue(report: OperationalEffectivenessReport): string {
  return report.throughput.partCount === undefined ? "제공 불가" : `${report.throughput.partCount}개`;
}

function throughputDetail(report: OperationalEffectivenessReport): string {
  return report.throughput.status === "UNAVAILABLE"
    ? "연속된 생산량 관측 근거 부족"
    : `연속 증가 ${report.throughput.usedTransitionCount}구간 · 리셋 ${report.throughput.resetCount}회`;
}

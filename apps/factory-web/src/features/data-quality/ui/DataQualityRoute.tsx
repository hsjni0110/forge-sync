import { useCallback, useEffect, useState, type ReactNode } from "react";

import type { TwinSessionFactory } from "../../twin/application/ports";
import { useTwinLiveSession } from "../../twin/ui/useTwinLiveSession";
import type { DataQualityClient } from "../application/ports";
import type { DataQualityReport } from "../domain/dataQuality";

const MACHINE_ID = "Mazak01";

export function DataQualityRoute({
  client,
  twinSessionFactory,
}: {
  client?: DataQualityClient;
  twinSessionFactory: TwinSessionFactory;
}) {
  const createSession = useCallback(() => twinSessionFactory(MACHINE_ID), [twinSessionFactory]);
  const { state } = useTwinLiveSession(createSession);
  const [sourceReport, setSourceReport] = useState<DataQualityReport>();
  const [scopedReport, setScopedReport] = useState<DataQualityReport>();
  const [sourceFailure, setSourceFailure] = useState<string>();
  const cursor = state.snapshot?.replayCursor;

  useEffect(() => {
    if (!client) return;
    const abort = new AbortController();
    setSourceFailure(undefined);
    void client
      .load(MACHINE_ID, undefined, abort.signal)
      .then((next) => {
        if (!abort.signal.aborted) setSourceReport(next);
      })
      .catch((error: unknown) => {
        if (!abort.signal.aborted) {
          setSourceFailure(error instanceof Error ? error.message : "알 수 없는 브라우저 오류");
        }
      });
    return () => abort.abort();
  }, [client]);

  useEffect(() => {
    if (!client || !cursor) return;
    const abort = new AbortController();
    const timer = window.setTimeout(() => {
      void client
        .load(
          MACHINE_ID,
          {
            replaySessionId: cursor.replaySessionId,
            throughReplaySequence: cursor.replaySequence,
          },
          abort.signal,
        )
        .then((next) => {
          if (!abort.signal.aborted) setScopedReport(next);
        })
        .catch(() => undefined);
    }, 500);
    return () => {
      window.clearTimeout(timer);
      abort.abort();
    };
  }, [client, cursor?.replaySequence, cursor?.replaySessionId]);

  const scopedReportIsCurrent =
    cursor !== undefined &&
    scopedReport?.replaySessionId === cursor.replaySessionId &&
    scopedReport.throughReplaySequence === cursor.replaySequence;
  const report = scopedReportIsCurrent ? scopedReport : sourceReport;

  if (!client) return <PageMessage text="데이터 품질 API가 연결되지 않았습니다." />;
  if (sourceFailure && !report) {
    return <PageMessage text="데이터 품질을 불러올 수 없습니다." detail={sourceFailure} />;
  }
  if (!report) return <PageMessage text="데이터 품질을 불러오는 중입니다." busy />;
  return <DataQualityView report={report} refreshing={Boolean(cursor && !scopedReportIsCurrent)} />;
}

export function DataQualityView({
  report,
  refreshing = false,
}: {
  report: DataQualityReport;
  refreshing?: boolean;
}) {
  const dimensions = report.dimensions;
  return (
    <article className="quality-page">
      <header className="quality-header">
        <p className="eyebrow">DATA EVIDENCE</p>
        <h1>데이터 품질</h1>
        <p>{report.machineId}의 원천·수신·파생 품질을 서로 섞지 않고 보여줍니다.</p>
        <strong className="quality-no-score">종합 품질 점수 없음</strong>
        {refreshing && <p role="status">현재 Replay 범위의 수신 품질을 맞추는 중입니다.</p>}
      </header>

      <section className="quality-dimension-grid" aria-label="데이터 품질 차원">
        <DimensionCard title="유효성" description="schema와 값 규칙을 통과했는지 확인합니다.">
          <MeasurementRow label="원천·매핑" measurement={dimensions.validity.sourceProfile} />
          <MeasurementRow label="실시간 수신" measurement={dimensions.validity.runtime} />
        </DimensionCard>
        <DimensionCard title="완전성" description="기대해야 할 데이터 대비 빠진 양입니다.">
          <MeasurementRow label="레코드 연속성" measurement={dimensions.completeness} />
        </DimensionCard>
        <DimensionCard title="순서" description="선언된 Replay 순서보다 뒤늦게 도착했는지 확인합니다.">
          <MeasurementRow label="원천 파일" measurement={dimensions.ordering.sourceProfile} />
          <MeasurementRow label="현재 Replay" measurement={dimensions.ordering.runtime} />
        </DimensionCard>
        <DimensionCard title="중복" description="같은 수신 identity가 반복 전달됐는지 확인합니다.">
          <MeasurementRow label="원천 파일" measurement={dimensions.duplication.sourceProfile} />
          <MeasurementRow label="현재 Replay" measurement={dimensions.duplication.runtime} />
        </DimensionCard>
        <DimensionCard title="최신성" description="원천 시각이 아닌 마지막 반영 시각을 기준으로 합니다.">
          <p className="quality-reading">
            {dimensions.freshness.status === "MEASURED"
              ? freshnessLabel(dimensions.freshness.value)
              : "측정 불가"}
          </p>
          {dimensions.freshness.reason && <small>{dimensions.freshness.reason}</small>}
        </DimensionCard>
        <DimensionCard title="의미 변환률" description="해석 성공이 아니라 Canonical mapping 완료 비율입니다.">
          <MeasurementRow label={`mapping ${dimensions.semanticCoverage.mappingVersion}`}
            measurement={dimensions.semanticCoverage} />
        </DimensionCard>
      </section>

      <section className="quality-section">
        <header><h2>현재 Replay 수신</h2><p>세션과 cursor 범위로만 집계합니다.</p></header>
        {report.runtime.status === "MEASURED" ? (
          <dl className="quality-facts">
            <Fact label="받음" value={report.runtime.receivedCount} />
            <Fact label="수락" value={report.runtime.acceptedCount} />
            <Fact label="중복" value={report.runtime.duplicateCount} />
            <Fact label="순서 지연" value={report.runtime.outOfOrderCount} />
          </dl>
        ) : <p className="quality-unavailable">Replay 수신 범위가 없어 측정하지 않았습니다.</p>}
      </section>

      <section className="quality-section">
        <header><h2>가공 분석 입력 범위</h2><p>원천 품질과 파생 feature 품질을 구분합니다.</p></header>
        <div className="quality-derived-grid">
          <DerivedCard title="가공 구간 분리" status={report.derivedProcess.segmentation.status}
            values={[
              ["입력 관찰", report.derivedProcess.segmentation.inputObservationCount],
              ["생성 구간", report.derivedProcess.segmentation.resultCount],
            ]} reason={report.derivedProcess.segmentation.reason} />
          <DerivedCard title="Feature coverage" status={report.derivedProcess.featureCoverage.status}
            values={[
              ["대상 run", report.derivedProcess.featureCoverage.eligibleRunCount],
              ["사용 가능", report.derivedProcess.featureCoverage.availableCount],
              ["일부만 있음", report.derivedProcess.featureCoverage.partialCount],
              ["없음", report.derivedProcess.featureCoverage.missingCount],
              ["빈 구간", report.derivedProcess.featureCoverage.emptyWindowCount],
            ]} reason={report.derivedProcess.featureCoverage.reason} />
        </div>
      </section>

      <section className="quality-section">
        <header><h2>아직 매핑되지 않은 DataItem</h2>
          <p>{report.unmappedDataItems.length}종 · 원본 위치를 함께 보존합니다.</p></header>
        <div className="quality-table-wrap">
          <table className="quality-table">
            <thead><tr><th>구분</th><th>DataItem</th><th>레코드</th><th>첫 원본 위치</th></tr></thead>
            <tbody>{report.unmappedDataItems.map((item) => (
              <tr key={`${item.classification}:${item.dataItemId ?? item.name}`}>
                <td>{classificationLabel(item.classification)}</td>
                <td><strong>{item.name}</strong>{item.dataItemId && <small>{item.dataItemId}</small>}</td>
                <td>{item.recordCount.toLocaleString("ko-KR")}</td>
                <td><a href={item.evidenceHref} target="_blank" rel="noreferrer"
                  title={item.firstRawRecordId}>원본 줄 보기</a></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      </section>

      <details className="quality-evidence">
        <summary>계산 근거와 provenance</summary>
        <dl>
          <dt>원천 profile run</dt><dd>{report.evidence.sourceProfileProcessingRunId}</dd>
          <dt>의미 매핑 run</dt><dd>{report.evidence.semanticMappingProcessingRunId}</dd>
          <dt>원본 artifact</dt><dd>{report.evidence.rawArtifactId}</dd>
        </dl>
        <a href={report.evidence.sourceHref} target="_blank" rel="noreferrer">고정된 NIST 원본 보기</a>
      </details>
    </article>
  );
}

function DimensionCard({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return <section className="quality-dimension"><header><h2>{title}</h2><p>{description}</p></header>{children}</section>;
}

function MeasurementRow({ label, measurement }: { label: string; measurement: DataQualityReport["dimensions"]["completeness"] }) {
  return <div className="quality-measurement"><span>{label}</span>
    <strong>{measurement.status === "MEASURED" && measurement.ratio !== null
      ? `${(measurement.ratio * 100).toLocaleString("ko-KR", { maximumFractionDigits: 2 })}%`
      : "측정 불가"}</strong>
    {measurement.status === "MEASURED" ? <small>{measurement.numerator.toLocaleString("ko-KR")} / {measurement.denominator.toLocaleString("ko-KR")}</small>
      : <small>{measurement.reason}</small>}
  </div>;
}

function Fact({ label, value }: { label: string; value: number }) {
  return <div><dt>{label}</dt><dd>{value.toLocaleString("ko-KR")}</dd></div>;
}

function DerivedCard({ title, status, values, reason }: { title: string; status: "MEASURED" | "NOT_EVALUATED"; values: Array<[string, number]>; reason: string | null }) {
  return <section className="quality-derived"><h3>{title}</h3>{status === "MEASURED"
    ? <dl>{values.map(([label, value]) => <Fact key={label} label={label} value={value} />)}</dl>
    : <p className="quality-unavailable">측정 불가 · {reason}</p>}</section>;
}

function PageMessage({ text, detail, busy = false }: { text: string; detail?: string; busy?: boolean }) {
  return <section className="page-message" aria-busy={busy}><h1>데이터 품질</h1><p>{text}</p>
    {detail && <small>{detail}</small>}</section>;
}

function freshnessLabel(value: string | null) {
  return value === "FRESH" ? "최신" : value === "LAGGING" ? "반영 지연" : value === "STALE" ? "오래된 데이터" : "측정 불가";
}

function classificationLabel(value: string) {
  return value === "UNKNOWN" ? "카탈로그 미확인" : value === "AMBIGUOUS" ? "이름 모호" : "매핑 보류";
}

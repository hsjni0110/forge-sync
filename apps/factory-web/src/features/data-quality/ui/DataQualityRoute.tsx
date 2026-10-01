import { useCallback, useEffect, useState, type ReactNode } from "react";

import type { TwinSessionFactory } from "../../twin/application/ports";
import { useTwinLiveSession } from "../../twin/ui/useTwinLiveSession";
import type { DataQualityClient } from "../application/ports";
import type { DataQualityReport } from "../domain/dataQuality";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import {
  useOperationalContextPublisher,
  type OperationalContextValue,
} from "../../shell/ui/OperationalContext";
import { PROCESS_GLOSSARY } from "../../../shared/presentation/processGlossary";
import { TermHelp } from "../../../shared/presentation/TermHelp";

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
  useOperationalContextPublisher({
    machineId: MACHINE_ID,
    connection: connectionContext(state.connectionStatus),
    freshness: freshnessContext(state.freshness),
    replay: cursor
      ? { label: `재생 범위 #${cursor.replaySequence}`, tone: "positive" }
      : { label: "재생 범위 확인 중", tone: "neutral" },
  });

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
        <p className="eyebrow">근거 기반 검토</p>
        <h1>데이터 품질</h1>
        <p>{report.machineId}의 원천·수신·파생 품질을 서로 섞지 않고 보여줍니다.</p>
        <p className="quality-no-score">종합 품질 점수 없음</p>
        {refreshing && <p role="status">현재 재생 범위의 수신 품질을 맞추는 중입니다.</p>}
      </header>

      <div className="quality-workspace">
      <section className="quality-group quality-source" aria-label="원천과 의미 매핑">
        <GroupHeading title="원천과 의미 매핑"
          description="보존된 원천이 형식과 값 규칙을 통과하고 의미에 연결된 범위입니다.">
          <TermHelp term="PROVENANCE" />
        </GroupHeading>
        <div className="quality-dimension-list">
        <DimensionCard title="유효성" description="원천 형식과 값 규칙을 확인합니다.">
          <MeasurementRow label="원천·매핑" measurement={dimensions.validity.sourceProfile} />
        </DimensionCard>
        <DimensionCard title="의미 변환률" description="해석 성공이 아니라 의미 매핑 완료 비율입니다.">
          <MeasurementRow label={`매핑 버전 ${dimensions.semanticCoverage.mappingVersion}`}
            measurement={dimensions.semanticCoverage} />
        </DimensionCard>
        </div>
        <details className="quality-evidence">
          <summary>계산 근거와 출처 계보</summary>
          <dl>
            <dt>원천 프로파일 처리</dt><dd>{report.evidence.sourceProfileProcessingRunId}</dd>
            <dt>의미 매핑 처리</dt><dd>{report.evidence.semanticMappingProcessingRunId}</dd>
            <dt>원본 자료 식별자</dt><dd>{report.evidence.rawArtifactId}</dd>
          </dl>
          <a href={report.evidence.sourceHref} target="_blank" rel="noreferrer">고정된 NIST 원본 보기</a>
        </details>
      </section>

      <section className="quality-group quality-runtime" aria-label="수신과 순서">
        <GroupHeading title="수신과 순서" description="선택한 재생 세션과 위치 범위에서 관측된 전달 결과입니다." />
        <div className="quality-dimension-list">
        <div className="quality-runtime-validity">
          <MeasurementRow label="수신 유효성" measurement={dimensions.validity.runtime} />
        </div>
        <DimensionCard title="완전성" description="기대할 수 있는 데이터 대비 빠진 양입니다.">
          <MeasurementRow label="레코드 연속성" measurement={dimensions.completeness} />
        </DimensionCard>
        <DimensionCard title="순서" description="선언된 재생 순서보다 뒤늦게 도착했는지 확인합니다.">
          <MeasurementRow label="원천 파일" measurement={dimensions.ordering.sourceProfile} />
          <MeasurementRow label="현재 재생" measurement={dimensions.ordering.runtime} />
        </DimensionCard>
        <DimensionCard title="중복" description="같은 수신 identity가 반복 전달됐는지 확인합니다.">
          <MeasurementRow label="원천 파일" measurement={dimensions.duplication.sourceProfile} />
          <MeasurementRow label="현재 재생" measurement={dimensions.duplication.runtime} />
        </DimensionCard>
        </div>
        {report.runtime.status === "MEASURED" ? (
          <dl className="quality-facts">
            <Fact label="받음" value={report.runtime.receivedCount} />
            <Fact label="수락" value={report.runtime.acceptedCount} />
            <Fact label="중복" value={report.runtime.duplicateCount} />
            <Fact label="순서 지연" value={report.runtime.outOfOrderCount} />
          </dl>
        ) : <NotEvaluated reason="재생 수신 범위가 없어 평가하지 않았습니다." />}
      </section>

      <section className="quality-group quality-freshness" aria-label="데이터 최신성">
        <GroupHeading title="데이터 최신성"
          description="원천 시각이 아니라 ForgeSync에 마지막으로 반영된 시각을 기준으로 합니다.">
          <TermHelp term="FRESHNESS" />
        </GroupHeading>
        <div className="quality-dimension-list">
        <DimensionCard title="최신성" description="최근 반영 뒤 지난 시간을 기준값과 비교합니다.">
          <p className="quality-reading">
            {dimensions.freshness.status === "MEASURED"
              ? freshnessLabel(dimensions.freshness.value)
              : PROCESS_GLOSSARY.NOT_EVALUATED.label}
          </p>
          {dimensions.freshness.reason && <small>{dimensions.freshness.reason}</small>}
        </DimensionCard>
        </div>
      </section>

      <section className="quality-group quality-derived-group" aria-label="파생 분석 범위">
        <GroupHeading title="파생 분석 범위"
          description="원천 품질과 가공 분석에 실제 사용된 입력 범위를 구분합니다." />
        <div className="quality-derived-grid">
          <DerivedCard title="가공 구간 분리" status={report.derivedProcess.segmentation.status}
            values={[
              ["입력 관찰", report.derivedProcess.segmentation.inputObservationCount],
              ["생성 구간", report.derivedProcess.segmentation.resultCount],
            ]} reason={report.derivedProcess.segmentation.reason} />
          <DerivedCard title="특징값 확보 범위" status={report.derivedProcess.featureCoverage.status}
            values={[
              ["대상 run", report.derivedProcess.featureCoverage.eligibleRunCount],
              ["사용 가능", report.derivedProcess.featureCoverage.availableCount],
              ["일부만 있음", report.derivedProcess.featureCoverage.partialCount],
              ["없음", report.derivedProcess.featureCoverage.missingCount],
              ["빈 구간", report.derivedProcess.featureCoverage.emptyWindowCount],
            ]} reason={report.derivedProcess.featureCoverage.reason} />
        </div>
      </section>

      <section className="quality-group quality-unmapped" aria-label="미해석 원천 항목">
        <GroupHeading title="미해석 원천 항목"
          description={`${report.unmappedDataItems.length}종 · 원본 위치를 함께 보존합니다.`} />
        <div className="quality-table-wrap">
          <table className="quality-table">
            <thead><tr><th>구분</th><th>원천 항목<small>DataItem</small></th><th>레코드</th><th>첫 원본 위치</th></tr></thead>
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
      </div>
    </article>
  );
}

function connectionContext(status: TwinLiveState["connectionStatus"]): OperationalContextValue {
  return {
    LOADING: { label: "불러오는 중", tone: "neutral" },
    LIVE: { label: "연결됨", tone: "positive" },
    RECONNECTING: { label: "다시 연결 중", tone: "warning" },
    RESYNCING: { label: "상태 동기화 중", tone: "warning" },
    UNAVAILABLE: { label: "연결할 수 없음", tone: "critical" },
  }[status] as OperationalContextValue;
}

function freshnessContext(freshness: TwinLiveState["freshness"]): OperationalContextValue {
  if (freshness === "FRESH") return { label: "최신", tone: "positive" };
  if (freshness === "LAGGING") return { label: "반영 지연", tone: "warning" };
  if (freshness === "STALE") return { label: "오래된 데이터", tone: "critical" };
  return { label: "최신성 확인 중", tone: "neutral" };
}

function DimensionCard({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return <section className="quality-dimension"><header><h2>{title}</h2><p>{description}</p></header>{children}</section>;
}

function GroupHeading({ title, description, children }: {
  title: string; description: string; children?: ReactNode;
}) {
  return <header className="quality-group-heading"><div><h2>{title}</h2><p>{description}</p></div>
    {children}</header>;
}

function MeasurementRow({ label, measurement }: { label: string; measurement: DataQualityReport["dimensions"]["completeness"] }) {
  return <div className="quality-measurement"><span>{label}</span>
    <strong>{measurement.status === "MEASURED" && measurement.ratio !== null
      ? `${(measurement.ratio * 100).toLocaleString("ko-KR", { maximumFractionDigits: 2 })}%`
      : PROCESS_GLOSSARY.NOT_EVALUATED.label}</strong>
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
    : <NotEvaluated reason={reason} />}</section>;
}

function NotEvaluated({ reason }: { reason: string | null }) {
  return <div className="quality-not-evaluated"><strong>{PROCESS_GLOSSARY.NOT_EVALUATED.label}</strong>
    {reason && <p>{reason}</p>}</div>;
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

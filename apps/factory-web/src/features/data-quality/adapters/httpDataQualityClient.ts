import Ajv2020, { type ValidateFunction } from "ajv/dist/2020.js";

import schema from "../../../../../../contracts/data-quality/v1/data-quality-report.schema.json";
import type { DataQualityClient } from "../application/ports";
import type { DataQualityReport, DataQualityScope } from "../domain/dataQuality";

const ajv = new Ajv2020({ allErrors: true, strict: true, strictTypes: false });
ajv.addFormat("date-time", (value: string) => Number.isFinite(Date.parse(value)));
ajv.addFormat(
  "uuid",
  (value: string) =>
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      value,
    ),
);
const validate = ajv.compile<DataQualityReport>(schema) as ValidateFunction<DataQualityReport>;
const browserFetch: typeof fetch = (input, init) => fetch(input, init);

export class HttpDataQualityClient implements DataQualityClient {
  constructor(
    private readonly baseUrl: string,
    private readonly fetcher: typeof fetch = browserFetch,
  ) {}

  async load(
    machineId: string,
    scope?: DataQualityScope,
    signal?: AbortSignal,
  ): Promise<DataQualityReport> {
    const query = new URLSearchParams();
    if (scope) {
      query.set("replaySessionId", scope.replaySessionId);
      query.set("throughReplaySequence", scope.throughReplaySequence.toString());
    }
    const suffix = query.size === 0 ? "" : `?${query.toString()}`;
    const response = await this.fetcher(
      `${this.baseUrl}/api/v1/machines/${encodeURIComponent(machineId)}/data-quality${suffix}`,
      {
        headers: { Accept: "application/vnd.forgesync.data-quality.v1+json" },
        signal,
      },
    );
    if (!response.ok) throw new Error(`Data quality request failed (${response.status})`);
    const document: unknown = await response.json();
    if (!validate(document) || !isConsistent(document, machineId, scope)) {
      throw new Error("Data quality response does not satisfy contract v1");
    }
    return document;
  }
}

function isConsistent(
  report: DataQualityReport,
  machineId: string,
  scope?: DataQualityScope,
): boolean {
  if (report.machineId !== machineId || report.overallGrade !== null) return false;
  if (!scope) {
    if (report.replaySessionId !== null || report.throughReplaySequence !== null) return false;
  } else if (
    report.replaySessionId !== scope.replaySessionId ||
    report.throughReplaySequence !== scope.throughReplaySequence
  ) {
    return false;
  }
  return measurements(report).every(
    (item) =>
      item.grade === null &&
      (item.status === "MEASURED"
        ? item.denominator > 0 && item.ratio !== null
        : item.denominator === 0 && item.ratio === null),
  ) && report.runtime.acceptedCount + report.runtime.duplicateCount === report.runtime.receivedCount;
}

function measurements(report: DataQualityReport): QualityMeasurement[] {
  return [
    report.dimensions.validity.sourceProfile,
    report.dimensions.validity.runtime,
    report.dimensions.completeness,
    report.dimensions.ordering.sourceProfile,
    report.dimensions.ordering.runtime,
    report.dimensions.duplication.sourceProfile,
    report.dimensions.duplication.runtime,
    report.dimensions.semanticCoverage,
  ];
}

type QualityMeasurement = DataQualityReport["dimensions"]["completeness"];

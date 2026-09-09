import Ajv2020 from "ajv/dist/2020.js";

import downtimeParetoSchema from "../../../../../../contracts/twin/v1/downtime-pareto.schema.json";
import type { DowntimeParetoClient } from "../application/ports";
import type { DowntimeParetoReport } from "../domain/downtimePareto";

const ajv = new Ajv2020({ allErrors: true, strict: true, validateFormats: true });
ajv.addFormat("date-time", {
  type: "string",
  validate: (value: string) => Number.isFinite(Date.parse(value)),
});
ajv.addFormat("uuid", {
  type: "string",
  validate: (value: string) =>
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      value,
    ),
});
const validatePareto = ajv.compile<DowntimeParetoReport>(downtimeParetoSchema);

export class HttpDowntimeParetoClient implements DowntimeParetoClient {
  constructor(private readonly apiBaseUrl: string) {}

  async analyze(
    machineId: string,
    replaySessionId: string,
    throughReplaySequence: number,
    signal?: AbortSignal,
  ): Promise<DowntimeParetoReport> {
    const machinePath = encodeURIComponent(machineId);
    const interval = await this.post(
      `/api/v1/machines/${machinePath}/equipment-state-intervals/processing-runs`,
      {
        replaySessionId,
        throughReplaySequence,
        intervalRuleVersion: "1.0.0",
      },
      "application/vnd.forgesync.equipment-state-intervals.v1+json",
      signal,
    );
    const utilization = await this.post(
      `/api/v1/machines/${machinePath}/utilization-kpis/processing-runs`,
      {
        intervalProcessingRunId: processingRunIdOf(interval),
        calculationVersion: "1.0.0",
      },
      "application/vnd.forgesync.utilization-kpis.v1+json",
      signal,
    );
    const document = await this.post(
      `/api/v1/machines/${machinePath}/downtime-pareto/processing-runs`,
      {
        utilizationProcessingRunId: processingRunIdOf(utilization),
        ruleVersion: "1.0.0",
      },
      "application/vnd.forgesync.downtime-pareto.v1+json",
      signal,
    );
    if (!validatePareto(document)) {
      throw new Error("Downtime Pareto response does not satisfy contract v1");
    }
    return document;
  }

  private async post(
    path: string,
    body: object,
    accept: string,
    signal?: AbortSignal,
  ): Promise<unknown> {
    const response = await fetch(`${this.apiBaseUrl}${path}`, {
      method: "POST",
      headers: { Accept: accept, "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal,
    });
    if (!response.ok) {
      throw new Error(`Downtime analysis request failed with ${response.status}`);
    }
    return response.json();
  }
}

function processingRunIdOf(document: unknown): string {
  if (
    typeof document !== "object" ||
    document === null ||
    !("processingRunId" in document) ||
    typeof document.processingRunId !== "string" ||
    !/^sha256:[0-9a-f]{64}$/.test(document.processingRunId)
  ) {
    throw new Error("Processing response is missing a valid immutable reference");
  }
  return document.processingRunId;
}

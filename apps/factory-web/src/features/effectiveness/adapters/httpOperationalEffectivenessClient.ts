import Ajv2020 from "ajv/dist/2020.js";

import schema from "../../../../../../contracts/process-analytics/v1/operational-effectiveness.schema.json";
import type { OperationalEffectivenessClient } from "../application/ports";
import type { OperationalEffectivenessReport } from "../domain/operationalEffectiveness";

const ajv = new Ajv2020({ allErrors: true, strict: true, validateFormats: true });
ajv.addFormat("date-time", { type: "string", validate: (value: string) => Number.isFinite(Date.parse(value)) });
ajv.addFormat("uuid", { type: "string", validate: (value: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value) });
const validate = ajv.compile<OperationalEffectivenessReport>(schema);

export class HttpOperationalEffectivenessClient implements OperationalEffectivenessClient {
  constructor(private readonly apiBaseUrl: string) {}

  async analyze(machineId: string, replaySessionId: string, throughReplaySequence: number,
    signal?: AbortSignal): Promise<OperationalEffectivenessReport> {
    const base = `/api/v1/machines/${encodeURIComponent(machineId)}`;
    const runs = await this.post(`${base}/machining-runs/processing-runs`, {
      replaySessionId, throughReplaySequence, segmentationRuleVersion: "1.0.0",
    }, "application/vnd.forgesync.machining-runs.v1+json", signal);
    const cycles = await this.post(`${base}/cycle-features/processing-runs`, {
      machiningRunProcessingRunId: processingRunIdOf(runs), cycleFeatureVersion: "1.0.0",
    }, "application/vnd.forgesync.cycle-features.v1+json", signal);
    const intervals = await this.post(`${base}/equipment-state-intervals/processing-runs`, {
      replaySessionId, throughReplaySequence, intervalRuleVersion: "1.0.0",
    }, "application/vnd.forgesync.equipment-state-intervals.v1+json", signal);
    const utilization = await this.post(`${base}/utilization-kpis/processing-runs`, {
      intervalProcessingRunId: processingRunIdOf(intervals), calculationVersion: "1.0.0",
    }, "application/vnd.forgesync.utilization-kpis.v1+json", signal);
    const report = await this.post(`${base}/operational-effectiveness/processing-runs`, {
      utilizationProcessingRunId: processingRunIdOf(utilization),
      cycleFeatureProcessingRunId: featureProcessingRunIdOf(cycles), policyVersion: "1.0.0",
      assumedIdealCycleSecondsByProgram: {},
    }, "application/vnd.forgesync.operational-effectiveness.v1+json", signal);
    if (!validate(report)) throw new Error("Operational effectiveness response does not satisfy contract v1");
    return report;
  }

  private async post(path: string, body: object, accept: string, signal?: AbortSignal): Promise<unknown> {
    const response = await fetch(`${this.apiBaseUrl}${path}`, { method: "POST", signal,
      headers: { Accept: accept, "Content-Type": "application/json" }, body: JSON.stringify(body) });
    if (!response.ok) throw new Error(`Operational effectiveness request failed with ${response.status}`);
    return response.json();
  }
}

function idOf(document: unknown, key: string): string {
  if (typeof document !== "object" || document === null || !(key in document)) throw new Error("Missing immutable reference");
  const value = (document as Record<string, unknown>)[key];
  if (typeof value !== "string" || !/^sha256:[0-9a-f]{64}$/.test(value)) throw new Error("Invalid immutable reference");
  return value;
}
const processingRunIdOf = (document: unknown) => idOf(document, "processingRunId");
const featureProcessingRunIdOf = (document: unknown) => idOf(document, "featureProcessingRunId");

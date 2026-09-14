import Ajv2020 from "ajv/dist/2020.js";
import type { ValidateFunction } from "ajv";

import machiningRunsSchema from "../../../../../../contracts/process-analytics/v1/machining-runs.schema.json";
import toolChangesSchema from "../../../../../../contracts/tool-changes/v1/tool-change-timeline.schema.json";
import downtimeSchema from "../../../../../../contracts/twin/v1/downtime-pareto.schema.json";
import intervalsSchema from "../../../../../../contracts/twin/v1/equipment-state-intervals.schema.json";
import utilizationSchema from "../../../../../../contracts/twin/v1/utilization-kpis.schema.json";
import type { DowntimeParetoEntry } from "../../downtime/domain/downtimePareto";
import { ShiftOverviewError, type ShiftOverviewClient } from "../application/ports";
import type { ShiftInterval, ShiftMarker, ShiftOverview, ShiftState } from "../domain/shiftOverview";

interface IdentityDocument {
  machineId: string; replaySessionId: string; throughReplaySequence: number;
}
interface ProcessingDocument extends IdentityDocument { processingRunId: string }
interface IntervalDocument extends ProcessingDocument {
  observedFrom: string; observedTo: string;
  intervals: { signal: string; value?: string; startedAt: string; endedAt?: string }[];
}
interface UtilizationDocument extends ProcessingDocument {
  intervalProcessingRunId: string;
  state: { status: string; states: { state: string; ratioPercent: number }[] };
  counters: { cuttingRatio: { status: string; ratioPercent?: number } };
}
interface ParetoDocument extends ProcessingDocument {
  schemaVersion: "1.0.0";
  utilizationProcessingRunId: string; intervalProcessingRunId: string;
  totalDowntimeSeconds: number; entries: DowntimeParetoEntry[];
}
interface MachiningRunsDocument extends ProcessingDocument {
  machiningRuns: { status: string }[];
}
interface ToolChangesDocument extends IdentityDocument {
  toolChanges: { sourceObservedAt: string; fromToolNumber: number; toToolNumber: number }[];
}

const ajv = new Ajv2020({ allErrors: true, strict: true, strictRequired: false, validateFormats: true });
ajv.addFormat("date-time", { type: "string", validate: (value: string) => Number.isFinite(Date.parse(value)) });
ajv.addFormat("uuid", { type: "string", validate: (value: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value) });
const validateIntervals = ajv.compile<IntervalDocument>(intervalsSchema);
const validateUtilization = ajv.compile<UtilizationDocument>(utilizationSchema);
const validatePareto = ajv.compile<ParetoDocument>(downtimeSchema);
const validateRuns = ajv.compile<MachiningRunsDocument>(machiningRunsSchema);
const validateToolChanges = ajv.compile<ToolChangesDocument>(toolChangesSchema);

export class HttpShiftOverviewClient implements ShiftOverviewClient {
  constructor(private readonly apiBaseUrl: string) {}

  async load(machineId: string, replaySessionId: string, throughReplaySequence: number,
    signal?: AbortSignal): Promise<ShiftOverview> {
    const base = `/api/v1/machines/${encodeURIComponent(machineId)}`;
    const [intervals, runs, toolChanges] = await Promise.all([
      this.post(`${base}/equipment-state-intervals/processing-runs`, {
        replaySessionId, throughReplaySequence, intervalRuleVersion: "1.0.0",
      }, "application/vnd.forgesync.equipment-state-intervals.v1+json", validateIntervals, signal),
      this.post(`${base}/machining-runs/processing-runs`, {
        replaySessionId, throughReplaySequence, segmentationRuleVersion: "1.0.0",
      }, "application/vnd.forgesync.machining-runs.v1+json", validateRuns, signal),
      this.get(`${base}/tool-changes`, { replaySessionId, throughReplaySequence: String(throughReplaySequence) },
        "application/vnd.forgesync.tool-changes.v1+json", validateToolChanges, signal),
    ]);
    this.requireAtOrBefore([intervals, runs, toolChanges], machineId, replaySessionId,
      throughReplaySequence);

    const utilization = await this.post(`${base}/utilization-kpis/processing-runs`, {
      intervalProcessingRunId: intervals.processingRunId, calculationVersion: "1.0.0",
    }, "application/vnd.forgesync.utilization-kpis.v1+json", validateUtilization, signal);
    this.requireIdentity([utilization], machineId, replaySessionId, intervals.throughReplaySequence);
    requireReference(utilization.intervalProcessingRunId, intervals.processingRunId);

    const pareto = await this.post(`${base}/downtime-pareto/processing-runs`, {
      utilizationProcessingRunId: utilization.processingRunId, ruleVersion: "1.1.0",
    }, "application/vnd.forgesync.downtime-pareto.v1+json", validatePareto, signal);
    this.requireIdentity([pareto], machineId, replaySessionId, utilization.throughReplaySequence);
    requireReference(pareto.utilizationProcessingRunId, utilization.processingRunId);
    requireReference(pareto.intervalProcessingRunId, intervals.processingRunId);

    return mapOverview(intervals, utilization, pareto, runs, toolChanges,
      throughReplaySequence);
  }

  private async post<T>(path: string, body: object, accept: string,
    validate: ValidateFunction<T>, signal?: AbortSignal): Promise<T> {
    const value = await this.postUntyped(path, body, accept, signal);
    if (!validate(value)) throw new ShiftOverviewError("VERSION_MISMATCH",
      `${accept}: ${ajv.errorsText(validate.errors)}`);
    return value;
  }

  private async postUntyped(path: string, body: object, accept: string, signal?: AbortSignal): Promise<unknown> {
    return this.request(`${this.apiBaseUrl}${path}`, {
      method: "POST", signal,
      headers: { Accept: accept, "Content-Type": "application/json" }, body: JSON.stringify(body),
    });
  }

  private async get<T>(path: string, query: Record<string, string>, accept: string,
    validate: ValidateFunction<T>, signal?: AbortSignal): Promise<T> {
    const value = await this.request(`${this.apiBaseUrl}${path}?${new URLSearchParams(query)}`, {
      signal, headers: { Accept: accept },
    });
    if (!validate(value)) throw new ShiftOverviewError("VERSION_MISMATCH",
      `${accept}: ${ajv.errorsText(validate.errors)}`);
    return value;
  }

  private async request(url: string, init: RequestInit): Promise<unknown> {
    let response: Response;
    try { response = await fetch(url, init); }
    catch { throw new ShiftOverviewError("NETWORK"); }
    if (!response.ok) {
      throw new ShiftOverviewError(response.status === 404 ? "INSUFFICIENT_DATA" : "NETWORK");
    }
    try { return await response.json(); }
    catch { throw new ShiftOverviewError("VERSION_MISMATCH"); }
  }

  private requireIdentity(documents: IdentityDocument[], machineId: string, replaySessionId: string,
    throughReplaySequence: number): void {
    if (documents.some((document) => document.machineId !== machineId
      || document.replaySessionId !== replaySessionId
      || document.throughReplaySequence !== throughReplaySequence)) {
      throw new ShiftOverviewError("VERSION_MISMATCH", "derived projection identity");
    }
  }

  private requireAtOrBefore(documents: IdentityDocument[], machineId: string, replaySessionId: string,
    requestedThroughReplaySequence: number): void {
    if (documents.some((document) => document.machineId !== machineId
      || document.replaySessionId !== replaySessionId
      || document.throughReplaySequence > requestedThroughReplaySequence)) {
      throw new ShiftOverviewError("VERSION_MISMATCH", "source projection identity");
    }
  }
}

function mapOverview(intervals: IntervalDocument, utilization: UtilizationDocument,
  pareto: ParetoDocument, runs: MachiningRunsDocument, toolChanges: ToolChangesDocument,
  requestedThroughReplaySequence: number): ShiftOverview {
  const stateIntervals = intervals.intervals.filter((interval) => interval.signal === "EXECUTION")
    .map((interval): ShiftInterval => ({
      state: stateOf(interval.value), startedAt: interval.startedAt, endedAt: interval.endedAt,
    }));
  const markers: ShiftMarker[] = [
    ...pareto.entries.map((entry): ShiftMarker => ({ kind: "DOWNTIME",
      sourceObservedAt: entry.startedAt, seekTo: entry.startedAt,
      label: `${downtimeLabel(entry.state)} ${entry.startedAt} 시작`, })),
    ...toolChanges.toolChanges.map((change): ShiftMarker => ({ kind: "TOOL_CHANGE",
      sourceObservedAt: change.sourceObservedAt, seekTo: change.sourceObservedAt,
      label: `공구 교체 ${change.fromToolNumber}번에서 ${change.toToolNumber}번 ${change.sourceObservedAt}`, })),
  ].sort((left, right) => Date.parse(left.sourceObservedAt) - Date.parse(right.sourceObservedAt));
  return {
    machineId: intervals.machineId, replaySessionId: intervals.replaySessionId,
    throughReplaySequence: requestedThroughReplaySequence,
    observedFrom: intervals.observedFrom, observedTo: intervals.observedTo,
    availabilityPercent: utilization.state.status === "UNAVAILABLE" ? undefined
      : utilization.state.states.find((state) => state.state === "ACTIVE")?.ratioPercent,
    cuttingPercent: utilization.counters.cuttingRatio.status !== "UNAVAILABLE"
      ? utilization.counters.cuttingRatio.ratioPercent : undefined,
    downtimeSeconds: pareto.totalDowntimeSeconds,
    totalMachiningCount: runs.machiningRuns.length,
    completedMachiningCount: runs.machiningRuns.filter((run) => run.status === "COMPLETED").length,
    intervalProcessingRunId: intervals.processingRunId,
    utilizationProcessingRunId: utilization.processingRunId,
    intervals: stateIntervals, markers,
    pareto: {
      schemaVersion: pareto.schemaVersion,
      processingRunId: pareto.processingRunId,
      machineId: pareto.machineId,
      replaySessionId: pareto.replaySessionId,
      throughReplaySequence: pareto.throughReplaySequence,
      totalDowntimeSeconds: pareto.totalDowntimeSeconds,
      entries: pareto.entries,
    },
  };
}

function stateOf(value?: string): ShiftState {
  if (value === undefined) return "UNKNOWN";
  if (value === "FEED_HOLD") return "INTERRUPTED";
  if (["ACTIVE", "READY", "STOPPED", "INTERRUPTED", "UNKNOWN"].includes(value)) {
    return value as ShiftState;
  }
  throw new ShiftOverviewError("VERSION_MISMATCH", `execution state ${value}`);
}

function downtimeLabel(state: string): string {
  return ({ STOPPED: "정지", INTERRUPTED: "가공 중단", UNKNOWN: "확인 불가" } as Record<string, string>)[state]
    ?? "비가동";
}

function requireReference(actual: string, expected: string): void {
  if (actual !== expected) throw new ShiftOverviewError("VERSION_MISMATCH", "processing reference");
}

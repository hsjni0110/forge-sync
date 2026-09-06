import type { MachiningRun } from "./processAnalysis";

export type RunClassification = "NORMAL" | "DEVIATING" | "HIGH_DEVIATION" | "UNAVAILABLE";

export interface RunFilters {
  programs: string[];
  classifications: RunClassification[];
  minimumDurationSeconds?: number;
  maximumDurationSeconds?: number;
}

export interface ProgramRunGroup {
  program: string;
  count: number;
  medianDurationSeconds: number | null;
  totalDurationSeconds: number;
  classifications: Record<RunClassification, number>;
  runs: MachiningRun[];
}

export interface TimelineItem {
  startPercent: number;
  endPercent: number;
  runIds: string[];
  runs: MachiningRun[];
}

export interface TimelineLayout {
  mode: "INDIVIDUAL" | "AGGREGATED";
  range: { startsAt: string; endsAt: string };
  items: TimelineItem[];
  leadingIdlePercent: number;
}

export function runClassification(run: MachiningRun): RunClassification {
  if (run.status !== "COMPLETED" || run.endedAt === undefined) return "UNAVAILABLE";
  const classification = run.assessment?.status === "AVAILABLE" ? run.assessment.classification : undefined;
  return classification === "NORMAL" || classification === "DEVIATING" || classification === "HIGH_DEVIATION"
    ? classification
    : "UNAVAILABLE";
}

export function runDurationSeconds(run: MachiningRun): number | undefined {
  if (!run.endedAt) return undefined;
  const duration = (Date.parse(run.endedAt) - Date.parse(run.startedAt)) / 1000;
  return Number.isFinite(duration) && duration >= 0 ? duration : undefined;
}

export function filterAndGroupRuns(runs: MachiningRun[], filters: RunFilters): {
  runs: MachiningRun[];
  groups: ProgramRunGroup[];
} {
  const filtered = runs.filter((run) => {
    const duration = runDurationSeconds(run);
    return (filters.programs.length === 0 || filters.programs.includes(run.program ?? "미확인"))
      && (filters.classifications.length === 0 || filters.classifications.includes(runClassification(run)))
      && (filters.minimumDurationSeconds === undefined || (duration !== undefined && duration >= filters.minimumDurationSeconds))
      && (filters.maximumDurationSeconds === undefined || (duration !== undefined && duration <= filters.maximumDurationSeconds));
  });
  const grouped = new Map<string, MachiningRun[]>();
  for (const run of filtered) {
    const program = run.program ?? "미확인";
    grouped.set(program, [...(grouped.get(program) ?? []), run]);
  }
  return {
    runs: filtered,
    groups: [...grouped.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([program, groupRuns]) => {
      const durations = groupRuns.map(runDurationSeconds).filter((value): value is number => value !== undefined).sort((a, b) => a - b);
      const middle = Math.floor(durations.length / 2);
      const median = durations.length === 0 ? null : durations.length % 2 === 0
        ? (durations[middle - 1] + durations[middle]) / 2 : durations[middle];
      const classifications: Record<RunClassification, number> = {
        NORMAL: 0, DEVIATING: 0, HIGH_DEVIATION: 0, UNAVAILABLE: 0,
      };
      for (const run of groupRuns) classifications[runClassification(run)] += 1;
      return {
        program, count: groupRuns.length, medianDurationSeconds: median,
        totalDurationSeconds: durations.reduce((sum, value) => sum + value, 0), classifications, runs: groupRuns,
      };
    }),
  };
}

export function layoutTimeline(
  runs: MachiningRun[],
  range: { startsAt: string; endsAt: string },
  maximumItems = 32,
): TimelineLayout {
  const rangeStart = Date.parse(range.startsAt);
  const rangeEnd = Date.parse(range.endsAt);
  const span = Math.max(1, rangeEnd - rangeStart);
  const percent = (time: number) => Math.min(100, Math.max(0, ((time - rangeStart) / span) * 100));
  const firstStart = runs.length === 0 ? rangeStart : Math.min(...runs.map((run) => Date.parse(run.startedAt)));
  if (runs.length <= maximumItems) {
    return {
      mode: "INDIVIDUAL", range,
      items: runs.map((run) => ({
        startPercent: percent(Date.parse(run.startedAt)),
        endPercent: percent(Date.parse(run.endedAt ?? run.startedAt)),
        runIds: [run.id], runs: [run],
      })),
      leadingIdlePercent: percent(firstStart),
    };
  }
  const bins = Array.from({ length: maximumItems }, () => [] as MachiningRun[]);
  for (const run of runs) {
    const index = Math.min(maximumItems - 1, Math.floor((percent(Date.parse(run.startedAt)) / 100) * maximumItems));
    bins[index].push(run);
  }
  return {
    mode: "AGGREGATED", range,
    items: bins.flatMap((bucket, index) => bucket.length === 0 ? [] : [{
      startPercent: (index / maximumItems) * 100,
      endPercent: ((index + 1) / maximumItems) * 100,
      runIds: bucket.map((run) => run.id), runs: bucket,
    }]),
    leadingIdlePercent: percent(firstStart),
  };
}

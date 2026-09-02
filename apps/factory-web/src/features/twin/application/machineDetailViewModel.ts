import type {
  Availability,
  FieldProvenance,
  ObservedValue,
  TwinSnapshot,
} from "../domain/twin";

export interface MachineDetailMetric {
  key: string;
  label: string;
  value: string;
  detail?: string;
  availability: Availability | "MISSING";
}

export interface MachineDetailCondition {
  key: string;
  conditionType: string;
  level: string;
  message?: string;
  componentId: string;
}

export interface MachineDetailProvenance {
  key: string;
  field: string;
  sourceLabel: string;
  sourceSetId: string;
  artifactId: string;
  rawRecordId: string;
  mappingVersion: string;
  sourceDataItemId: string;
}

export interface MachineDetailViewModel {
  machineId: string;
  twinVersion: number;
  projectedAt: string;
  consistency: TwinSnapshot["consistency"]["status"];
  missingFields: string[];
  freshness: TwinSnapshot["state"]["freshness"];
  states: Array<{ label: string; value: string }>;
  metrics: MachineDetailMetric[];
  conditions: MachineDetailCondition[];
  provenance: MachineDetailProvenance[];
}

export function mapTwinToMachineDetail(snapshot: TwinSnapshot): MachineDetailViewModel {
  const provenance: MachineDetailProvenance[] = [];
  appendProvenance(provenance, "네트워크 연결", snapshot.state.connectivity.provenance);
  appendProvenance(provenance, "가동 상태", snapshot.state.execution.provenance);
  appendProvenance(provenance, "설비 상태", snapshot.state.health.provenance);

  const spindleSpeeds = snapshot.metrics.spindleSpeeds.map((speed) => {
    appendProvenance(provenance, `주축 속도 · ${speed.observation.componentId}`, [
      speed.provenance,
    ]);
    return {
      key: `spindle-${speed.provenance.transformation.sourceDataItemId}`,
      label: `주축 속도 · ${speed.observation.componentId}`,
      value:
        speed.availability === "AVAILABLE" && speed.value !== undefined
          ? `${speed.value} rpm`
          : "확인할 수 없음",
      detail: speed.provenance.transformation.sourceDataItemId,
      availability: speed.availability,
    } satisfies MachineDetailMetric;
  });

  const toolNumber = observedMetric("tool", "공구 번호", snapshot.metrics.toolNumber);
  const program = observedMetric("program", "실행 프로그램", snapshot.metrics.program);
  if (snapshot.metrics.toolNumber) {
    appendProvenance(provenance, "공구 번호", [snapshot.metrics.toolNumber.provenance]);
  }
  if (snapshot.metrics.program) {
    appendProvenance(provenance, "실행 프로그램", [snapshot.metrics.program.provenance]);
  }

  const conditions = snapshot.conditions.map((condition, index) => {
    appendProvenance(provenance, `상태 신호 · ${condition.conditionType}`, [
      condition.provenance,
    ]);
    return {
      key: `${condition.provenance.transformation.sourceDataItemId}-${index}`,
      conditionType: condition.conditionType,
      level: translateCode(condition.level),
      message: condition.message,
      componentId: condition.observation.componentId,
    };
  });

  return {
    machineId: snapshot.machine.machineId,
    twinVersion: snapshot.consistency.twinVersion,
    projectedAt: snapshot.consistency.projectedAt,
    consistency: snapshot.consistency.status,
    missingFields: snapshot.consistency.missingFields.map(translateMissingField),
    freshness: snapshot.state.freshness,
    states: [
      { label: "네트워크 연결", value: translateCode(snapshot.state.connectivity.value) },
      { label: "가동 상태", value: translateCode(snapshot.state.execution.value) },
      { label: "설비 상태", value: translateCode(snapshot.state.health.value) },
    ],
    metrics: [...spindleSpeeds, toolNumber, program],
    conditions,
    provenance,
  };
}

function observedMetric(
  key: string,
  label: string,
  observed: ObservedValue<number | string> | undefined,
): MachineDetailMetric {
  if (!observed) {
    return { key, label, value: "확인할 수 없음", availability: "MISSING" };
  }
  return {
    key,
    label,
    value:
      observed.availability === "AVAILABLE" && observed.value !== undefined
        ? String(observed.value)
        : "확인할 수 없음",
    detail: observed.provenance.transformation.sourceDataItemId,
    availability: observed.availability,
  };
}

function appendProvenance(
  target: MachineDetailProvenance[],
  field: string,
  provenance: FieldProvenance[],
): void {
  provenance.forEach((item, index) => {
    target.push({
      key: `${field}-${item.transformation.sourceDataItemId}-${index}`,
      field,
      sourceLabel: `실제 데이터 · ${item.source.provider} (${item.source.kind}:${item.source.provider})`,
      sourceSetId: item.source.sourceSetId,
      artifactId: item.source.artifactId,
      rawRecordId: item.transformation.rawRecordId,
      mappingVersion: item.transformation.mappingVersion,
      sourceDataItemId: item.transformation.sourceDataItemId,
    });
  });
}

const CODE_LABELS: Record<string, string> = {
  UNKNOWN: "확인되지 않음",
  ONLINE: "온라인",
  OFFLINE: "오프라인",
  READY: "작업 준비됨",
  ACTIVE: "가동 중",
  IDLE: "대기 중",
  HOLD: "일시 정지",
  STOPPED: "정지됨",
  NORMAL: "정상",
  WARNING: "주의",
  FAULT: "고장",
  UNAVAILABLE: "확인할 수 없음",
};

function translateCode(value: string): string {
  return CODE_LABELS[value] ?? value;
}

function translateMissingField(field: string): string {
  const labels: Record<string, string> = {
    "state.connectivity": "네트워크 연결",
    "state.execution": "가동 상태",
    "state.health": "설비 상태",
    "metrics.spindleSpeeds": "주축 속도",
    "metrics.toolNumber": "공구 번호",
    "metrics.program": "실행 프로그램",
  };
  return labels[field] ?? field;
}

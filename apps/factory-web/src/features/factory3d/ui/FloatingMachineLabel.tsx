import type {
  MachineSceneBinding,
  MachineVisualState,
} from "../domain/machineVisualState";
import type { MachineVisualPresentation } from "../domain/machineVisualPresentation";

export function FloatingMachineLabel({
  binding,
  visualState,
  visualPresentation,
  onSelectMachine,
}: {
  binding: MachineSceneBinding;
  visualState: MachineVisualState | undefined;
  visualPresentation: MachineVisualPresentation;
  onSelectMachine: (machineId: string) => void;
}) {
  const isSelected = visualState?.selected ?? false;
  return (
    <button
      type="button"
      className={`floating-machine-label${isSelected ? " is-selected" : ""}`}
      aria-pressed={isSelected}
      data-status={visualPresentation.status.toLowerCase()}
      onClick={() => onSelectMachine(binding.machineId)}
    >
      <strong>{binding.machineId}</strong>
      <span>
        {visualState ? `Twin v${visualState.twinVersion}` : "Twin 불러오는 중"}
      </span>
      <span className="machine-visual-status">
        <span aria-hidden="true">{visualPresentation.statusSymbol}</span>{" "}
        {visualPresentation.statusLabel}
      </span>
      <span>
        {visualState?.rpm === undefined
          ? "RPM 확인할 수 없음"
          : `${visualState.rpm} RPM`}
      </span>
      {/* The scene already shows whether the spindle turns and which machine is ringed, so these
          stay for readers who cannot see either. */}
      <span className="visually-hidden">
        시각 회전 {visualPresentation.isSpindleAnimating ? "켜짐" : "꺼짐"}
      </span>
      {isSelected && <span className="visually-hidden">선택됨</span>}
    </button>
  );
}

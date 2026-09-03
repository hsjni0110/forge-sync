import type {
  MachineSceneBinding,
  MachineVisualState,
} from "../domain/machineVisualState";

export function FloatingMachineLabel({
  binding,
  visualState,
  onSelectMachine,
}: {
  binding: MachineSceneBinding;
  visualState: MachineVisualState | undefined;
  onSelectMachine: (machineId: string) => void;
}) {
  const isSelected = visualState?.selected ?? false;
  return (
    <button
      type="button"
      className={`floating-machine-label${isSelected ? " is-selected" : ""}`}
      aria-pressed={isSelected}
      onClick={() => onSelectMachine(binding.machineId)}
    >
      <strong>{binding.machineId}</strong>
      <span>
        {visualState ? `Twin v${visualState.twinVersion}` : "Twin 불러오는 중"}
      </span>
      {isSelected && <span>선택됨</span>}
    </button>
  );
}

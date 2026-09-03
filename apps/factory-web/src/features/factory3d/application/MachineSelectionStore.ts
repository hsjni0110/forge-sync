type SelectionListener = () => void;

export class MachineSelectionStore {
  private readonly listeners = new Set<SelectionListener>();

  constructor(private selectedMachineId: string | undefined) {}

  currentSelection = (): string | undefined => this.selectedMachineId;

  selectMachine = (machineId: string): void => {
    if (this.selectedMachineId === machineId) {
      return;
    }
    this.selectedMachineId = machineId;
    this.listeners.forEach((listener) => listener());
  };

  subscribe = (listener: SelectionListener): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };
}

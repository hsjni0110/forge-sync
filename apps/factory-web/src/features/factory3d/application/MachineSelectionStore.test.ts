import { describe, expect, it, vi } from "vitest";

import { MachineSelectionStore } from "./MachineSelectionStore";

describe("MachineSelectionStore", () => {
  it("publishes a changed machine selection and ignores the same identity", () => {
    const store = new MachineSelectionStore("Mazak01");
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    store.selectMachine("Mazak01");
    expect(listener).not.toHaveBeenCalled();

    store.selectMachine("Mazak02");
    expect(store.currentSelection()).toBe("Mazak02");
    expect(listener).toHaveBeenCalledOnce();

    unsubscribe();
    store.selectMachine("Mazak03");
    expect(listener).toHaveBeenCalledOnce();
  });
});

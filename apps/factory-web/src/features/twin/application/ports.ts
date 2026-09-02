import type { TwinPatch, TwinSnapshot } from "../domain/twin";

export interface TwinSnapshotReader {
  loadSnapshot(machineId: string): Promise<TwinSnapshot>;
}

export interface TwinPatchDecoder {
  decode(message: string, expectedMachineId: string): TwinPatch;
}

export interface TwinSocket {
  close(): void;
}

export interface TwinSocketCallbacks {
  opened(): void;
  received(message: string): void;
  closed(): void;
}

export interface TwinSocketFactory {
  connect(machineId: string, callbacks: TwinSocketCallbacks): TwinSocket;
}

export interface Clock {
  nowMillis(): number;
}

export interface Timer {
  schedule(callback: () => void, delayMillis: number): number;
  cancel(timerId: number): void;
}

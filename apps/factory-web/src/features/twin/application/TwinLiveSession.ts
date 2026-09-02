import { classifyFreshness, nextFreshnessBoundaryMillis } from "../domain/freshness";
import type { Freshness, TwinSnapshot } from "../domain/twin";
import { decidePatch } from "../domain/versionGuard";
import type {
  Clock,
  Timer,
  TwinPatchDecoder,
  TwinSnapshotReader,
  TwinSocket,
  TwinSocketFactory,
} from "./ports";

export type ConnectionStatus =
  | "LOADING"
  | "LIVE"
  | "RECONNECTING"
  | "RESYNCING"
  | "UNAVAILABLE";

export interface TwinLiveState {
  connectionStatus: ConnectionStatus;
  snapshot?: TwinSnapshot;
  freshness?: Freshness;
}

type StateListener = (state: TwinLiveState) => void;

const RECONNECT_DELAYS_MILLIS = [1_000, 2_000, 4_000, 8_000, 10_000] as const;

export class TwinLiveSession {
  private state: TwinLiveState = { connectionStatus: "LOADING" };
  private readonly listeners = new Set<StateListener>();
  private socket?: TwinSocket;
  private reconnectTimerId?: number;
  private freshnessTimerId?: number;
  private reconnectAttempt = 0;
  private generation = 0;
  private isDisposed = false;

  constructor(
    private readonly machineId: string,
    private readonly snapshotReader: TwinSnapshotReader,
    private readonly socketFactory: TwinSocketFactory,
    private readonly patchDecoder: TwinPatchDecoder,
    private readonly clock: Clock,
    private readonly timer: Timer,
  ) {}

  start(): void {
    void this.resynchronize("LOADING");
  }

  subscribe(listener: StateListener): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => this.listeners.delete(listener);
  }

  currentState(): TwinLiveState {
    return this.state;
  }

  dispose(): void {
    this.isDisposed = true;
    this.generation += 1;
    this.closeSocket();
    this.cancelTimer(this.reconnectTimerId);
    this.cancelTimer(this.freshnessTimerId);
    this.listeners.clear();
  }

  private async resynchronize(status: ConnectionStatus): Promise<void> {
    const operationGeneration = ++this.generation;
    this.closeSocket();
    this.updateState({ ...this.state, connectionStatus: status });
    try {
      const snapshot = await this.snapshotReader.loadSnapshot(this.machineId);
      if (this.isStaleOperation(operationGeneration)) {
        return;
      }
      this.updateSnapshot(snapshot);
      this.connectSocket(operationGeneration);
    } catch {
      if (this.isStaleOperation(operationGeneration)) {
        return;
      }
      this.updateState({ ...this.state, connectionStatus: "UNAVAILABLE" });
      this.scheduleReconnect();
    }
  }

  private connectSocket(operationGeneration: number): void {
    this.socket = this.socketFactory.connect(this.machineId, {
      opened: () => {
        if (this.isStaleOperation(operationGeneration)) {
          return;
        }
        this.reconnectAttempt = 0;
        this.updateState({ ...this.state, connectionStatus: "LIVE" });
      },
      received: (message) => this.receivePatch(message, operationGeneration),
      closed: () => {
        if (this.isStaleOperation(operationGeneration)) {
          return;
        }
        this.socket = undefined;
        this.updateState({ ...this.state, connectionStatus: "RECONNECTING" });
        this.scheduleReconnect();
      },
    });
  }

  private receivePatch(message: string, operationGeneration: number): void {
    if (this.isStaleOperation(operationGeneration) || !this.state.snapshot) {
      return;
    }
    try {
      const patch = this.patchDecoder.decode(message, this.machineId);
      const decision = decidePatch(
        this.state.snapshot.consistency.twinVersion,
        patch,
      );
      if (decision === "IGNORE") {
        return;
      }
      if (decision === "RESYNC") {
        void this.resynchronize("RESYNCING");
        return;
      }
      this.updateSnapshot(patch.snapshot);
    } catch {
      void this.resynchronize("RESYNCING");
    }
  }

  private updateSnapshot(snapshot: TwinSnapshot): void {
    this.updateState({
      connectionStatus: this.state.connectionStatus,
      snapshot,
      freshness: classifyFreshness(snapshot, this.clock.nowMillis()),
    });
    this.scheduleFreshnessBoundary();
  }

  private scheduleFreshnessBoundary(): void {
    this.cancelTimer(this.freshnessTimerId);
    this.freshnessTimerId = undefined;
    if (!this.state.snapshot) {
      return;
    }
    const delayMillis = nextFreshnessBoundaryMillis(
      this.state.snapshot,
      this.clock.nowMillis(),
    );
    if (delayMillis === undefined) {
      return;
    }
    this.freshnessTimerId = this.timer.schedule(() => {
      if (!this.state.snapshot || this.isDisposed) {
        return;
      }
      this.updateState({
        ...this.state,
        freshness: classifyFreshness(this.state.snapshot, this.clock.nowMillis()),
      });
      this.scheduleFreshnessBoundary();
    }, delayMillis);
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimerId !== undefined || this.isDisposed) {
      return;
    }
    const delayIndex = Math.min(
      this.reconnectAttempt,
      RECONNECT_DELAYS_MILLIS.length - 1,
    );
    const delayMillis = RECONNECT_DELAYS_MILLIS[delayIndex];
    this.reconnectAttempt += 1;
    this.reconnectTimerId = this.timer.schedule(() => {
      this.reconnectTimerId = undefined;
      void this.resynchronize("RESYNCING");
    }, delayMillis);
  }

  private updateState(state: TwinLiveState): void {
    this.state = state;
    for (const listener of this.listeners) {
      listener(state);
    }
  }

  private closeSocket(): void {
    const activeSocket = this.socket;
    this.socket = undefined;
    activeSocket?.close();
  }

  private cancelTimer(timerId: number | undefined): void {
    if (timerId !== undefined) {
      this.timer.cancel(timerId);
    }
  }

  private isStaleOperation(operationGeneration: number): boolean {
    return this.isDisposed || operationGeneration !== this.generation;
  }
}

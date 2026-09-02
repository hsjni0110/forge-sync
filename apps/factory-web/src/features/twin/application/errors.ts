export type TwinSnapshotFailureKind =
  | "NOT_FOUND"
  | "TEMPORARILY_UNAVAILABLE"
  | "NETWORK"
  | "INVALID_CONTRACT";

export class TwinSnapshotLoadError extends Error {
  constructor(
    readonly kind: TwinSnapshotFailureKind,
    message: string,
    options?: ErrorOptions,
  ) {
    super(message, options);
    this.name = "TwinSnapshotLoadError";
  }
}

export function classifyTwinSnapshotFailure(error: unknown): TwinSnapshotFailureKind {
  return error instanceof TwinSnapshotLoadError ? error.kind : "NETWORK";
}

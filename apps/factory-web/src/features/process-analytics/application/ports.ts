import type { ProcessCursor, RunAnalysis } from "../domain/processAnalysis";

export interface ProcessAnalysisClient {
  analyze(machineId: string, cursor: ProcessCursor, signal: AbortSignal, existing?: ProcessingReferences): Promise<RunAnalysis>;
}

export interface ProcessingReferences {
  processingId: string;
  featureProcessingId: string;
  assessmentProcessingId: string;
}

export class ProcessAnalysisError extends Error {
  constructor(readonly code: "INPUT_NOT_FOUND" | "VERSION_MISMATCH" | "NETWORK") {
    super(code);
  }
}

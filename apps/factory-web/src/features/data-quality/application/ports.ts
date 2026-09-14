import type { DataQualityReport, DataQualityScope } from "../domain/dataQuality";

export interface DataQualityClient {
  load(
    machineId: string,
    scope?: DataQualityScope,
    signal?: AbortSignal,
  ): Promise<DataQualityReport>;
}

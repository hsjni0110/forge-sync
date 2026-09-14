import Ajv2020 from "ajv/dist/2020.js";
import schema from "../../../../../../contracts/production/v1/observed-production-context.schema.json";
import type { ObservedProductionContext } from "../domain/observedProductionContext";

const ajv = new Ajv2020({ allErrors: true, strict: true, strictRequired: false });
ajv.addFormat("date-time", (value: string) => Number.isFinite(Date.parse(value)));
ajv.addFormat("uuid", (value: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value));
const validate = ajv.compile<ObservedProductionContext>(schema);
const browserFetch: typeof fetch = (input, init) => fetch(input, init);

export class HttpObservedProductionContextClient {
  constructor(
    private readonly apiBaseUrl: string,
    private readonly fetcher: typeof fetch = browserFetch,
  ) {}

  async find(machineId: string, machiningRunProcessingRunId: string, signal?: AbortSignal) {
    const query = new URLSearchParams({ machiningRunProcessingRunId, ruleVersion: "1.0.0" });
    const response = await this.fetcher(
      `${this.apiBaseUrl}/api/v1/machines/${encodeURIComponent(machineId)}/production-context?${query}`,
      { signal, headers: { Accept: "application/vnd.forgesync.observed-production-context.v1+json" } },
    );
    if (!response.ok) throw new Error("Production context request failed");
    const document: unknown = await response.json();
    if (
      !validate(document) ||
      document.machineId !== machineId ||
      document.machiningRunProcessingRunId !== machiningRunProcessingRunId
    ) {
      throw new Error("Production context contract mismatch");
    }
    return document;
  }
}

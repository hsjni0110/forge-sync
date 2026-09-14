import Ajv2020 from "ajv/dist/2020.js";
import schema from "../../../../../../contracts/process-analytics/v1/tool-load-trends.schema.json";
import type { ToolLoadTrendReport } from "../domain/toolLoadTrend";

const ajv = new Ajv2020({ allErrors: true, strict: true, strictRequired: false });
ajv.addFormat("date-time", (value: string) => Number.isFinite(Date.parse(value)));
ajv.addFormat("uuid", (value: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value));
const validate = ajv.compile<ToolLoadTrendReport>(schema);
const browserFetch: typeof fetch = (input, init) => fetch(input, init);

export class HttpToolLoadTrendClient {
  constructor(
    private readonly apiBaseUrl: string,
    private readonly fetcher: typeof fetch = browserFetch,
  ) {}

  async find(machineId: string, machiningRunProcessingRunId: string, signal?: AbortSignal) {
    const query = new URLSearchParams({ machiningRunProcessingRunId, policyVersion: "1.0.0" });
    const response = await this.fetcher(
      `${this.apiBaseUrl}/api/v1/machines/${encodeURIComponent(machineId)}/tool-load-trends?${query}`,
      { signal, headers: { Accept: "application/vnd.forgesync.tool-load-trends.v1+json" } },
    );
    if (!response.ok) throw new Error("Tool load trend request failed");
    const document: unknown = await response.json();
    if (
      !validate(document) ||
      document.machineId !== machineId ||
      document.machiningRunProcessingRunId !== machiningRunProcessingRunId
    ) {
      throw new Error("Tool load trend contract mismatch");
    }
    return document;
  }
}

import Ajv2020 from "ajv/dist/2020.js";
import runSchema from "../../../../../../contracts/process-analytics/v1/machining-runs.schema.json";
import featureSchema from "../../../../../../contracts/process-analytics/v1/cycle-features.schema.json";
import assessmentSchema from "../../../../../../contracts/process-analytics/v1/anomaly-assessments.schema.json";
import { ProcessAnalysisError } from "../application/ports";
import type { AssessmentDocument, FeatureDocument, RunDocument } from "./processDocuments";
import { isDecimalMultiple } from "./decimalMultiple";

const ajv = new Ajv2020({ strict: true, allErrors: true });
ajv.removeKeyword("multipleOf");
ajv.addKeyword({ keyword: "multipleOf", type: "number", schemaType: "number", validate: isDecimalMultiple, errors: false });
ajv.addFormat("date-time", { type: "string", validate: (value: string) => Number.isFinite(Date.parse(value)) });
ajv.addFormat("uuid", /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
const runs = ajv.compile<RunDocument>(runSchema);
const features = ajv.compile<FeatureDocument>(featureSchema);
const assessments = ajv.compile<AssessmentDocument>(assessmentSchema);

export function decodeRuns(document: unknown): RunDocument {
  if (!runs(document)) throw new ProcessAnalysisError("VERSION_MISMATCH");
  return document;
}
export function decodeFeatures(document: unknown): FeatureDocument {
  if (!features(document)) throw new ProcessAnalysisError("VERSION_MISMATCH");
  return document;
}
export function decodeAssessments(document: unknown): AssessmentDocument {
  if (!assessments(document)) throw new ProcessAnalysisError("VERSION_MISMATCH");
  return document;
}

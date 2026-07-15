import { readFileSync } from "node:fs";
import { parseArgs } from "node:util";
import { OPERATION_HTTP_BINDINGS } from "generated/petstore/api/client/operationBindings";
import { parseClientResponse } from "generated/petstore/api/client/clientResponse";

const VARIABLE_PATTERN = /\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}/g;
const SERVER_VARIABLE_PATTERN = /^\$\{server\.([a-zA-Z_][a-zA-Z0-9_]*)\}$/;

interface StepResult {
  status: number;
  headers: Record<string, string>;
  responseJson: Record<string, unknown> | null;
  responseBody: string | null;
}

function loadJson(path: string): Record<string, unknown> {
  return JSON.parse(readFileSync(path, "utf-8"));
}

function mergeVariables(
  caseVariables: Record<string, unknown> | undefined,
  serverVariables: Record<string, unknown>,
): Record<string, unknown> {
  const merged = { ...serverVariables };
  if (caseVariables) {
    for (const [key, rawValue] of Object.entries(caseVariables)) {
      if (typeof rawValue === "string") {
        const match = SERVER_VARIABLE_PATTERN.exec(rawValue);
        if (match) {
          const serverKey = match[1];
          if (!(serverKey in serverVariables)) {
            throw new Error(`server variable '${serverKey}' is not available`);
          }
          merged[key] = serverVariables[serverKey];
          continue;
        }
      }
      merged[key] = rawValue;
    }
  }
  return merged;
}

function substituteValue(value: unknown, variables: Record<string, unknown>): unknown {
  if (typeof value === "string") {
    return value.replace(VARIABLE_PATTERN, (_, name: string) => {
      if (!(name in variables)) {
        throw new Error(`unknown variable '${name}'`);
      }
      return String(variables[name]);
    });
  }
  if (Array.isArray(value)) {
    return value.map((item) => substituteValue(item, variables));
  }
  if (value !== null && typeof value === "object") {
    const result: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value)) {
      result[key] = substituteValue(item, variables);
    }
    return result;
  }
  return value;
}

function jsonPointerGet(document: unknown, pointer: string): unknown {
  if (!pointer.startsWith("$.")) {
    throw new Error(`unsupported capture pointer '${pointer}' (expected '$.…')`);
  }
  let current: unknown = document;
  for (const segment of pointer.slice(2).split(".")) {
    if (typeof current !== "object" || current === null) {
      throw new Error(`capture pointer '${pointer}' did not resolve to an object`);
    }
    const obj = current as Record<string, unknown>;
    if (!(segment in obj)) {
      throw new Error(`capture pointer '${pointer}' missing segment '${segment}'`);
    }
    current = obj[segment];
  }
  return current;
}

function matchesExpected(actual: unknown, expected: unknown, path: string): void {
  if (expected !== null && typeof expected === "object") {
    const exp = expected as Record<string, unknown>;
    if ("$type" in exp || "$exists" in exp || "$minLength" in exp) {
      if (exp.$exists === true) {
        return;
      }
      const expectedType = exp.$type;
      if (expectedType === "string") {
        if (typeof actual !== "string") {
          throw new Error(`${path}: expected string, got ${typeof actual}`);
        }
        const minLength = exp.$minLength;
        if (typeof minLength === "number" && (actual as string).length < minLength) {
          throw new Error(`${path}: expected minLength ${minLength}, got ${(actual as string).length}`);
        }
        return;
      }
      if (expectedType === "integer") {
        if (typeof actual !== "number" || !Number.isInteger(actual) || typeof actual === "boolean") {
          throw new Error(`${path}: expected integer, got ${actual}`);
        }
        return;
      }
      if (expectedType === "boolean") {
        if (typeof actual !== "boolean") {
          throw new Error(`${path}: expected boolean, got ${typeof actual}`);
        }
        return;
      }
      throw new Error(`${path}: unsupported matcher ${JSON.stringify(exp)}`);
    }
    if (typeof actual === "object" && actual !== null) {
      const actObj = actual as Record<string, unknown>;
      for (const [key, expectedValue] of Object.entries(exp)) {
        if (!(key in actObj)) {
          throw new Error(`${path}.${key}: missing key in response`);
        }
        matchesExpected(actObj[key], expectedValue, `${path}.${key}`);
      }
      return;
    }
    throw new Error(`${path}: expected object, got ${typeof actual}`);
  }
  if (Array.isArray(expected)) {
    if (!Array.isArray(actual)) {
      throw new Error(`${path}: expected array, got ${typeof actual}`);
    }
    if (actual.length !== expected.length) {
      throw new Error(`${path}: expected array length ${expected.length}, got ${actual.length}`);
    }
    for (let i = 0; i < expected.length; i++) {
      matchesExpected(actual[i], expected[i], `${path}[${i}]`);
    }
    return;
  }
  if (actual !== expected) {
    throw new Error(`${path}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

function headerValue(headers: Record<string, string>, name: string): string | null {
  const lower = name.toLowerCase();
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === lower) {
      return value;
    }
  }
  return null;
}

function operationBinding(operationId: string): (typeof OPERATION_HTTP_BINDINGS)[string] {
  const binding = OPERATION_HTTP_BINDINGS[operationId];
  if (binding === undefined) {
    const known = Object.keys(OPERATION_HTTP_BINDINGS)
      .filter((k) => k[0] >= "A" && k[0] <= "Z")
      .sort()
      .join(", ");
    throw new Error(`unsupported operationId '${operationId}' (known: ${known})`);
  }
  return binding;
}

async function executeRawRequest(baseUrl: string, requestSpec: Record<string, unknown>): Promise<StepResult> {
  const method = requestSpec.method as string;
  const path = requestSpec.path as string;
  const url = `${baseUrl.replace(/\/+$/, "")}${path}`;
  const headers: Record<string, string> = { ...((requestSpec.headers as Record<string, string>) || {}) };
  const jsonBody = requestSpec.json as Record<string, unknown> | undefined;

  if (jsonBody !== undefined && !("content-type" in headers) && !("Content-Type" in headers)) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(url, {
    method,
    headers,
    body: jsonBody ? JSON.stringify(jsonBody) : undefined,
    redirect: "manual",
  });

  const responseText = await response.text();
  let responseJson: Record<string, unknown> | null = null;
  if (responseText) {
    try {
      const parsed = JSON.parse(responseText);
      if (parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)) {
        responseJson = parsed as Record<string, unknown>;
      }
    } catch {
      responseJson = null;
    }
  }

  const responseHeaders: Record<string, string> = {};
  response.headers.forEach((value, key) => {
    responseHeaders[key] = value;
  });

  return {
    status: response.status,
    headers: responseHeaders,
    responseJson,
    responseBody: responseText || null,
  };
}

async function executeClientRequest(baseUrl: string, requestSpec: Record<string, unknown>): Promise<StepResult> {
  const operationId = requestSpec.operationId as string | undefined;
  if (!operationId) {
    throw new Error(`request for ${requestSpec.method} ${requestSpec.path} is missing operationId`);
  }

  const method = requestSpec.method as string;
  const path = requestSpec.path as string;
  const url = `${baseUrl.replace(/\/+$/, "")}${path}`;
  const headers: Record<string, string> = { ...((requestSpec.headers as Record<string, string>) || {}) };
  const jsonBody = requestSpec.json as Record<string, unknown> | undefined;

  if (jsonBody !== undefined && !("content-type" in headers) && !("Content-Type" in headers)) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(url, {
    method,
    headers,
    body: jsonBody ? JSON.stringify(jsonBody) : undefined,
    redirect: "manual",
  });

  const responseText = await response.text();
  const responseHeaders: Record<string, string> = {};
  response.headers.forEach((value, key) => {
    responseHeaders[key] = value;
  });

  const binding = operationBinding(operationId);
  const normalized = {
    status: response.status,
    headers: responseHeaders,
    text: () => Promise.resolve(responseText),
    json: async () => JSON.parse(responseText),
  };

  let responseJson: Record<string, unknown> | null = null;
  try {
    const model = await parseClientResponse(normalized, binding);
    if (model !== null && typeof model === "object") {
      responseJson = model as Record<string, unknown>;
    }
  } catch {
    if (responseText) {
      try {
        const parsed = JSON.parse(responseText);
        if (parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)) {
          responseJson = parsed as Record<string, unknown>;
        }
      } catch {
        responseJson = null;
      }
    }
  }

  return {
    status: response.status,
    headers: responseHeaders,
    responseJson,
    responseBody: responseText || null,
  };
}

function applyCaptures(
  stepId: string,
  step: Record<string, unknown>,
  result: StepResult,
  variables: Record<string, unknown>,
): void {
  const captures = step.capture as Record<string, string> | undefined;
  if (!captures) return;
  for (const [variableName, pointer] of Object.entries(captures)) {
    if (result.responseJson === null) {
      throw new Error(`${stepId}: cannot capture from empty response body`);
    }
    variables[variableName] = jsonPointerGet(result.responseJson, pointer);
  }
}

function assertStepExpectations(
  stepId: string,
  expectSpec: Record<string, unknown>,
  result: StepResult,
  variables: Record<string, unknown>,
): void {
  const expectedStatus = substituteValue(expectSpec.status, variables);
  if (result.status !== expectedStatus) {
    const detail = result.responseBody || result.responseJson;
    throw new Error(`${stepId}: expected status ${expectedStatus}, got ${result.status}: ${detail}`);
  }

  const expectedHeaders = substituteValue(expectSpec.headers || {}, variables);
  if (expectedHeaders !== null && typeof expectedHeaders === "object") {
    for (const [headerName, expectedValue] of Object.entries(expectedHeaders as Record<string, unknown>)) {
      const actualValue = headerValue(result.headers, headerName);
      if (actualValue !== expectedValue) {
        throw new Error(`${stepId}: header ${headerName}: expected ${JSON.stringify(expectedValue)}, got ${JSON.stringify(actualValue)}`);
      }
    }
  }

  if ("json" in expectSpec) {
    if (result.responseJson === null) {
      throw new Error(`${stepId}: expected JSON response body, got none`);
    }
    const expectedJson = substituteValue(expectSpec.json, variables);
    matchesExpected(result.responseJson, expectedJson, `${stepId}.json`);
  }

  if ("body" in expectSpec) {
    const expectedBody = substituteValue(expectSpec.body, variables);
    if (result.responseBody !== expectedBody) {
      throw new Error(`${stepId}: body expected ${JSON.stringify(expectedBody)}, got ${JSON.stringify(result.responseBody)}`);
    }
  }
}

async function runCase(caseFile: string, baseUrl: string, contextFile: string): Promise<void> {
  const caseData = loadJson(caseFile);
  if (caseData.schema !== "smithystache.example.test-case/v1") {
    throw new Error(`unsupported test case schema in ${caseFile}`);
  }

  const context = loadJson(contextFile);
  const serverVariables = (context.variables as Record<string, unknown>) || {};
  const variables = mergeVariables(caseData.variables as Record<string, unknown> | undefined, serverVariables);

  const steps = caseData.steps as Record<string, unknown>[];
  for (let i = 0; i < steps.length; i++) {
    const step = steps[i];
    const stepId = (step.id as string) || `step-${i + 1}`;
    const requestSpec = substituteValue(step.request, variables) as Record<string, unknown>;
    const expectSpec = (step.expect as Record<string, unknown>) || {};

    const transport = (requestSpec.transport as string) || "client";
    let result: StepResult;
    if (transport === "raw") {
      result = await executeRawRequest(baseUrl, requestSpec);
    } else if (transport === "client") {
      result = await executeClientRequest(baseUrl, requestSpec);
    } else {
      throw new Error(`${stepId}: unsupported request transport '${transport}'`);
    }

    applyCaptures(stepId, step, result, variables);
    assertStepExpectations(stepId, expectSpec, result, variables);
  }
}

async function main(): Promise<number> {
  const { values } = parseArgs({
    options: {
      context: { type: "string" },
    },
    allowPositionals: true,
  });

  const caseFile = process.argv[2];
  const baseUrl = process.argv[3];
  const contextFile = values.context;

  if (!caseFile || !baseUrl || !contextFile) {
    console.error("usage: run_case.ts <case-file> <base-url> --context <context-file>");
    return 2;
  }

  try {
    await runCase(caseFile, baseUrl, contextFile);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`${caseFile.split("/").pop()}: ${message}`);
    return 1;
  }
  console.log(`${caseFile.split("/").pop()}: ok`);
  return 0;
}

main()
  .then((exitCode) => process.exit(exitCode))
  .catch((error) => {
    const message = error instanceof Error ? error.message : String(error);
    console.error(message);
    process.exit(1);
  });

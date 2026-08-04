import assert from "node:assert/strict";
import test from "node:test";

import { createApiClients } from "generated/example/authApi/client/clientRegistry";
import type {
  AuthCredential,
  AuthProvider,
} from "generated/example/authApi/client/operationBindings";
import { createApiClients as createQueryClients } from "generated/example/queryAuthApi/client/clientRegistry";

const BEARER = "smithy.api#httpBearerAuth";
const API_KEY = "smithy.api#httpApiKeyAuth";

class Provider implements AuthProvider {
  readonly resolved: string[] = [];

  constructor(private readonly credentials: Record<string, AuthCredential>) {}

  resolveAuth(schemeId: string): AuthCredential | undefined {
    this.resolved.push(schemeId);
    return this.credentials[schemeId];
  }
}

function recordingFetch(): {
  fetchFn: typeof fetch;
  requests: Array<[string, RequestInit | undefined]>;
} {
  const requests: Array<[string, RequestInit | undefined]> = [];
  const fetchFn = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    requests.push([String(input), init]);
    return new Response(JSON.stringify({ authenticated: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };
  return { fetchFn, requests };
}

test("fetch auth applies ordered credentials and preserves modeled bindings", async () => {
  const { fetchFn, requests } = recordingFetch();
  const provider = new Provider({ [API_KEY]: { schemeId: API_KEY, value: "secret" } });
  const clients = createApiClients("https://example.test/", fetchFn, provider);

  await clients.authClient.required("trace-1", "active");

  assert.deepEqual(provider.resolved, [BEARER, API_KEY]);
  assert.equal(requests[0]?.[0], "https://example.test/required?filter=active");
  assert.equal(requests[0]?.[1]?.credentials, "include");
  assert.deepEqual(requests[0]?.[1]?.headers, {
    "X-Trace": "trace-1",
    "X-API-Key": "ApiKey secret",
  });
});

test("cookie auth is browser-managed and includes credentials", async () => {
  const { fetchFn, requests } = recordingFetch();
  const provider = new Provider({});
  const clients = createApiClients("https://example.test", fetchFn, provider);

  await clients.authClient.cookieOnly();

  assert.deepEqual(provider.resolved, []);
  assert.equal(requests[0]?.[1]?.credentials, "include");
  assert.deepEqual(requests[0]?.[1]?.headers, {});
});

test("query API key and bearer credentials are serialized", async () => {
  const query = recordingFetch();
  const queryProvider = new Provider({ [API_KEY]: { schemeId: API_KEY, value: "query secret" } });
  await createQueryClients("https://example.test", query.fetchFn, queryProvider).queryAuthClient.queryApiKey("modeled");
  assert.equal(query.requests[0]?.[0], "https://example.test/query-key?filter=modeled&api_key=query%20secret");

  const bearer = recordingFetch();
  const bearerProvider = new Provider({ [BEARER]: { schemeId: BEARER, value: "token" } });
  await createApiClients("https://example.test", bearer.fetchFn, bearerProvider).authClient.bearerOnly();
  assert.deepEqual(bearer.requests[0]?.[1]?.headers, { Authorization: "Bearer token" });
});

test("missing and invalid credentials fail before fetch while optional and public proceed", async () => {
  const { fetchFn, requests } = recordingFetch();
  const clients = createApiClients("https://example.test", fetchFn);

  await assert.rejects(clients.authClient.bearerOnly(), /No usable authentication credential/);
  assert.equal(requests.length, 0);
  await clients.authClient.optional();
  await clients.authClient.public_();
  assert.equal(requests.length, 2);

  const invalidProvider = new Provider({
    [BEARER]: { schemeId: "example#invalid", value: "token" },
  });
  const invalidClients = createApiClients("https://example.test", fetchFn, invalidProvider);
  await assert.rejects(invalidClients.authClient.bearerOnly(), /not allowed by the operation/);
  assert.equal(requests.length, 2);
});

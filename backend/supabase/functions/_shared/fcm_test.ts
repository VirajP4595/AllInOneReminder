import {
  cachedAccessToken,
  createFcmClient,
  serviceAccountAccessToken,
  type ServiceAccount,
} from "./fcm.ts";
import { assert, assertEquals } from "./testing.ts";

function base64UrlDecode(value: string): Uint8Array {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") +
    "=".repeat((4 - (value.length % 4)) % 4);
  return Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
}

function toPem(label: string, bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  const lines = btoa(binary).match(/.{1,64}/g) ?? [];
  return `-----BEGIN ${label}-----\n${lines.join("\n")}\n-----END ${label}-----`;
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

async function testKeyPair(): Promise<{
  privatePem: string;
  publicBytes: Uint8Array;
}> {
  const pair = await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  ) as CryptoKeyPair;
  const pkcs8 = new Uint8Array(
    await crypto.subtle.exportKey("pkcs8", pair.privateKey),
  );
  const spki = new Uint8Array(
    await crypto.subtle.exportKey("spki", pair.publicKey),
  );
  return { privatePem: toPem("PRIVATE KEY", pkcs8), publicBytes: spki };
}

Deno.test("createFcmClient sends the FCM v1 data message to the injected endpoint", async () => {
  let capturedUrl = "";
  let capturedAuthorization = "";
  let capturedBody = "";
  const mockFetch: typeof fetch = (input, init) => {
    capturedUrl = String(input);
    capturedAuthorization = new Headers(init?.headers).get("Authorization") ?? "";
    capturedBody = String(init?.body ?? "");
    return Promise.resolve(Response.json({ name: "projects/demo/messages/1" }));
  };

  const client = createFcmClient({
    projectId: "demo-project",
    getAccessToken: async () => "access-1",
    fetchImpl: mockFetch,
  });

  const result = await client.send({
    token: "device-token",
    data: {
      type: "missed",
      occurrence_ref: "occ-1",
      profile_ref: "profile-1",
    },
  });

  assertEquals(result, { ok: true });
  assertEquals(
    capturedUrl,
    "https://fcm.googleapis.com/v1/projects/demo-project/messages:send",
  );
  assertEquals(capturedAuthorization, "Bearer access-1");
  assertEquals(JSON.parse(capturedBody), {
    message: {
      token: "device-token",
      data: {
        type: "missed",
        occurrence_ref: "occ-1",
        profile_ref: "profile-1",
      },
    },
  });
});

Deno.test("createFcmClient classifies dead tokens and transient failures", async () => {
  const clientWith = (response: Response | Error) =>
    createFcmClient({
      projectId: "demo-project",
      getAccessToken: async () => "access-1",
      fetchImpl: (() => {
        if (response instanceof Error) return Promise.reject(response);
        return Promise.resolve(response);
      }) as typeof fetch,
    });

  const notFound = await clientWith(new Response("NOT_FOUND", { status: 404 }))
    .send({ token: "dead", data: {} });
  assert(!notFound.ok && notFound.invalidToken, "404 must clear the token");

  const badRequest = await clientWith(
    new Response("INVALID_ARGUMENT", { status: 400 }),
  ).send({ token: "dead", data: {} });
  assert(!badRequest.ok && badRequest.invalidToken, "400 must clear the token");

  const unavailable = await clientWith(
    new Response("UNAVAILABLE", { status: 503 }),
  ).send({ token: "alive", data: {} });
  assert(
    !unavailable.ok && !unavailable.invalidToken,
    "5xx must keep the token for a retry",
  );

  const offline = await clientWith(new Error("network down"))
    .send({ token: "alive", data: {} });
  assert(
    !offline.ok && offline.status === 0 && !offline.invalidToken,
    "network errors are transient",
  );
});

Deno.test("serviceAccountAccessToken signs a verifiable RS256 assertion for the token endpoint", async () => {
  const { privatePem, publicBytes } = await testKeyPair();
  const account: ServiceAccount = {
    project_id: "demo-project",
    client_email: "svc@example.iam.gserviceaccount.com",
    private_key: privatePem,
  };

  let tokenUrl = "";
  let requestBody = "";
  const mockFetch: typeof fetch = (input, init) => {
    tokenUrl = String(input);
    requestBody = String(init?.body ?? "");
    return Promise.resolve(Response.json({ access_token: "ya29.test" }));
  };

  const token = await serviceAccountAccessToken(account, {
    fetchImpl: mockFetch,
    nowSeconds: 1_700_000_000,
  });

  assertEquals(token, "ya29.test");
  assertEquals(tokenUrl, "https://oauth2.googleapis.com/token");

  const form = new URLSearchParams(requestBody);
  assertEquals(
    form.get("grant_type"),
    "urn:ietf:params:oauth:grant-type:jwt-bearer",
  );

  const assertion = form.get("assertion");
  assert(assertion !== null, "the token request must carry an assertion");
  const [header, claims, signature] = assertion!.split(".");
  const verifyKey = await crypto.subtle.importKey(
    "spki",
    toArrayBuffer(publicBytes),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    verifyKey,
    toArrayBuffer(base64UrlDecode(signature)),
    new TextEncoder().encode(`${header}.${claims}`),
  );
  assertEquals(valid, true, "the assertion signature must verify");

  const decodedHeader = JSON.parse(
    new TextDecoder().decode(base64UrlDecode(header)),
  );
  const decodedClaims = JSON.parse(
    new TextDecoder().decode(base64UrlDecode(claims)),
  );
  assertEquals(decodedHeader, { alg: "RS256", typ: "JWT" });
  assertEquals(decodedClaims.iss, account.client_email);
  assertEquals(
    decodedClaims.scope,
    "https://www.googleapis.com/auth/firebase.messaging",
  );
  assertEquals(decodedClaims.aud, "https://oauth2.googleapis.com/token");
  assertEquals(decodedClaims.iat, 1_700_000_000);
  assertEquals(decodedClaims.exp - decodedClaims.iat, 3600);
});

Deno.test("cachedAccessToken reuses an unexpired access token", async () => {
  const { privatePem } = await testKeyPair();
  const account: ServiceAccount = {
    project_id: "demo-project",
    client_email: "svc@example.iam.gserviceaccount.com",
    private_key: privatePem,
  };
  let exchanges = 0;
  const mockFetch: typeof fetch = () => {
    exchanges += 1;
    return Promise.resolve(Response.json({ access_token: `token-${exchanges}` }));
  };
  // The cache uses the real clock, so pin a token exchange that works now.
  const originalFetch = globalThis.fetch;
  globalThis.fetch = mockFetch;
  try {
    const getToken = cachedAccessToken(account);
    assertEquals(await getToken(), "token-1");
    assertEquals(await getToken(), "token-1");
    assertEquals(exchanges, 1, "one exchange covers a warm instance");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

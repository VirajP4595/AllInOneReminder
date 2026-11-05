// FCM HTTP v1 client, with the transport injected so tests can mock the
// endpoint. Nothing here touches health data: callers pass opaque refs only.

export interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

export interface FcmMessage {
  token: string;
  data: Record<string, string>;
}

export type FcmResult =
  | { ok: true }
  | { ok: false; status: number; error: string; invalidToken: boolean };

export interface FcmClient {
  send(message: FcmMessage): Promise<FcmResult>;
}

export interface FcmClientOptions {
  projectId: string;
  getAccessToken: () => Promise<string>;
  fetchImpl?: typeof fetch;
  endpoint?: string;
}

export function createFcmClient(options: FcmClientOptions): FcmClient {
  const doFetch = options.fetchImpl ?? fetch;
  const endpoint =
    options.endpoint ??
    `https://fcm.googleapis.com/v1/projects/${options.projectId}/messages:send`;

  return {
    async send(message: FcmMessage): Promise<FcmResult> {
      try {
        const accessToken = await options.getAccessToken();
        const response = await doFetch(endpoint, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify({
            message: { token: message.token, data: message.data },
          }),
        });

        if (response.ok) {
          return { ok: true };
        }

        const body = await response.text();
        // FCM v1 reports an unregistered token as 404 NOT_FOUND and a malformed
        // token as 400 INVALID_ARGUMENT. Treat both as a dead token so the
        // caller can clear it instead of retrying forever.
        return {
          ok: false,
          status: response.status,
          error: body.slice(0, 500),
          invalidToken: response.status === 400 || response.status === 404,
        };
      } catch (error) {
        // Network errors are transient: keep the token and retry next run.
        return {
          ok: false,
          status: 0,
          error: error instanceof Error ? error.message : String(error),
          invalidToken: false,
        };
      }
    },
  };
}

export interface AccessTokenOptions {
  fetchImpl?: typeof fetch;
  tokenUrl?: string;
  nowSeconds?: number;
}

// Keeps one OAuth2 token per warm Edge Function instance. Google tokens live
// for an hour; refresh a little early to avoid a race at the boundary.
export function cachedAccessToken(
  account: ServiceAccount,
): () => Promise<string> {
  let cached: { value: string; expiresAt: number } | null = null;
  return async (): Promise<string> => {
    if (cached !== null && Date.now() < cached.expiresAt) {
      return cached.value;
    }
    const value = await serviceAccountAccessToken(account);
    cached = { value, expiresAt: Date.now() + 55 * 60 * 1000 };
    return value;
  };
}


// Exchanges a service-account assertion for an OAuth2 access token. The
// assertion is an RS256 JWT signed with WebCrypto and the service account's
// private key. The key is read from the FCM_SERVICE_ACCOUNT secret only and is
// never logged.
export async function serviceAccountAccessToken(
  account: ServiceAccount,
  options: AccessTokenOptions = {},
): Promise<string> {
  const doFetch = options.fetchImpl ?? fetch;
  const tokenUrl = options.tokenUrl ?? "https://oauth2.googleapis.com/token";
  const now = options.nowSeconds ?? Math.floor(Date.now() / 1000);

  const unsigned = `${base64UrlJson({ alg: "RS256", typ: "JWT" })}.${
    base64UrlJson({
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: tokenUrl,
      iat: now,
      exp: now + 3600,
    })
  }`;

  const key = await importPrivateKey(account.private_key);
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      key,
      new TextEncoder().encode(unsigned),
    ),
  );
  const assertion = `${unsigned}.${base64Url(signature)}`;

  const response = await doFetch(tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }).toString(),
  });

  if (!response.ok) {
    throw new Error(`FCM OAuth token exchange failed: HTTP ${response.status}`);
  }

  const json = (await response.json()) as { access_token?: string };
  if (!json.access_token) {
    throw new Error("FCM OAuth token exchange returned no access_token");
  }
  return json.access_token;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const base64 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const raw = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    raw,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function base64UrlJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

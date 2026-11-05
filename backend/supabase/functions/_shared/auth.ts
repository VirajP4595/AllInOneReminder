// Shared-secret check for the cron-invoked Edge Functions (review E1). The functions are deployed
// with verify_jwt = false, because the public anon key must not be enough to trigger them.

/** Constant-time string comparison that does not leak the secret's length through early exit. */
export function timingSafeEqual(a: string, b: string): boolean {
  const encoder = new TextEncoder();
  const left = encoder.encode(a);
  const right = encoder.encode(b);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i++) {
    diff |= (left[i] ?? 0) ^ (right[i] ?? 0);
  }
  return diff === 0;
}

export function isAuthorized(request: Request, cronSecret: string): boolean {
  if (cronSecret.length === 0) return false;
  const supplied = request.headers.get("x-cron-secret") ?? "";
  return timingSafeEqual(supplied, cronSecret);
}

export function unauthorized(): Response {
  return Response.json({ ok: false }, { status: 401 });
}

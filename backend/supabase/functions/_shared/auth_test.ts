import { isAuthorized, timingSafeEqual } from "./auth.ts";
import { assertEquals } from "./testing.ts";

function req(secret?: string): Request {
  return new Request("https://example.test/escalate", {
    method: "POST",
    headers: secret === undefined ? {} : { "x-cron-secret": secret },
  });
}

Deno.test("a matching secret is authorized", () => {
  assertEquals(isAuthorized(req("s3cret"), "s3cret"), true);
});

Deno.test("a missing, wrong or prefix secret is rejected", () => {
  assertEquals(isAuthorized(req(), "s3cret"), false);
  assertEquals(isAuthorized(req("wrong"), "s3cret"), false);
  assertEquals(isAuthorized(req("s3cre"), "s3cret"), false);
  assertEquals(isAuthorized(req("s3cret!"), "s3cret"), false);
});

Deno.test("an unconfigured empty secret never authorizes", () => {
  assertEquals(isAuthorized(req(""), ""), false);
});

Deno.test("timingSafeEqual compares different lengths and contents", () => {
  assertEquals(timingSafeEqual("a", "a"), true);
  assertEquals(timingSafeEqual("a", "b"), false);
  assertEquals(timingSafeEqual("a", "ab"), false);
});

import { FakeEscalationRepo, FakeFcm, FakeHeartbeatRepo } from "./fakes.ts";
import { createEscalateHandler, createHeartbeatHandler } from "./handlers.ts";
import { assertEquals } from "./testing.ts";

const SECRET = "test-cron-secret";
const authed = () =>
  new Request("https://example.test/fn", {
    method: "POST",
    headers: { "x-cron-secret": SECRET },
  });
const anonymous = () => new Request("https://example.test/fn", { method: "POST" });

Deno.test("the escalate handler returns an ok summary", async () => {
  const repo = new FakeEscalationRepo();
  repo.slots = [{
    id: "slot-1",
    household_id: "house-1",
    occurrence_ref: "occ-1",
    profile_ref: "profile-1",
    owner_device_id: "owner",
    due_at: "2026-11-05T09:00:00.000Z",
    grace_minutes: 60,
    done_at: null,
    escalated_at: null,
  }];
  repo.devices = [
    { id: "owner", household_id: "house-1", fcm_token: "owner-token" },
    { id: "caregiver", household_id: "house-1", fcm_token: "caregiver-token" },
  ];
  repo.prefs = [{
    device_id: "caregiver",
    profile_ref: "profile-1",
    mode: "MISSED_ONLY",
  }];

  const response = await createEscalateHandler(
    repo,
    new FakeFcm(),
    SECRET,
    () => new Date("2026-11-05T10:05:00.000Z"),
  )(authed());
  const body = await response.json();

  assertEquals(response.status, 200);
  assertEquals(body, { ok: true, slots: 1, sent: 1, cleared: 0, escalated: 1 });
});

Deno.test("the heartbeat handler returns an ok summary", async () => {
  const repo = new FakeHeartbeatRepo();
  repo.devices = [
    {
      id: "owner",
      household_id: "house-1",
      fcm_token: "owner-token",
      last_heartbeat_at: "2026-11-01T00:00:00.000Z",
      created_at: "2026-10-01T00:00:00.000Z",
    },
    {
      id: "caregiver",
      household_id: "house-1",
      fcm_token: "caregiver-token",
      last_heartbeat_at: "2026-11-05T11:00:00.000Z",
      created_at: "2026-10-01T00:00:00.000Z",
    },
  ];
  repo.slots = [{ owner_device_id: "owner", done_at: null }];

  const response = await createHeartbeatHandler(
    repo,
    new FakeFcm(),
    SECRET,
    () => new Date("2026-11-05T12:00:00.000Z"),
  )(authed());
  const body = await response.json();

  assertEquals(response.status, 200);
  assertEquals(body, { ok: true, silent: 1, alerts: 1, sent: 1, cleared: 0 });
});

Deno.test("the escalate handler returns a generic 500 without leaking details", async () => {
  const repo = new FakeEscalationRepo();
  repo.listOpenSlots = () =>
    Promise.reject(new Error("database password is secret"));
  const response = await createEscalateHandler(repo, new FakeFcm(), SECRET)(authed());
  const body = await response.json();

  assertEquals(response.status, 500);
  assertEquals(body, { ok: false });
});

Deno.test("both handlers return 401 without the cron secret and do no work", async () => {
  const escalationRepo = new FakeEscalationRepo();
  let touched = false;
  escalationRepo.listOpenSlots = () => {
    touched = true;
    return Promise.resolve([]);
  };
  const escalate = await createEscalateHandler(escalationRepo, new FakeFcm(), SECRET)(
    anonymous(),
  );
  assertEquals(escalate.status, 401);
  assertEquals(await escalate.json(), { ok: false });
  assertEquals(touched, false);

  const heartbeatRepo = new FakeHeartbeatRepo();
  heartbeatRepo.listHeartbeatDevices = () => {
    touched = true;
    return Promise.resolve([]);
  };
  const heartbeat = await createHeartbeatHandler(heartbeatRepo, new FakeFcm(), SECRET)(
    new Request("https://example.test/fn", {
      method: "POST",
      headers: { "x-cron-secret": "wrong" },
    }),
  );
  assertEquals(heartbeat.status, 401);
  assertEquals(touched, false);
});

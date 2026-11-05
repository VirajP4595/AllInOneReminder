import type { HeartbeatDevice } from "./heartbeat.ts";
import { runHeartbeat, selectSilentDevices } from "./heartbeat.ts";
import { FakeFcm, FakeHeartbeatRepo } from "./fakes.ts";
import { assert, assertEquals } from "./testing.ts";

function device(
  id: string,
  overrides: Partial<HeartbeatDevice> = {},
): HeartbeatDevice {
  return {
    id,
    household_id: "house-1",
    fcm_token: `token-${id}`,
    last_heartbeat_at: "2026-11-05T11:00:00.000Z",
    created_at: "2026-10-01T00:00:00.000Z",
    ...overrides,
  };
}

const NOW = "2026-11-05T12:00:00.000Z";

Deno.test("selectSilentDevices only selects open-slot owners silent for more than 24 h", () => {
  const devices = [
    device("stale-owner", { last_heartbeat_at: "2026-11-04T11:59:59.000Z" }),
    device("exactly-24h", { last_heartbeat_at: "2026-11-04T12:00:00.000Z" }),
    device("recent-owner", { last_heartbeat_at: "2026-11-05T11:59:00.000Z" }),
    device("stale-non-owner", { last_heartbeat_at: "2026-11-01T00:00:00.000Z" }),
    device("never-heartbeat-old", {
      last_heartbeat_at: null,
      created_at: "2026-11-01T00:00:00.000Z",
    }),
    device("never-heartbeat-new", {
      last_heartbeat_at: null,
      created_at: "2026-11-05T00:00:00.000Z",
    }),
  ];
  const openSlots = [
    { owner_device_id: "stale-owner", done_at: null },
    { owner_device_id: "exactly-24h", done_at: null },
    { owner_device_id: "recent-owner", done_at: null },
    { owner_device_id: "never-heartbeat-old", done_at: null },
    { owner_device_id: "never-heartbeat-new", done_at: null },
    { owner_device_id: "stale-owner", done_at: "2026-11-05T10:00:00.000Z" },
    { owner_device_id: "done-only", done_at: "2026-11-05T10:00:00.000Z" },
  ];

  const silent = selectSilentDevices(
    devices,
    openSlots,
    Date.parse(NOW),
  );

  assertEquals(
    silent.map((item) => item.id),
    ["stale-owner", "never-heartbeat-old"],
  );
});

Deno.test("runHeartbeat alerts the other devices in the household with an opaque payload", async () => {
  const repo = new FakeHeartbeatRepo();
  repo.devices = [
    device("owner", { last_heartbeat_at: "2026-11-01T00:00:00.000Z" }),
    device("caregiver"),
    device("other-house", { household_id: "house-2" }),
  ];
  repo.slots = [{ owner_device_id: "owner", done_at: null }];
  const fcm = new FakeFcm();

  const summary = await runHeartbeat(repo, fcm, new Date(NOW));

  assertEquals(summary, { silent: 1, alerts: 1, sent: 1, cleared: 0 });
  assertEquals(fcm.messages.length, 1);
  assertEquals(fcm.messages[0].token, "token-caregiver");
  assertEquals(fcm.messages[0].data, {
    type: "device_silent",
    device_id: "owner",
  });
  assertEquals(repo.silenceAlerts.length, 1);
});

Deno.test("runHeartbeat alerts at most once per device per 24 h", async () => {
  const repo = new FakeHeartbeatRepo();
  repo.devices = [
    device("owner", { last_heartbeat_at: "2026-11-01T00:00:00.000Z" }),
    device("caregiver"),
  ];
  repo.slots = [{ owner_device_id: "owner", done_at: null }];
  const fcm = new FakeFcm();

  await runHeartbeat(repo, fcm, new Date(NOW));
  await runHeartbeat(repo, fcm, new Date("2026-11-05T23:00:00.000Z"));

  assertEquals(fcm.messages.length, 1, "the second run must be suppressed");
  assertEquals(repo.silenceAlerts.length, 1);

  const nextDay = new Date("2026-11-06T12:00:01.000Z");
  await runHeartbeat(repo, fcm, nextDay);

  assertEquals(fcm.messages.length, 2, "the next 24 h window may alert again");
});

Deno.test("runHeartbeat retries after only transient delivery failures", async () => {
  const repo = new FakeHeartbeatRepo();
  repo.devices = [
    device("owner", { last_heartbeat_at: "2026-11-01T00:00:00.000Z" }),
    device("caregiver"),
  ];
  repo.slots = [{ owner_device_id: "owner", done_at: null }];
  const fcm = new FakeFcm(() => ({
    ok: false,
    status: 503,
    error: "unavailable",
    invalidToken: false,
  }));

  const first = await runHeartbeat(repo, fcm, new Date(NOW));
  assertEquals(first, { silent: 1, alerts: 0, sent: 0, cleared: 0 });
  assertEquals(repo.silenceAlerts.length, 0);

  await runHeartbeat(repo, fcm, new Date("2026-11-05T12:05:00.000Z"));
  assertEquals(fcm.messages.length, 2, "a transient failure is retried");
});

Deno.test("runHeartbeat clears invalid recipient tokens and records the alert", async () => {
  const repo = new FakeHeartbeatRepo();
  repo.devices = [
    device("owner", { last_heartbeat_at: "2026-11-01T00:00:00.000Z" }),
    device("gone"),
  ];
  repo.slots = [{ owner_device_id: "owner", done_at: null }];
  const fcm = new FakeFcm(() => ({
    ok: false,
    status: 404,
    error: "NOT_FOUND",
    invalidToken: true,
  }));

  const summary = await runHeartbeat(repo, fcm, new Date(NOW));

  assertEquals(summary, { silent: 1, alerts: 1, sent: 0, cleared: 1 });
  assertEquals(repo.cleared, ["gone"]);
  assertEquals(repo.silenceAlerts.length, 1);
});

Deno.test("runHeartbeat leaves the alert pending when there is nobody to tell", async () => {
  const repo = new FakeHeartbeatRepo();
  repo.devices = [
    device("owner", { last_heartbeat_at: "2026-11-01T00:00:00.000Z" }),
    device("other-house", { household_id: "house-2" }),
  ];
  repo.slots = [{ owner_device_id: "owner", done_at: null }];
  const fcm = new FakeFcm();

  const summary = await runHeartbeat(repo, fcm, new Date(NOW));

  assertEquals(summary, { silent: 1, alerts: 0, sent: 0, cleared: 0 });
  assertEquals(repo.silenceAlerts.length, 0);
  assert(fcm.messages.length === 0, "no cross-household message may be sent");
});

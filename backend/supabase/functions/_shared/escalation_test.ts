import type {
  Device,
  EscalationSlot,
  ProfilePref,
} from "./escalation.ts";
import {
  isSlotComplete,
  runEscalation,
  selectOverdueSlots,
  selectTargets,
} from "./escalation.ts";
import { FakeEscalationRepo, FakeFcm } from "./fakes.ts";
import { assert, assertEquals } from "./testing.ts";

function slot(overrides: Partial<EscalationSlot> = {}): EscalationSlot {
  return {
    id: "slot-1",
    household_id: "house-1",
    occurrence_ref: "occ-1",
    profile_ref: "profile-1",
    owner_device_id: "owner",
    due_at: "2026-11-05T09:00:00.000Z",
    grace_minutes: 60,
    done_at: null,
    escalated_at: null,
    ...overrides,
  };
}

function device(
  id: string,
  householdId = "house-1",
  token: string | null = `token-${id}`,
): Device {
  return { id, household_id: householdId, fcm_token: token };
}

function pref(deviceId: string, mode: ProfilePref["mode"]): ProfilePref {
  return { device_id: deviceId, profile_ref: "profile-1", mode };
}

Deno.test("selectOverdueSlots enforces the grace window and skips settled slots", () => {
  const due = "2026-11-05T09:00:00.000Z";
  const slots = [
    slot({
      id: "before-grace",
      due_at: "2026-11-05T09:01:00.000Z",
      grace_minutes: 60,
    }),
    slot({
      id: "at-grace",
      due_at: "2026-11-05T09:00:01.000Z",
      grace_minutes: 60,
    }),
    slot({
      id: "after-grace",
      due_at: "2026-11-05T09:00:00.000Z",
      grace_minutes: 60,
    }),
    slot({ id: "done", due_at: due, done_at: "2026-11-05T09:30:00.000Z" }),
    slot({
      id: "already-escalated",
      due_at: due,
      escalated_at: "2026-11-05T10:01:00.000Z",
    }),
  ];

  const overdue = selectOverdueSlots(slots, Date.parse("2026-11-05T10:00:01.000Z"));

  assertEquals(overdue.map((item) => item.id), ["after-grace"]);
});

Deno.test("selectTargets excludes the owner, OFF prefs, missing prefs and empty tokens", () => {
  const devices = [
    device("owner"),
    device("off"),
    device("missing-pref"),
    device("no-token", "house-1", null),
    device("alarm"),
    device("notification"),
    device("missed-only"),
    device("other-house", "house-2"),
  ];
  const prefs = [
    pref("owner", "MISSED_ONLY"),
    pref("off", "OFF"),
    pref("alarm", "ALARM"),
    pref("notification", "NOTIFICATION"),
    pref("missed-only", "MISSED_ONLY"),
    pref("other-house", "ALARM"),
  ];

  const targets = selectTargets(slot(), devices, prefs, []);

  assertEquals(
    targets.map((item) => item.id),
    ["alarm", "notification", "missed-only"],
  );
});

Deno.test("runEscalation sends one opaque payload per eligible device and marks the slot escalated", async () => {
  const repo = new FakeEscalationRepo();
  repo.slots = [slot()];
  repo.devices = [device("owner"), device("alarm"), device("notification")];
  repo.prefs = [pref("alarm", "ALARM"), pref("notification", "NOTIFICATION")];
  const fcm = new FakeFcm();

  const summary = await runEscalation(
    repo,
    fcm,
    new Date("2026-11-05T10:05:00.000Z"),
  );

  assertEquals(summary, { slots: 1, sent: 2, cleared: 0, escalated: 1 });
  assertEquals(fcm.messages.length, 2);
  assertEquals(fcm.messages[0].data, {
    type: "missed",
    occurrence_ref: "occ-1",
    profile_ref: "profile-1",
  });
  assertEquals(
    Object.keys(fcm.messages[0].data).sort(),
    ["occurrence_ref", "profile_ref", "type"],
  );
  assertEquals(
    repo.sends.map((delivery) => delivery.status),
    ["sent", "sent"],
  );
  assertEquals(repo.escalated.length, 1);
});

Deno.test("runEscalation is idempotent when the job runs again", async () => {
  const repo = new FakeEscalationRepo();
  repo.slots = [slot()];
  repo.devices = [device("owner"), device("alarm")];
  repo.prefs = [pref("alarm", "ALARM")];
  const fcm = new FakeFcm();

  await runEscalation(repo, fcm, new Date("2026-11-05T10:05:00.000Z"));
  await runEscalation(repo, fcm, new Date("2026-11-05T10:10:00.000Z"));

  assertEquals(fcm.messages.length, 1, "a re-run must not double-send");
  assertEquals(repo.escalated.length, 1);
});

Deno.test("a transient FCM failure is retried only for the device that failed", async () => {
  const repo = new FakeEscalationRepo();
  repo.slots = [slot()];
  repo.devices = [device("flaky"), device("steady")];
  repo.prefs = [pref("flaky", "ALARM"), pref("steady", "ALARM")];

  let flakyAttempts = 0;
  const fcm = new FakeFcm((message) => {
    if (message.token === "token-flaky" && flakyAttempts === 0) {
      flakyAttempts += 1;
      return {
        ok: false,
        status: 503,
        error: "unavailable",
        invalidToken: false,
      };
    }
    return { ok: true };
  });

  const first = await runEscalation(
    repo,
    fcm,
    new Date("2026-11-05T10:05:00.000Z"),
  );
  assertEquals(first, { slots: 1, sent: 1, cleared: 0, escalated: 0 });
  assertEquals(repo.escalated.length, 0, "a partial failure stays pending");

  const second = await runEscalation(
    repo,
    fcm,
    new Date("2026-11-05T10:10:00.000Z"),
  );
  assertEquals(second, { slots: 1, sent: 1, cleared: 0, escalated: 1 });
  assertEquals(fcm.messages.length, 3, "only the failed device is retried");
  assertEquals(
    fcm.messages.map((message) => message.token),
    ["token-flaky", "token-steady", "token-flaky"],
  );
});

Deno.test("an invalid token is cleared and the slot still escalates", async () => {
  const repo = new FakeEscalationRepo();
  repo.slots = [slot()];
  repo.devices = [device("gone")];
  repo.prefs = [pref("gone", "MISSED_ONLY")];
  const fcm = new FakeFcm(() => ({
    ok: false,
    status: 404,
    error: "NOT_FOUND",
    invalidToken: true,
  }));

  const summary = await runEscalation(
    repo,
    fcm,
    new Date("2026-11-05T10:05:00.000Z"),
  );

  assertEquals(summary, { slots: 1, sent: 0, cleared: 1, escalated: 1 });
  assertEquals(repo.cleared, ["gone"]);
  assertEquals(repo.sends, [
    { slot_id: "slot-1", device_id: "gone", status: "invalid_token" },
  ]);

  await runEscalation(repo, fcm, new Date("2026-11-05T10:10:00.000Z"));
  assertEquals(fcm.messages.length, 1, "a cleared token is not retried");
});

Deno.test("a slot with every pref OFF is escalated without sending", async () => {
  const repo = new FakeEscalationRepo();
  repo.slots = [slot()];
  repo.devices = [device("off")];
  repo.prefs = [pref("off", "OFF")];
  const fcm = new FakeFcm();

  const summary = await runEscalation(
    repo,
    fcm,
    new Date("2026-11-05T10:05:00.000Z"),
  );

  assertEquals(summary, { slots: 1, sent: 0, cleared: 0, escalated: 1 });
  assertEquals(fcm.messages.length, 0);
  assertEquals(repo.sends.length, 0);
});

Deno.test("isSlotComplete requires a terminal row for every eligible device", () => {
  const devices = [device("a"), device("b")];
  const prefs = [pref("a", "ALARM"), pref("b", "ALARM")];

  assert(
    !isSlotComplete(slot(), devices, prefs, [
      { slot_id: "slot-1", device_id: "a", status: "sent" },
    ]),
    "one missing device must keep the slot open",
  );
  assert(
    isSlotComplete(slot(), devices, prefs, [
      { slot_id: "slot-1", device_id: "a", status: "sent" },
      { slot_id: "slot-1", device_id: "b", status: "invalid_token" },
    ]),
    "sent and invalid_token are terminal states",
  );
});

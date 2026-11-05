// Pure escalation planning plus a thin orchestration loop. No Deno APIs and no
// network calls here, so the rules are fully testable with fakes.

import type { FcmClient } from "./fcm.ts";

export type NotificationMode =
  | "ALARM"
  | "NOTIFICATION"
  | "MISSED_ONLY"
  | "OFF";

export interface EscalationSlot {
  id: string;
  household_id: string;
  occurrence_ref: string;
  profile_ref: string;
  owner_device_id: string;
  due_at: string;
  grace_minutes: number;
  done_at: string | null;
  escalated_at: string | null;
}

export interface Device {
  id: string;
  household_id: string;
  fcm_token: string | null;
}

export interface ProfilePref {
  device_id: string;
  profile_ref: string;
  mode: NotificationMode;
}

export type DeliveryStatus = "sent" | "invalid_token";

export interface Delivery {
  slot_id: string;
  device_id: string;
  status: DeliveryStatus;
}

export interface EscalationRepo {
  listOpenSlots(): Promise<EscalationSlot[]>;
  listDevices(householdIds: string[]): Promise<Device[]>;
  listPrefs(deviceIds: string[]): Promise<ProfilePref[]>;
  listSends(slotIds: string[]): Promise<Delivery[]>;
  recordDelivery(delivery: Delivery): Promise<void>;
  clearFcmToken(deviceId: string): Promise<void>;
  markEscalated(slotId: string, at: string): Promise<void>;
}

export interface EscalationSummary {
  slots: number;
  sent: number;
  cleared: number;
  escalated: number;
}

const GRACE_MS_PER_MINUTE = 60_000;

export function selectOverdueSlots(
  slots: EscalationSlot[],
  nowMs: number,
): EscalationSlot[] {
  return slots.filter((slot) => {
    if (slot.done_at !== null || slot.escalated_at !== null) {
      return false;
    }
    const dueMs = Date.parse(slot.due_at);
    if (Number.isNaN(dueMs)) {
      return false;
    }
    return nowMs > dueMs + slot.grace_minutes * GRACE_MS_PER_MINUTE;
  });
}

function isTerminal(delivery: Delivery): boolean {
  return delivery.status === "sent" || delivery.status === "invalid_token";
}

function modeByDevice(
  prefs: ProfilePref[],
  profileRef: string,
): Map<string, NotificationMode> {
  const modes = new Map<string, NotificationMode>();
  for (const pref of prefs) {
    if (pref.profile_ref === profileRef) {
      modes.set(pref.device_id, pref.mode);
    }
  }
  return modes;
}

function eligibleDevices(
  slot: EscalationSlot,
  devices: Device[],
  prefs: ProfilePref[],
): Device[] {
  const modes = modeByDevice(prefs, slot.profile_ref);
  return devices.filter((device) => {
    if (device.household_id !== slot.household_id) return false;
    if (device.id === slot.owner_device_id) return false;
    if (!device.fcm_token) return false;
    const mode = modes.get(device.id);
    // A device with no pref for this profile has not opted in, so it is not a
    // target. OFF is an explicit opt-out.
    return mode !== undefined && mode !== "OFF";
  });
}

export function selectTargets(
  slot: EscalationSlot,
  devices: Device[],
  prefs: ProfilePref[],
  deliveries: Delivery[],
): Device[] {
  const delivered = new Set(
    deliveries
      .filter((delivery) => delivery.slot_id === slot.id && isTerminal(delivery))
      .map((delivery) => delivery.device_id),
  );
  return eligibleDevices(slot, devices, prefs).filter(
    (device) => !delivered.has(device.id),
  );
}

export function isSlotComplete(
  slot: EscalationSlot,
  devices: Device[],
  prefs: ProfilePref[],
  deliveries: Delivery[],
): boolean {
  const delivered = new Set(
    deliveries
      .filter((delivery) => delivery.slot_id === slot.id && isTerminal(delivery))
      .map((delivery) => delivery.device_id),
  );
  return eligibleDevices(slot, devices, prefs).every((device) =>
    delivered.has(device.id)
  );
}

export async function runEscalation(
  repo: EscalationRepo,
  fcm: FcmClient,
  now: Date = new Date(),
): Promise<EscalationSummary> {
  const overdue = selectOverdueSlots(await repo.listOpenSlots(), now.getTime());
  const summary: EscalationSummary = {
    slots: overdue.length,
    sent: 0,
    cleared: 0,
    escalated: 0,
  };
  if (overdue.length === 0) {
    return summary;
  }

  const householdIds = [...new Set(overdue.map((slot) => slot.household_id))];
  const devices = await repo.listDevices(householdIds);
  const deviceIds = devices.map((device) => device.id);
  const prefs = deviceIds.length > 0 ? await repo.listPrefs(deviceIds) : [];
  const slotIds = overdue.map((slot) => slot.id);
  const sends = await repo.listSends(slotIds);

  for (const slot of overdue) {
    const targets = selectTargets(slot, devices, prefs, sends);

    for (const device of targets) {
      const result = await fcm.send({
        token: device.fcm_token as string,
        data: {
          type: "missed",
          occurrence_ref: slot.occurrence_ref,
          profile_ref: slot.profile_ref,
        },
      });

      if (result.ok) {
        const delivery: Delivery = {
          slot_id: slot.id,
          device_id: device.id,
          status: "sent",
        };
        await repo.recordDelivery(delivery);
        sends.push(delivery);
        summary.sent += 1;
      } else if (result.invalidToken) {
        await repo.clearFcmToken(device.id);
        const delivery: Delivery = {
          slot_id: slot.id,
          device_id: device.id,
          status: "invalid_token",
        };
        await repo.recordDelivery(delivery);
        sends.push(delivery);
        summary.cleared += 1;
      }
      // Transient failures (429/5xx/network) write nothing; the next run sees
      // the device as still pending and retries it, while already-sent devices
      // stay terminal and are not contacted twice.
    }

    if (isSlotComplete(slot, devices, prefs, sends)) {
      await repo.markEscalated(slot.id, now.toISOString());
      summary.escalated += 1;
    }
  }

  return summary;
}

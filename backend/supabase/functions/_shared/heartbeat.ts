// Pure heartbeat rules plus orchestration. A device "owns" an open slot when it
// is the slot's owner_device_id. Only its household's other devices are
// notified, at most once per 24 h.

import type { FcmClient } from "./fcm.ts";

export interface HeartbeatDevice {
  id: string;
  household_id: string;
  fcm_token: string | null;
  last_heartbeat_at: string | null;
  created_at: string;
}

export interface OpenSlot {
  owner_device_id: string;
  done_at: string | null;
}

export interface SilenceAlert {
  device_id: string;
  last_alert_at: string;
}

export interface HeartbeatRepo {
  listHeartbeatDevices(): Promise<HeartbeatDevice[]>;
  listOpenSlots(): Promise<OpenSlot[]>;
  listSilenceAlerts(deviceIds: string[]): Promise<SilenceAlert[]>;
  recordSilenceAlert(deviceId: string, at: string): Promise<void>;
  clearFcmToken(deviceId: string): Promise<void>;
}

export interface HeartbeatSummary {
  silent: number;
  alerts: number;
  sent: number;
  cleared: number;
}

const SILENCE_MS = 24 * 60 * 60 * 1000;

function lastSeenMs(device: HeartbeatDevice): number {
  return Date.parse(device.last_heartbeat_at ?? device.created_at);
}

export function selectSilentDevices(
  devices: HeartbeatDevice[],
  openSlots: OpenSlot[],
  nowMs: number,
): HeartbeatDevice[] {
  const owners = new Set(
    openSlots
      .filter((slot) => slot.done_at === null)
      .map((slot) => slot.owner_device_id),
  );
  const cutoff = nowMs - SILENCE_MS;
  return devices.filter((device) => {
    if (!owners.has(device.id)) return false;
    const seen = lastSeenMs(device);
    return !Number.isNaN(seen) && seen < cutoff;
  });
}

function alertedRecently(
  alerts: Map<string, SilenceAlert>,
  deviceId: string,
  nowMs: number,
): boolean {
  const alert = alerts.get(deviceId);
  if (!alert) return false;
  const last = Date.parse(alert.last_alert_at);
  return !Number.isNaN(last) && nowMs - last < SILENCE_MS;
}

export async function runHeartbeat(
  repo: HeartbeatRepo,
  fcm: FcmClient,
  now: Date = new Date(),
): Promise<HeartbeatSummary> {
  const nowMs = now.getTime();
  const devices = await repo.listHeartbeatDevices();
  const slots = await repo.listOpenSlots();
  const silent = selectSilentDevices(devices, slots, nowMs);
  const summary: HeartbeatSummary = {
    silent: silent.length,
    alerts: 0,
    sent: 0,
    cleared: 0,
  };
  if (silent.length === 0) {
    return summary;
  }

  const alerts = new Map(
    (await repo.listSilenceAlerts(silent.map((device) => device.id))).map(
      (alert) => [alert.device_id, alert],
    ),
  );

  for (const device of silent) {
    if (alertedRecently(alerts, device.id, nowMs)) {
      continue;
    }

    const targets = devices.filter((candidate) =>
      candidate.household_id === device.household_id &&
      candidate.id !== device.id &&
      Boolean(candidate.fcm_token)
    );

    let terminal = 0;
    for (const target of targets) {
      const result = await fcm.send({
        token: target.fcm_token as string,
        data: { type: "device_silent", device_id: device.id },
      });
      if (result.ok) {
        summary.sent += 1;
        terminal += 1;
      } else if (result.invalidToken) {
        await repo.clearFcmToken(target.id);
        summary.cleared += 1;
        terminal += 1;
      }
      // 429/5xx/network: nothing terminal, so this device is retried next run.
    }

    // No recipients means no alert yet: keep it pending so a device added
    // later still gets the warning. At least one terminal send records the
    // 24 h window and guarantees we never alert twice in a day.
    if (targets.length > 0 && terminal > 0) {
      const at = now.toISOString();
      await repo.recordSilenceAlert(device.id, at);
      alerts.set(device.id, { device_id: device.id, last_alert_at: at });
      summary.alerts += 1;
    }
  }

  return summary;
}

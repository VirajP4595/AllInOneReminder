import type { FcmClient, FcmMessage, FcmResult } from "./fcm.ts";
import type {
  Delivery,
  Device,
  EscalationRepo,
  EscalationSlot,
  ProfilePref,
} from "./escalation.ts";
import type {
  HeartbeatDevice,
  HeartbeatRepo,
  OpenSlot,
  SilenceAlert,
} from "./heartbeat.ts";

export class FakeEscalationRepo implements EscalationRepo {
  slots: EscalationSlot[] = [];
  devices: Device[] = [];
  prefs: ProfilePref[] = [];
  sends: Delivery[] = [];
  escalated: Array<{ slotId: string; at: string }> = [];
  cleared: string[] = [];

  async listOpenSlots(): Promise<EscalationSlot[]> {
    return this.slots.filter((slot) =>
      slot.done_at === null && slot.escalated_at === null
    );
  }

  async listDevices(householdIds: string[]): Promise<Device[]> {
    return this.devices.filter((device) =>
      householdIds.includes(device.household_id)
    );
  }

  async listPrefs(deviceIds: string[]): Promise<ProfilePref[]> {
    return this.prefs.filter((pref) => deviceIds.includes(pref.device_id));
  }

  async listSends(slotIds: string[]): Promise<Delivery[]> {
    return this.sends.filter((delivery) => slotIds.includes(delivery.slot_id));
  }

  async recordDelivery(delivery: Delivery): Promise<void> {
    this.sends.push(delivery);
  }

  async clearFcmToken(deviceId: string): Promise<void> {
    this.cleared.push(deviceId);
    const device = this.devices.find((item) => item.id === deviceId);
    if (device) device.fcm_token = null;
  }

  async markEscalated(slotId: string, at: string): Promise<void> {
    const slot = this.slots.find((item) => item.id === slotId);
    if (slot) slot.escalated_at = at;
    this.escalated.push({ slotId, at });
  }
}

export class FakeHeartbeatRepo implements HeartbeatRepo {
  devices: HeartbeatDevice[] = [];
  slots: OpenSlot[] = [];
  silenceAlerts: SilenceAlert[] = [];
  cleared: string[] = [];

  async listHeartbeatDevices(): Promise<HeartbeatDevice[]> {
    return this.devices;
  }

  async listOpenSlots(): Promise<OpenSlot[]> {
    return this.slots;
  }

  async listSilenceAlerts(deviceIds: string[]): Promise<SilenceAlert[]> {
    return this.silenceAlerts.filter((alert) =>
      deviceIds.includes(alert.device_id)
    );
  }

  async recordSilenceAlert(deviceId: string, at: string): Promise<void> {
    this.silenceAlerts = this.silenceAlerts.filter((alert) =>
      alert.device_id !== deviceId
    );
    this.silenceAlerts.push({ device_id: deviceId, last_alert_at: at });
  }

  async clearFcmToken(deviceId: string): Promise<void> {
    this.cleared.push(deviceId);
    const device = this.devices.find((item) => item.id === deviceId);
    if (device) device.fcm_token = null;
  }
}

export class FakeFcm implements FcmClient {
  messages: FcmMessage[] = [];
  private readonly result: (message: FcmMessage, index: number) => FcmResult;

  constructor(
    result: (message: FcmMessage, index: number) => FcmResult = () => ({
      ok: true,
    }),
  ) {
    this.result = result;
  }

  async send(message: FcmMessage): Promise<FcmResult> {
    this.messages.push(message);
    return this.result(message, this.messages.length - 1);
  }
}

// Minimal PostgREST client for the service-role Edge Functions. Only opaque
// refs, tokens and timestamps are read or written here.

import type {
  Delivery,
  Device,
  EscalationSlot,
  ProfilePref,
} from "./escalation.ts";
import type {
  HeartbeatDevice,
  OpenSlot,
  SilenceAlert,
} from "./heartbeat.ts";

function inFilter(values: string[]): string {
  return values.map((value) => encodeURIComponent(value)).join(",");
}

export class SupabaseRepo {
  private readonly restUrl: string;
  private readonly headers: Record<string, string>;

  constructor(url: string, serviceRoleKey: string) {
    this.restUrl = `${url.replace(/\/$/, "")}/rest/v1`;
    this.headers = {
      apikey: serviceRoleKey,
      Authorization: `Bearer ${serviceRoleKey}`,
      "Content-Type": "application/json",
    };
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`${this.restUrl}/${path}`, {
      ...init,
      headers: { ...this.headers, ...(init.headers ?? {}) },
    });
    if (!response.ok) {
      throw new Error(
        `Supabase REST ${init.method ?? "GET"} ${path} failed: HTTP ${response.status}`,
      );
    }
    if (response.status === 204) {
      return undefined as T;
    }
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  async listOpenSlots(): Promise<EscalationSlot[]> {
    return await this.request<EscalationSlot[]>(
      "escalation_slots?select=id,household_id,occurrence_ref,profile_ref," +
        "owner_device_id,due_at,grace_minutes,done_at,escalated_at" +
        "&done_at=is.null&escalated_at=is.null",
    );
  }

  async listDevices(householdIds: string[]): Promise<Device[]> {
    if (householdIds.length === 0) return [];
    return await this.request<Device[]>(
      `devices?select=id,household_id,fcm_token&household_id=in.(${
        inFilter(householdIds)
      })`,
    );
  }

  async listPrefs(deviceIds: string[]): Promise<ProfilePref[]> {
    if (deviceIds.length === 0) return [];
    return await this.request<ProfilePref[]>(
      `device_profile_prefs?select=device_id,profile_ref,mode` +
        `&device_id=in.(${inFilter(deviceIds)})`,
    );
  }

  async listSends(slotIds: string[]): Promise<Delivery[]> {
    if (slotIds.length === 0) return [];
    return await this.request<Delivery[]>(
      `escalation_sends?select=slot_id,device_id,status&slot_id=in.(${
        inFilter(slotIds)
      })`,
    );
  }

  async recordDelivery(delivery: Delivery): Promise<void> {
    await this.request("escalation_sends?on_conflict=slot_id,device_id", {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify(delivery),
    });
  }

  async clearFcmToken(deviceId: string): Promise<void> {
    await this.request(`devices?id=eq.${encodeURIComponent(deviceId)}`, {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({ fcm_token: null }),
    });
  }

  async markEscalated(slotId: string, at: string): Promise<void> {
    await this.request(
      `escalation_slots?id=eq.${encodeURIComponent(slotId)}&escalated_at=is.null`,
      {
        method: "PATCH",
        headers: { Prefer: "return=minimal" },
        body: JSON.stringify({ escalated_at: at }),
      },
    );
  }

  async listHeartbeatDevices(): Promise<HeartbeatDevice[]> {
    const owners = await this.request<OpenSlot[]>(
      "escalation_slots?select=owner_device_id,done_at&done_at=is.null",
    );
    const ids = [...new Set(owners.map((slot) => slot.owner_device_id))];
    if (ids.length === 0) return [];
    return await this.request<HeartbeatDevice[]>(
      `devices?select=id,household_id,fcm_token,last_heartbeat_at,created_at` +
        `&id=in.(${inFilter(ids)})`,
    );
  }

  async listSilenceAlerts(deviceIds: string[]): Promise<SilenceAlert[]> {
    if (deviceIds.length === 0) return [];
    return await this.request<SilenceAlert[]>(
      `device_silence_alerts?select=device_id,last_alert_at` +
        `&device_id=in.(${inFilter(deviceIds)})`,
    );
  }

  async recordSilenceAlert(deviceId: string, at: string): Promise<void> {
    await this.request("device_silence_alerts?on_conflict=device_id", {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify({ device_id: deviceId, last_alert_at: at }),
    });
  }
}

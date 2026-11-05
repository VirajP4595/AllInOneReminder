// Minimal PostgREST client for the service-role Edge Functions. Only opaque
// refs, tokens and timestamps are read or written here.

import type {
  Delivery,
  Device,
  EscalationSlot,
  ProfilePref,
} from "./escalation.ts";
import { chunk, fetchAllPages } from "./paging.ts";
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

  /** Reads every row of a list query, one capped page at a time, in a stable order. */
  private async all<T>(path: string, order: string): Promise<T[]> {
    return await fetchAllPages<T>((offset, limit) =>
      this.request<T[]>(`${path}&order=${order}&limit=${limit}&offset=${offset}`)
    );
  }

  /** Same, for `in.(...)` filters: ids are chunked so the URL stays short. */
  private async allIn<T>(
    base: string,
    column: string,
    ids: string[],
    order: string,
  ): Promise<T[]> {
    const out: T[] = [];
    for (const group of chunk(ids, 50)) {
      out.push(...await this.all<T>(`${base}&${column}=in.(${inFilter(group)})`, order));
    }
    return out;
  }

  async listOpenSlots(): Promise<EscalationSlot[]> {
    return await this.all<EscalationSlot>(
      "escalation_slots?select=id,household_id,occurrence_ref,profile_ref," +
        "owner_device_id,due_at,grace_minutes,done_at,escalated_at" +
        "&done_at=is.null&escalated_at=is.null",
      "id",
    );
  }

  async listDevices(householdIds: string[]): Promise<Device[]> {
    if (householdIds.length === 0) return [];
    return await this.allIn<Device>(
      "devices?select=id,household_id,fcm_token",
      "household_id",
      householdIds,
      "id",
    );
  }

  async listPrefs(deviceIds: string[]): Promise<ProfilePref[]> {
    if (deviceIds.length === 0) return [];
    return await this.allIn<ProfilePref>(
      "device_profile_prefs?select=device_id,profile_ref,mode",
      "device_id",
      deviceIds,
      "device_id,profile_ref",
    );
  }

  async listSends(slotIds: string[]): Promise<Delivery[]> {
    if (slotIds.length === 0) return [];
    return await this.allIn<Delivery>(
      "escalation_sends?select=slot_id,device_id,status",
      "slot_id",
      slotIds,
      "slot_id,device_id",
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
    const owners = await this.all<OpenSlot>(
      "escalation_slots?select=id,owner_device_id,done_at&done_at=is.null",
      "id",
    );
    const ids = [...new Set(owners.map((slot) => slot.owner_device_id))];
    if (ids.length === 0) return [];
    return await this.allIn<HeartbeatDevice>(
      "devices?select=id,household_id,fcm_token,last_heartbeat_at,created_at",
      "id",
      ids,
      "id",
    );
  }

  async listSilenceAlerts(deviceIds: string[]): Promise<SilenceAlert[]> {
    if (deviceIds.length === 0) return [];
    return await this.allIn<SilenceAlert>(
      "device_silence_alerts?select=device_id,last_alert_at",
      "device_id",
      deviceIds,
      "device_id",
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

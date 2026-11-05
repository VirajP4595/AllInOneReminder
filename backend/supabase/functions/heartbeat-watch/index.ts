// Heartbeat watch job. Invoked by pg_cron every 5 minutes. Alerts the other
// devices in a household when a device that owns open escalation slots has not
// checked in for more than 24 hours, at most once per device per day.

import {
  cachedAccessToken,
  createFcmClient,
  type ServiceAccount,
} from "../_shared/fcm.ts";
import { createHeartbeatHandler } from "../_shared/handlers.ts";
import { SupabaseRepo } from "../_shared/repo.ts";

function required(name: string): string {
  const value = Deno.env.get(name);
  if (!value) {
    throw new Error(`${name} is not configured`);
  }
  return value;
}

function serviceAccount(): ServiceAccount {
  return JSON.parse(required("FCM_SERVICE_ACCOUNT")) as ServiceAccount;
}

const account = serviceAccount();
const repo = new SupabaseRepo(
  required("SUPABASE_URL"),
  required("SUPABASE_SERVICE_ROLE_KEY"),
);
const fcm = createFcmClient({
  projectId: account.project_id,
  getAccessToken: cachedAccessToken(account),
});

Deno.serve(createHeartbeatHandler(repo, fcm, required("CRON_SECRET")));

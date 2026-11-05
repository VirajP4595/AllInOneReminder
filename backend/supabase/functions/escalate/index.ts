// Missed-dose escalation job. Invoked by pg_cron through /functions/v1/escalate
// every 5 minutes. The service account is read from the FCM_SERVICE_ACCOUNT
// Supabase secret only; it is never written to the repo or to logs.

import {
  cachedAccessToken,
  createFcmClient,
  type ServiceAccount,
} from "../_shared/fcm.ts";
import { createEscalateHandler } from "../_shared/handlers.ts";
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

Deno.serve(createEscalateHandler(repo, fcm, required("CRON_SECRET")));

// HTTP handlers are factories so the Deno tests can drive the real flow with a
// fake repository and a mocked FCM endpoint. They never log payload contents.

import type { FcmClient } from "./fcm.ts";
import { runEscalation, type EscalationRepo } from "./escalation.ts";
import { runHeartbeat, type HeartbeatRepo } from "./heartbeat.ts";

function failure(error: unknown): Response {
  console.error(
    "edge_function_failed",
    error instanceof Error ? error.name : "unknown",
  );
  return Response.json({ ok: false }, { status: 500 });
}

export function createEscalateHandler(
  repo: EscalationRepo,
  fcm: FcmClient,
  clock: () => Date = () => new Date(),
): () => Promise<Response> {
  return async (): Promise<Response> => {
    try {
      const summary = await runEscalation(repo, fcm, clock());
      return Response.json({ ok: true, ...summary });
    } catch (error) {
      return failure(error);
    }
  };
}

export function createHeartbeatHandler(
  repo: HeartbeatRepo,
  fcm: FcmClient,
  clock: () => Date = () => new Date(),
): () => Promise<Response> {
  return async (): Promise<Response> => {
    try {
      const summary = await runHeartbeat(repo, fcm, clock());
      return Response.json({ ok: true, ...summary });
    } catch (error) {
      return failure(error);
    }
  };
}

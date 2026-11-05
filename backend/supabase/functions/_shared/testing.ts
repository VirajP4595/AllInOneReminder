// Tiny assertion helpers so the Edge Function tests have no external deps.

export function assertEquals<T>(
  actual: T,
  expected: T,
  message = "values differ",
): void {
  const actualJson = JSON.stringify(actual);
  const expectedJson = JSON.stringify(expected);
  if (actualJson !== expectedJson) {
    throw new Error(`${message}\n  actual:   ${actualJson}\n  expected: ${expectedJson}`);
  }
}

export function assert(condition: unknown, message = "assertion failed"): void {
  if (!condition) {
    throw new Error(message);
  }
}

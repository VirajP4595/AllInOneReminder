import { chunk, fetchAllPages } from "./paging.ts";
import { assertEquals } from "./testing.ts";

function source(total: number) {
  const calls: number[] = [];
  const fetchPage = (offset: number, limit: number) => {
    calls.push(offset);
    const rows: number[] = [];
    for (let i = offset; i < Math.min(offset + limit, total); i++) rows.push(i);
    return Promise.resolve(rows);
  };
  return { fetchPage, calls };
}

Deno.test("paging returns every row across the page boundary", async () => {
  const s = source(2500);
  const rows = await fetchAllPages(s.fetchPage, 1000);
  assertEquals(rows.length, 2500);
  assertEquals(s.calls, [0, 1000, 2000]);
});

Deno.test("an exact multiple of the page size needs one extra empty page", async () => {
  const s = source(2000);
  const rows = await fetchAllPages(s.fetchPage, 1000);
  assertEquals(rows.length, 2000);
  assertEquals(s.calls, [0, 1000, 2000]);
});

Deno.test("an empty result returns an empty list after one request", async () => {
  const s = source(0);
  assertEquals(await fetchAllPages(s.fetchPage, 1000), []);
  assertEquals(s.calls, [0]);
});

Deno.test("chunk splits a list into bounded groups", () => {
  assertEquals(chunk([1, 2, 3, 4, 5], 2), [[1, 2], [3, 4], [5]]);
  assertEquals(chunk([], 2), []);
});

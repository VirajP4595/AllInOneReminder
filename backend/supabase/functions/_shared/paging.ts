// PostgREST caps every response (max_rows, 1000 by default). Reading page by page, and failing
// loudly if a page is full of nothing new, keeps a health alert from silently ignoring rows (E2).

export const PAGE_SIZE = 1000;
const MAX_PAGES = 1000;

export async function fetchAllPages<T>(
  fetchPage: (offset: number, limit: number) => Promise<T[]>,
  pageSize: number = PAGE_SIZE,
): Promise<T[]> {
  const all: T[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const rows = await fetchPage(page * pageSize, pageSize);
    all.push(...rows);
    if (rows.length < pageSize) return all;
  }
  throw new Error("paging_limit_exceeded");
}

/** Splits long id lists so `in.(...)` filters stay well inside URL length limits. */
export function chunk<T>(values: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < values.length; i += size) out.push(values.slice(i, i + size));
  return out;
}

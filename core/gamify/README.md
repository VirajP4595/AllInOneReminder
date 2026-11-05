# core-gamify

Pure Kotlin/JVM gamification engine. No Android, no networking, no clock access: `Gamification.compute`
is a deterministic function of occurrence history, a timezone and optional daily steps. This folder is
a standalone Gradle build; it will be folded into the root build after Phase 1 merges.

## What it does

- **Points ledger:** one entry per occurrence id, sorted by occurrence id.
  - Habit items (`WATER_DRINK`, `EXERCISE`, `HABIT`, `FOOD`, `SUPPLEMENT`): **10 pts** when TAKEN
    inside ±30 minutes of `dueAt`, **5 pts** when later but inside the item's grace, otherwise **0**.
  - Medicines and injections (`MEDICINE`, `INJECTION`): **5 pts** only inside ±30 minutes; **0**
    otherwise, even inside grace.
  - `DUPLICATE_ACTION`, skipped, missed, pending and not-yet-acted records earn **0**. Appointments
    and custom items are not gamified.
- **Per-item streaks:** consecutive local days on which every occurrence of the item is TAKEN.
  One automatic freeze per ISO week per item covers one missed day. A doctor-stopped skip is neutral.
- **Daily rings:** Meds / Hydration / Movement percentages of that day's occurrences completed on
  time, grouped by local date in the supplied `ZoneId`.
- **Badges:** 7/30/100-day streaks (per item), Hydration hero (7 consecutive days at 100% hydration),
  Perfect week (all rings at 100% for 7 days), and 10k steps from the optional steps input.

## Build and test

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification coverageSummary
```

CI: `.github/workflows/core-gamify.yml`. The build fails if line coverage is below 90% and prints
`LINE_COVERAGE=xx.xx%` from the JaCoCo XML.

## Library versions

- Kotlin 2.4.21
- JUnit Jupiter 6.1.3
- JaCoCo 0.8.16
- Gradle 8.14.3 (wrapper)

## Decisions

- **History is the only state.** There are no mutable counters and no stored progress; the ledger,
  streaks, rings and badges are recomputed from the supplied occurrences. Track B may cache the
  result, but the cache can always be discarded.
- **One ledger entry per occurrence.** Multiple action records for the same `occurrenceId` collapse
  to a single entry; the earliest TAKEN wins, so an append-only action log cannot double-count.
- **Freeze semantics.** The first broken day in an ISO week consumes that week's freeze and leaves
  the current streak intact; a second broken day in the same week resets it. A neutral day (all
  doctor-stopped skips) neither extends nor breaks the streak.
- **Calendar gaps are unknown, not failures.** A day with no occurrence for an item is not treated
  as a miss, because the module only sees history that exists.
- **An empty ring is 100%.** A category with no occurrences has nothing to complete, so its percent
  is 100. This avoids a red ring on a day with no medicines or no exercise.
- **Window edges are inclusive.** Exactly ±30 minutes is on time and the last minute of grace still
  earns the late habit points. Negative grace is clamped to zero, so it can never create a window.
- **Badges use the longest streak.** A badge is not revoked by a later break; it records that the
  milestone happened. Hydration and perfect-week runs must be on consecutive local dates.
- **Steps are optional.** The 10k badge only appears when the P3-B4 steps input is supplied; an
  absent input is not a failure.
- **Criticality is accepted but not scored.** `criticality` is part of the input contract and of
  the UI, but no points/streak/ring/badge rule varies by it. Safety comes from timing, not from
  rewarding critical items more.
- **DST is handled by the zone, not by 24-hour arithmetic.** Rings and streaks group by
  `dueAt.atZone(zone).toLocalDate()`, so a 23-hour or 25-hour day stays one local date. Tests cover
  Europe/London and America/New_York spring-forward and fall-back days.
- **Performance target.** A one-year history for 20 items (7,300 occurrences) computes in under
  200 ms on the JVM; this is asserted by `PerformanceTest`.

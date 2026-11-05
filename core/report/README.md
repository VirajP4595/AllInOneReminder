# core-report

Pure Kotlin/JVM doctor-report model. It turns plain input data classes into a deterministic
`DoctorReport` for one profile and date range. No Android, no networking, no PDF rendering (that is
P4-B3) and no dependency on another module's build. This folder is a standalone Gradle build.

## What it does

- **Profile basics and allergies** from `ReportProfile`.
- **Active medicines** (medicine and injection items, `active = true`) with plain-English schedule
  text, for example `09:00; 15 min before breakfast; with lunch`.
- **Adherence per medicine:** taken / (taken + missed + skipped), rounded to a whole percent. Pending
  and duplicate-action rows are ignored; no resolved doses gives `null`, not 0%.
- **Missed doses** per medicine, sorted by due time and occurrence id.
- **Daily adherence:** one row per local date in the range, including days with no doses. Rows are
  grouped by `dueAt.atZone(zoneId).toLocalDate()`, so DST days stay one date.
- **Vitals summaries** through the small `VitalsSummaryProvider` interface; the report sorts them by
  kind and label and never imports `/core/vitals`.
- **Past and upcoming appointments** within the range, split at the supplied `now`. Upcoming is
  soonest first (an appointment exactly at `now` is upcoming); past is newest first.

## Build and test

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification coverageSummary
```

CI: `.github/workflows/core-report.yml`. The build fails if line coverage is below 90% and prints
`LINE_COVERAGE=xx.xx%` from the JaCoCo XML.

## Library versions

- Kotlin 2.4.21
- JUnit Jupiter 6.1.3
- JaCoCo 0.8.16
- Gradle 8.14.3 (wrapper)

## Decisions

- **Deterministic by construction.** Medicines are sorted by name then id, schedules by fixed time
  then meal/relation/offset, missed doses by due time then id, vitals by kind then label, upcoming
  appointments ascending and past appointments descending. The same input always gives the same
  report, regardless of list order.
- **Input-only clock.** `DoctorReportInput.now` is both the split point for past/upcoming
  appointments and `DoctorReport.generatedAt`; the builder never reads a clock.
- **Range is local, not UTC.** An occurrence or appointment belongs to the report when its local date
  in the supplied zone falls inside `ReportRange`. This keeps a 90-day report and DST boundaries
  intuitive for the person reading it.
- **Only active medicines are reported.** Historical occurrences for an inactive item are not counted
  in adherence, so a stopped medicine does not dominate the report.
- **Pending is not a miss.** Adherence uses resolved actions only, and a medicine with no resolved
  dose reports `null` percent so the PDF can print "no data" rather than 0%.
- **The daily table includes empty days.** A doctor can see the whole date range, and empty days have
  `percent = null`.
- **Vitals stay decoupled.** `VitalsSummaryProvider` receives the profile id, range and zone; Track B
  can bridge it to `/core/vitals` without this module depending on that build.
- **No interpretation.** The report describes counts and timestamps; it does not advise, rank or add
  normal ranges.

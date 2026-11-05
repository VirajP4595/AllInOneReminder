# core-vitals

Pure Kotlin/JVM vital-sign model: types, unit conversions, plausibility validation, trend summaries
and user-target range checks. No Android, no networking, no clock access. This folder is a standalone
Gradle build; it will be folded into the root build after Phase 1 merges.

## What it does

- **Types:** blood pressure (systolic/diastolic and optional pulse, mmHg), glucose (mg/dL with
  fasting / post-meal / random context), weight (kg), SpO2 (%), temperature (°C), pulse (bpm) and
  custom (name + unit + value).
- **Conversions:** glucose mg/dL ↔ mmol/L (factor `18.0156`), weight kg ↔ lb, temperature °C ↔ °F,
  with round trips inside 0.1 of the original unit.
- **Validation:** wide plausibility ranges that reject typos; they are not medical "normal" ranges.
- **Trends:** rolling 7/30/90-day `count`, `min`, `average`, `max` per vital kind and field. A reading
  exactly on the cutoff is included; future readings are ignored; empty input returns `null`.
- **Targets:** `TargetChecks.evaluate` flags `BELOW` / `WITHIN` / `ABOVE` only against a range the user
  entered. There are no default ranges and no interpretation of a reading.

## Plausibility ranges

| Reading | Accepted range |
|---|---|
| Blood pressure systolic | 50–260 mmHg |
| Blood pressure diastolic | 30–160 mmHg |
| Blood pressure / standalone pulse | 20–300 bpm |
| Glucose | 20–600 mg/dL |
| Weight | 1–400 kg |
| SpO2 | 50–100 % |
| Temperature | 30–45 °C |

Systolic must also be greater than diastolic, which rejects a reversed cuff entry. Custom readings
require a non-blank name and unit and a finite value. All numeric readings reject NaN and infinity.

## Build and test

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification coverageSummary
```

CI: `.github/workflows/core-vitals.yml`. The build fails if line coverage is below 90% and prints
`LINE_COVERAGE=xx.xx%` from the JaCoCo XML.

## Library versions

- Kotlin 2.4.21
- JUnit Jupiter 6.1.3
- JaCoCo 0.8.16
- Gradle 8.14.3 (wrapper)

## Decisions

- **Canonical storage units.** `VitalReading` stores mg/dL, kg, °C, %, bpm and mmHg. UI units are
  converted at the edge by `UnitConversion` or the `VitalReading` factories, so trends and reports
  never have to guess a unit.
- **Bad input is data, not a crash.** Constructors accept any number and `VitalValidation` returns
  field-level errors, so the entry screen can show inline messages instead of throwing.
- **Validation is not diagnosis.** The limits are deliberately wide typo guards (for example a
  temperature of 3 °C or 300 °C) and must never be presented as normal/abnormal advice.
- **No default targets.** `TargetChecks` returns an empty list when the user has not entered a target
  for that kind, and ignores targets for other kinds.
- **Blood pressure can be targeted per component.** `UserTarget.field` selects systolic, diastolic or
  pulse; a missing pulse produces no pulse check rather than a false flag.
- **Trends are instant-based.** Windows are `now - N days` through `now`, inclusive at both ends. The
  module never reads the clock; Track B passes `now`. Per-day, timezone-aware statistics belong to
  `/core/report`, which has its own inputs.
- **Deterministic output.** Trend summaries depend only on the supplied readings and `now`; target
  checks are sorted by field and label.

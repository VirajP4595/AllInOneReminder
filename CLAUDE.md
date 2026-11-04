# Agent guide for this repo

- The spec is `docs/PLAN.md`. For Phase 1, §12–§19 are binding; do not build Phase 2–4 features.
- Work milestone by milestone (§18), one PR each, on branches `p1/m<N>-<name>`. Never push to `main`; never merge your own PR.
- Hard rules: $0 services only; **no `INTERNET` permission in Phase 1**; no analytics/crash/ads SDKs; never log medicine names, doses, keys or passphrases; never skip or weaken tests.
- `/core/schedule` is pure Kotlin with no Android imports and no direct clock access. Write it test-first.
- All alarm (re)scheduling goes through `AlarmCoordinator` only.
- Record every deviation from the plan in `docs/DECISIONS.md`.
- Before opening a PR, run: `./gradlew lint test assembleDebug`.
- Use the PR template in `.github/pull_request_template.md`.

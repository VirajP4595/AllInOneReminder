# Delegation prompt: Phase 1 build

Copy everything below the line into the implementing agent.

---

You are implementing **Phase 1 (offline MVP)** of an Android app, "All-in-One Reminder", in the GitHub repo `VirajP4595/AllInOneReminder`. It is a family health reminder app: medicines, injections, water, food, exercise and appointments, with **full-screen alarms** that must ring reliably on a locked phone.

## Source of truth
Read `docs/PLAN.md` in full before writing code. It is on the `main` branch, or on `claude/great-goldberg-dn0f1n` if `main` doesn't have it yet; base your work on whichever branch has it.
- **§12–§19 are your binding spec** for Phase 1.
- §0–§11 are context. Do **not** build anything from Phases 2–4.
- If the spec is ambiguous, choose the safer behaviour for medication (never silently drop, duplicate or shift a dose), record the decision in `docs/DECISIONS.md`, and list it under "Open questions" in the PR description.
- Do not change product decisions in §0. If one blocks you, stop and ask.

## How to work
1. Deliver milestones **M0 → M6 from §18, in order, one pull request per milestone**.
   - Branch names: `p1/m0-scaffold`, `p1/m1-data`, `p1/m2-schedule`, `p1/m3-alarm`, `p1/m4-ui`, `p1/m5-meal-flows`, `p1/m6-backup-release`.
   - Each branch builds on the previous one; stack them if earlier PRs aren't merged yet, and say so in the PR.
2. Each PR must meet the §18 acceptance criteria and the Definition of Done. CI must be green before you open the next milestone.
3. Write `/core/schedule` test-first. It is the highest-risk logic after the alarm path. All tests listed in §15 are mandatory.
4. Before marking M3 done, check every item in the §19 review checklist that applies to it.
5. You cannot test on a physical phone. Say so honestly in each PR, and put device steps in `docs/TESTING.md` for the owner to run. Use Robolectric/unit tests for logic; use an emulator job in CI only if it works reliably, and don't block on it.

## Hard constraints
- **$0 cost.** No paid services, no cards, no Play Store.
- **No `INTERNET` permission and no network code in Phase 1.** No analytics, crash reporting, ads, or any SDK that sends data off the device.
- Health data stays encrypted at rest (SQLCipher). Never log medicine names, doses, keys or passphrases.
- `minSdk 30`, latest stable `targetSdk`. Kotlin + Jetpack Compose + Hilt + Room. Use `java.time` only.
- Use the latest stable library versions at build time, pinned in the version catalog. Verify APIs against current Android documentation instead of relying on memory, especially exact alarms, full-screen intent and foreground-service type rules for the target SDK.
- Never weaken, skip or delete a test to make CI pass.
- Keep commits small, with clear messages. Do not push to `main` directly. Do not merge your own PRs.

## Each PR description must contain
- **Summary** of what was built, mapped to the spec sections
- **How it was tested** (commands, test names, results)
- **Screenshots** (UI PRs: Compose preview renders are fine)
- **Deviations** (link to `docs/DECISIONS.md` entries)
- **Known gaps / open questions**
- **Device test steps** the owner should run (link to `docs/TESTING.md` sections)

## When you're finished with M6
- A tagged release `v0.1.0` with an installable APK attached.
- Post a final summary listing every PR, the remaining known gaps, and the §19 checklist with each item marked as met (with evidence) or not met (with the reason).

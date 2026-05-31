# CyanBridge — Branch Reconciliation Audit

**Document type:** documentation-only audit (no code changed, no build run, no commit/push)
**Branch:** `ai/claude-v1-branch-reconciliation-audit`
**Date:** 2026-05-31
**Author:** Claude Code (senior Android engineer / technical architect)

> Goal: audit the active CyanBridge working branches and produce a merge plan that keeps
> CyanBridge **one app made of weakly-coupled feature modules** (Intent-action routing,
> no cross-module compile dependencies, no ADB-only standalone modules).

---

## 0. Repository facts established for this audit

- Git root: `…/Alternative-HeyCyan-codex-native-automation-review` (modules live under `android/CyanBridge/`).
- Current integration base branch = **`ai/claude-feature-integration-shell`** (tip `7380ef8`).
  The audit branch HEAD currently points at the same commit.
- The two un-integrated feature branches (`photo-question-openrouter-direct`,
  `ai-user-shell-v1-3`) both branch from **`c9326e7`** — i.e. *before* the gated-translation
  commit `2cab6df` and *before* the V1 project-map docs (`eee3b42` / `7380ef8`).
- `git diff --check` → clean (no whitespace/conflict markers).
- ⚠️ Working tree contains two **untracked / git-ignored** module directories that are NOT
  part of the integration branch: `android/CyanBridge/ai-user-shell/` and
  `android/CyanBridge/photo-question-tools/` (both report `!!` under `git status --ignored`).
  They are leftovers from a previous checkout of the feature branches. See Risk R6.

---

## 1. Current branch landscape

| Branch | Latest important commit | Purpose | Feature / module | Phone-tested | Build-tested | Merge into feature-integration-shell? | Risk |
|---|---|---|---|---|---|---|---|
| `ai/claude-feature-integration-shell` | `7380ef8` Merge V1 project map and test matrix | **Integration base.** Tools/Diagnostics shell + gated translation + docs | `ToolsActivity`, `FeatureIntents`, all diagnostics modules, `:conversation-translation` (gated) | ❌ deferred (APK `/root/cyanbridge-feature-integration-shell.apk` built, not yet phone-tested) | ✅ APK built | — (this **is** the base) | Low–medium (untested APK) |
| `ai/claude-conversation-translation-mlkit` | `2618f2b` Merge conversation translation UX fixes | On-device dialog translation (SpeechRecognizer→ML Kit→TTS) | `:conversation-translation` | ✅ user-confirmed "всё работает" (RU↔EN, TTS, UX) | ✅ | **No** (already integrated as gated `implementation`) | Low |
| `ai/claude-headset-button-diagnostic` | `6afad27` Merge headset button diagnostic review fixes | Diagnostic: standard Android media/headset key events | `:headset-button-tools` | ⚠️ tested — no events arrived on glasses | ✅ | **No** (already integrated, debug-only) | Low |
| `ai/claude-glasses-button-event-diagnostic` | `b8a5a02` Merge glasses button event diagnostic review fixes | Read-only logcat tail of `DeviceNotify` BLE frames | `:glasses-button-event-tools` | ⚠️ tested — no useful events on phone | ✅ | **No** (already integrated, debug-only) | Low |
| `ai/claude-photo-question-openrouter-direct` | `b543da1` Merge OpenRouter direct photo question review fixes | "Фото и вопрос": pick photo + question → OpenRouter answer | `:photo-question-tools` (+ card in `ToolsActivity`) | ⚠️ "в целом работает", UX issues (save unclear) | ✅ (per project memory) | **Later** (rebase first; clean module, but `debugImplementation` + pre-gated build.gradle) | Medium |
| `ai/stable-v1-2-translation-photo-openrouter` | `b543da1` (same tip as photo-question) | Stability marker pointing at the photo-question tip | same as photo-question | n/a | n/a | **No** (alias/marker) | Low |
| `ai/claude-ai-user-shell-v1-3` | `707b926` Harden AI user shell intent boundaries | User-facing "AI-функции" shell routing to translation + photo | `:ai-user-shell` (+ carries all photo-question commits) | ❌ not confirmed | partial | **Later / extract ideas only** — introduces a *second* navigation shell | High |
| `ai/codex-review-*` | — | Codex review/hardening branches already merged into their feature tips | — | — | — | **No** (already folded into feature merges) | Low |
| `ai/codex-fix-*`, `ai/claude-native-automation-mvp`, `ai/claude-tasker-replacement-audit`, etc. | — | Older build-fix / earlier-direction branches (pre-shell baseline) | — | — | — | **No** (historical baseline) | Low |

---

## 2. What is already integrated into `ai/claude-feature-integration-shell`

Verified by reading the tracked tree at HEAD (`git ls-tree`, `git show HEAD:…`).

| Item | Present on integration base? | Evidence |
|---|---|---|
| `:conversation-translation` module | ✅ yes — **gated `implementation`** | `settings.gradle.kts` (conditional `include`), `app/build.gradle:189` `implementation project(":conversation-translation")` |
| `:headset-button-tools` module | ✅ yes — `debugImplementation` | `settings.gradle.kts`, `app/build.gradle:202` |
| `:glasses-button-event-tools` module | ✅ yes — `debugImplementation` | `settings.gradle.kts`, `app/build.gradle:215` |
| `:phone-test-tools`, `:debug-log-tools`, `:runtime-diagnostics-tools` | ✅ yes — `debugImplementation` | `settings.gradle.kts`, `app/build.gradle:155/165/175` |
| Feature actions (Intent contract) | ✅ yes — 6 actions | `FeatureIntents.kt`: CONVERSATION_TRANSLATION, HEADSET_BUTTON_DIAGNOSTIC, GLASSES_BUTTON_EVENT_DIAGNOSTIC, DEBUG_LOGS, RUNTIME_DIAGNOSTICS, PHONE_TEST_TOOLS |
| `ToolsActivity` (Tools/Diagnostics shell) | ✅ yes — `exported="false"` | `app/src/main/java/.../ui/tools/ToolsActivity.kt`; manifest line 124–125 |
| `FeatureIntents` | ✅ yes | `app/src/main/java/.../ui/tools/FeatureIntents.kt` |
| Project map / test matrix / roadmap docs | ✅ yes | `CYANBRIDGE_V1_PROJECT_MAP.md`, `CYANBRIDGE_V1_TEST_MATRIX.md`, `CYANBRIDGE_V1_ROADMAP.md` (merged via `7380ef8`) |
| Loose-coupling guarantees | ✅ holds | `ToolsActivity` opens modules via `Intent(action).setPackage(packageName)` + `resolveActivity` guard; no module Activity classes imported by core; module Activities `exported=false` |

**Conclusion:** the integration base already contains the full diagnostics suite + the gated
translation feature + the V1 documentation set, all behind the loosely-coupled Intent-action shell.

---

## 3. What is NOT yet integrated

### 3.1 `:photo-question-tools` (from `ai/claude-photo-question-openrouter-direct`)
Files added relative to integration base (`git diff --name-only feature-integration-shell..photo-question`):
- `photo-question-tools/` module: `PhotoQuestionActivity.kt`, `OpenRouterClient.kt`,
  `PhotoQuestionResponder.kt`, `PhotoQuestionSettings.kt`, `build.gradle`, `AndroidManifest.xml`
- `settings.gradle.kts`: adds conditional `include(":photo-question-tools")`
- `FeatureIntents.kt`: **+1 action** `PHOTO_QUESTION = "com.fersaiyan.cyanbridge.feature.PHOTO_QUESTION"`
- `ToolsActivity.kt`: **+1 card** routing to PHOTO_QUESTION
- `app/build.gradle`: `debugImplementation project(":photo-question-tools")`

**Overlap analysis:** ✅ **Good shape.** Photo-question *extends the existing `ToolsActivity`* via
a new action + card. It does **not** introduce a separate shell. The module is self-contained
(no dependency on `:app`, glasses SDK, BLE, or `:conversation-translation`).

**Blocking detail:** the branch's `app/build.gradle` still has
`debugImplementation project(":conversation-translation")` (pre-gating), because it branched at
`c9326e7`, before `2cab6df`. A naïve merge will conflict on `app/build.gradle` and could
**revert the gated-translation change**. Must rebase/merge carefully.

### 3.2 `:ai-user-shell` (from `ai/claude-ai-user-shell-v1-3`)
Adds, on top of everything in 3.1:
- `ai-user-shell/` module: `AiUserShellActivity.kt`, `FeatureActions.kt`, `build.gradle`, `AndroidManifest.xml`
- `SettingsActivity.kt` + `activity_settings.xml`: new entry point to the AI shell
- `app/build.gradle`: `debugImplementation project(":ai-user-shell")`

**Overlap analysis:** ⚠️ **This is a second navigation shell.** `AiUserShellActivity` ("AI-функции")
renders its own cards ("Переводчик", "Фото и вопрос", "История запросов" disabled, "Настройки AI"
disabled) and routes via `FeatureActions.kt` — which **deliberately duplicates the action string
constants** already in `FeatureIntents.kt`. So it overlaps `ToolsActivity` in *function* (routes to
the same translation + photo-question features) but is a distinct user-facing surface.

The architectural idea is reasonable (a clean **user-facing** AI section vs. a **debug** diagnostics
section), but as shipped it: (a) duplicates routing logic, (b) is `debugImplementation` so it would
**not exist in a release build**, and (c) carries the same pre-gated `build.gradle` regression risk.

---

## 4. Recommended merge strategy

1. **Keep `ai/claude-feature-integration-shell` as the single integration base.** It already
   owns the loosely-coupled shell, the gated translation, the diagnostics suite, and the V1 docs.
2. **Do NOT merge `ai/claude-ai-user-shell-v1-3` blindly.** It would add a second navigation shell
   that duplicates `ToolsActivity` routing and re-declares the action strings. Merging as-is risks
   two competing "where do users find AI features" surfaces.
3. **Extract the useful ideas from `ai-user-shell`, not the module wholesale:**
   - the *concept* of a clean user-facing AI section separate from the debug diagnostics shell;
   - the Settings entry point pattern;
   - the placeholder cards for "История запросов" / "Настройки AI".
   Re-implement them on top of the current `FeatureIntents` contract (single source of truth) rather
   than via a duplicated `FeatureActions.kt`.
4. **Bring `:photo-question-tools` into the integrated shell *later*, as a proper card/page** — it is
   the cleanest of the un-integrated work and already plugs into `ToolsActivity` + `FeatureIntents`.
   Sequence: rebase the photo-question branch onto the current integration base **first** (so the
   gated-translation `build.gradle` is preserved), resolve the `app/build.gradle` /
   `FeatureIntents.kt` / `ToolsActivity.kt` overlaps, then merge.
5. **Keep all diagnostics modules `debugImplementation`** (phone-test, debug-log, runtime-diagnostics,
   headset-button, glasses-button). They are testing tools, not user features.
6. **Keep `:conversation-translation` as gated `implementation`** (commit `2cab6df`). Any merge that
   touches `app/build.gradle` must NOT regress this back to `debugImplementation`.
7. **Decide photo-question's delivery channel before merge:** if "Фото и вопрос" is a user feature it
   must move from `debugImplementation` to a gated `implementation` (like translation); if it stays a
   tester tool for now, keep it debug-only and document that it is intentionally release-hidden.

---

## 5. Risk list

| # | Risk | Where it shows up | Severity |
|---|---|---|---|
| R1 | **Duplicate navigation shells** | `ai-user-shell` `AiUserShellActivity` duplicates `ToolsActivity` routing; `FeatureActions.kt` duplicates `FeatureIntents.kt` action strings | High |
| R2 | **`debugImplementation` hides user-facing features in release** | `:photo-question-tools` and `:ai-user-shell` are `debugImplementation` — "Фото и вопрос" / "AI-функции" would be absent from a release build | High |
| R3 | **Gated-translation regression on merge** | photo-question & ai-user-shell branches predate `2cab6df`; their `app/build.gradle` still has `debugImplementation project(":conversation-translation")` → merge conflict / silent revert | High |
| R4 | **Direct Activity class dependencies** | Not present today (Intent-action routing holds). Must stay this way when merging new modules — do not import module Activities into `:app` | Medium |
| R5 | **`exported=true` risk** | Core launcher + a VIEW/BROWSABLE deep-link activity are `exported=true` (expected); all feature/module/`ToolsActivity` are `exported=false`. New modules must keep their Activities `exported=false` | Medium |
| R6 | **`.gitignore` hiding source / stale untracked module dirs** | `ai-user-shell/` and `photo-question-tools/` sit in the working tree as **ignored/untracked** on the integration branch (leftover checkout). `.gitignore` also blanket-ignores `*.json` (could hide module/test resources) and an experiment file `AutoPairManager.kt` | Medium |
| R7 | **Action-string mismatch** | `ai-user-shell` copies action constants by hand; if the canonical `FeatureIntents`/module manifest strings ever change, the duplicated copies drift and cards silently fall back to "Модуль не включён" | Medium |
| R8 | **Untested feature-integration APK** | `/root/cyanbridge-feature-integration-shell.apk` built but phone-test deferred; integration base not yet validated on-device | Medium |

---

## 6. Next recommended sequence

1. **Phone-test the current feature-integration-shell APK** (`/root/cyanbridge-feature-integration-shell.apk`):
   verify Settings → "Инструменты / Диагностика" → all 6 cards open / degrade correctly, and that
   the gated translation works in this build.
2. **If successful, formally make `ai/claude-feature-integration-shell` the main integration base** and
   tag/snapshot it.
3. **Audit the photo-question OpenRouter branch in detail** (`OpenRouterClient`, `PhotoQuestionSettings`
   — API-key storage/masking, model selection, save UX) before integrating.
4. **Design "Фото и вопрос" as a weakly-coupled feature inside the Tools/AI section** — add the
   `PHOTO_QUESTION` card to the existing `ToolsActivity`/`FeatureIntents`, rebased on the gated base,
   and decide gated `implementation` vs debug-only delivery (Risk R2/R7).
5. **Only then implement history / settings / model-picker** (the deferred `ai-user-shell` placeholders)
   on top of the single `FeatureIntents` contract — do not resurrect the duplicate shell.
6. **Keep the SDK/BLE button bridge as a separate read-only investigation** — neither headset nor
   glasses diagnostics produced usable Android key events; a dedicated SDK/BLE-notify bridge study is a
   later, independent track.

---

## 7. Commands used / suggested (all safe, read-only)

```bash
# Branch & history overview
git branch --all
git log --oneline --decorate --graph --all --max-count=40

# What differs between integration base and a feature branch
git diff --name-only ai/claude-feature-integration-shell..ai/claude-photo-question-openrouter-direct
git diff --name-only ai/claude-feature-integration-shell..ai/claude-ai-user-shell-v1-3
git log  --oneline       ai/claude-feature-integration-shell..ai/claude-ai-user-shell-v1-3

# Common ancestor (confirms branches predate the gated-translation commit)
git merge-base ai/claude-feature-integration-shell ai/claude-photo-question-openrouter-direct
git merge-base ai/claude-feature-integration-shell ai/claude-ai-user-shell-v1-3

# Inspect tracked content of a branch without checking it out
git ls-tree -r --name-only ai/claude-feature-integration-shell android/CyanBridge/app
git show ai/claude-feature-integration-shell:android/CyanBridge/settings.gradle.kts
git show ai/claude-photo-question-openrouter-direct:android/CyanBridge/app/build.gradle

# Targeted file diffs (build wiring, intent contract, shell)
git diff ai/claude-feature-integration-shell..ai/claude-photo-question-openrouter-direct -- \
  android/CyanBridge/app/build.gradle \
  android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/ui/tools/FeatureIntents.kt

# Hygiene checks
git status --porcelain --ignored android/CyanBridge/ai-user-shell android/CyanBridge/photo-question-tools
git diff --check
```

> **Suggested before any future merge** (do NOT run as part of this audit):
> `git merge --no-commit --no-ff <branch>` to preview conflicts in `app/build.gradle` /
> `FeatureIntents.kt` / `ToolsActivity.kt`, then `git merge --abort`.

---

## Summary

- **Audit document created:** `CYANBRIDGE_BRANCH_RECONCILIATION_AUDIT.md` (this file). No code changed,
  no Gradle run, no commit, no push; `git diff --check` clean.
- **Key recommendation:** keep one app + one shell. Integrate `:photo-question-tools` *into the existing
  `ToolsActivity`/`FeatureIntents` contract* after rebasing onto the gated base; do **not** merge
  `:ai-user-shell` wholesale (it duplicates the shell and routing) — extract its ideas instead. Watch the
  three high risks: duplicate shells (R1), release-hidden user features via `debugImplementation` (R2),
  and gated-translation regression on merge (R3).
- **Integration base:** `ai/claude-feature-integration-shell` (tip `7380ef8`).
- **Branches needing further audit before merge:** `ai/claude-photo-question-openrouter-direct`
  (API-key handling, save UX, gated-vs-debug delivery) and `ai/claude-ai-user-shell-v1-3`
  (shell duplication / action-string drift). Validate the integration-shell APK on-device first.

# CyanBridge V1 — Roadmap

> Documentation only. No app behaviour, flags, or modules are changed by this file.
> Date: 2026-05-31 · Branch: `ai/claude-v1-project-map-test-matrix`

## Completed

- **Translation V1.1** (`:conversation-translation`, `2618f2b`) — on-device dialog
  translation (ML Kit + TTS). RU→EN, EN→RU, TTS, UX-fix. **Phone-tested, works.**
  No Hermes, no OpenRouter.
- **Headset/media button diagnostic** (`:headset-button-tools`, `6afad27`) — implemented and
  reviewed. Phone-test result: **no standard Android media/headset events from the glasses.**
- **Glasses button event diagnostic** (`:glasses-button-event-tools`, `b8a5a02`) —
  read-only DeviceNotify/logcat decoder, implemented and reviewed. Phone-test result:
  **no useful events observed.**
- **Feature integration shell** (`ai/claude-feature-integration-shell`, `2cab6df`) — built;
  loosely-coupled Tools shell with 6 cards; gated conversation translation; weak-coupling rules
  enforced. APK produced and copied to the server.
- **V1 documentation set** (this branch) — Project Map, Test Matrix, Roadmap.

## In progress / needs phone-test

- **Feature integration shell — phone-test (postponed).** APK
  `/root/cyanbridge-feature-integration-shell.apk` is built but **not installed/tested**.
  Must verify `Settings → Инструменты / Диагностика` and all 6 cards, and re-confirm
  translation still works inside the shell. Do not mark phone-tested until then.

## Next small sprints

1. **Phone-test the feature integration shell** — install the APK, walk the 6 cards, confirm
   no crash on missing/disabled module, regression-check translation.
2. **Merge / stabilise the integration shell** — once phone-test passes, stabilise the shell
   branch toward `main` (or the stable V1 line).
3. **Direct SDK/BLE notify bridge investigation** — *only if glasses button control is still
   needed.* Read-only investigation of the HeyCyan SDK BLE notify path (the `DeviceNotify`
   frames). No writes to the device, no behaviour changes to the SDK flow.
4. **Photo from glasses → question → AI answer** — capture a photo on the glasses, attach a
   question, get an AI answer. (Related experimental branches already exist:
   `ai/claude-photo-question-feature-shell`, `ai/claude-photo-question-openrouter-direct`.)
5. **Audio/video transcription** — transcription of captured audio/video.
6. **APK delivery without wire** — over-the-air / wireless APK delivery (no USB cable).
7. **Release/debug flavor cleanup and CI** — finalise which modules belong in release vs.
   debug, tidy the gated vs. `debugImplementation` split, and wire CI build/checks.

## Deferred

- Audio/video transcription beyond a first pass (#5) — depends on photo→AI flow landing first.
- Wireless APK delivery (#6) — depends on a stabilised release flavor.
- CI hardening (#7) — depends on the release/debug split being finalised.

## Do not do yet

- Do not modify app logic; V1 doc/shell work is integration + documentation only.
- Do not add new features outside the listed sprints.
- Do not write to the glasses over BLE/SDK during diagnostics — investigation stays
  **read-only**.
- Do not touch: DeviceBindActivity, PictureVm, AlbumDownloader, BleIpBridge/P2P,
  NativeAutomationEngine, MainActivity core flow, ChatThreadActivity, glasses SDK flow,
  media/photo/video/audio flow, Moonshine fallback, ConversationTranslationActivity logic,
  diagnostics logic.
- Do not mark the integration shell APK as phone-tested before it is actually tested.

## Recommended next sequence

1. Phone-test feature integration shell.
2. Merge / stabilise integration shell.
3. Direct SDK/BLE notify bridge investigation (read-only) — *if button control still needed.*
4. Photo from glasses → question → AI answer.
5. Audio/video transcription.
6. APK delivery without wire.
7. Release/debug flavor cleanup and CI.

See also: [CYANBRIDGE_V1_PROJECT_MAP.md](CYANBRIDGE_V1_PROJECT_MAP.md) ·
[CYANBRIDGE_V1_TEST_MATRIX.md](CYANBRIDGE_V1_TEST_MATRIX.md)

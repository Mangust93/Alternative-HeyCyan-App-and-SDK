# Codex review: AI User Shell V1.3

Branch: `ai/codex-review-ai-user-shell-v1-3`
Base: `ai/stable-v1-2-translation-photo-openrouter`
Reviewed feature commit: `be928b2 Add AI user shell for V1.3`

## Summary

Reviewed the new optional `:ai-user-shell` module and its host-app entry point for coupling, build gating, debug/release behavior, and intent boundaries.

The module remains a standalone user-facing shell for `AI-функции`. It has no dependency on `:app`, `:conversation-translation`, `:photo-question-tools`, BLE/P2P/media sync, the vendor SDK, OpenRouter core, translation core, or diagnostics. Feature screens are still launched only by package-scoped Intent actions.

## Findings and changes

### Hardened action resolution

The original implementation used `PackageManager.resolveActivity(intent, 0)` for the Settings entry and shell feature cards. That was functional, but too broad for an in-app action boundary because it did not require a default launch target.

Changed:

- `app/src/main/java/com/fersaiyan/cyanbridge/ui/SettingsActivity.kt`
- `ai-user-shell/src/main/java/com/fersaiyan/cyanbridge/ai_user_shell/AiUserShellActivity.kt`

Both now:

- add `Intent.CATEGORY_DEFAULT` to package-scoped feature intents;
- resolve only with `PackageManager.MATCH_DEFAULT_ONLY`;
- use Android 13+ `ResolveInfoFlags` where required;
- keep graceful degradation when `:ai-user-shell` or target feature modules are absent.

### Verified exported boundaries

- `:ai-user-shell` declares `AiUserShellActivity` with `android:exported="false"`.
- `:conversation-translation` and `:photo-question-tools` target activities are also `android:exported="false"`.
- The host app and shell use `Intent(action).setPackage(packageName)` plus `CATEGORY_DEFAULT`; no direct Activity imports were introduced.

### Verified debug/release and Gradle gating

- Default debug build includes `:ai-user-shell` through `debugImplementation`.
- `-PincludeAiUserShell=false` skips the project dependency and the debug merged manifest has no `AI_USER_SHELL`, `AiUserShellActivity`, or `ai_user_shell` entries.
- Release manifest processing does not include the shell because it is not a release dependency.

## Checks

Required checks:

| Check | Result |
| --- | --- |
| `git status --short --branch` | PASS; branch `ai/codex-review-ai-user-shell-v1-3`, expected review/doc changes present |
| `git diff --check` | PASS |
| `./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs` | PASS with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`; only existing `LocalBroadcastManager` deprecation warnings |
| `./gradlew :app:assembleDebug --no-daemon --no-watch-fs` | PASS with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| `./gradlew :app:assembleDebug -PincludeAiUserShell=false --no-daemon --no-watch-fs` | PASS with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`; Gradle logged `includeAiUserShell=false; skipping :ai-user-shell dependency` |

Additional verification:

| Check | Result |
| --- | --- |
| `./gradlew :app:processReleaseMainManifest --no-daemon --no-watch-fs` | PASS with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| Disabled debug manifest grep for `AI_USER_SHELL`, `AiUserShellActivity`, `ai_user_shell` | PASS; no matches |
| Release merged manifest grep for `AI_USER_SHELL`, `AiUserShellActivity`, `ai_user_shell` | PASS; no matches |
| Default debug merged manifest grep | PASS; `AiUserShellActivity` and `AI_USER_SHELL` present, shell activity exported false |
| Import/dependency review | PASS; no new feature-module imports, no `:ai-user-shell` dependency from feature modules, no dependency on `:app` internals |

Note: `/opt/android-studio/jbr` is not present in this workspace, so the first Gradle attempt with that documented path failed before build execution. The successful checks used the available Java 21 install at `/usr/lib/jvm/java-21-openjdk-amd64`.

## Remaining risks

- No physical-device UI test was run. The Settings card visibility and shell navigation were verified by compile/build/manifest checks only.
- `:ai-user-shell` is currently debug-only. That matches the current wiring, but a future user-facing release must intentionally move the dependency out of `debugImplementation` and re-run release manifest/build checks.
- Action strings are intentionally duplicated for loose coupling; future feature action renames must update both manifests and local constants together.

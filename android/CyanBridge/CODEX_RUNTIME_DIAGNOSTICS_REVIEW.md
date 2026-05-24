# Runtime Diagnostics Tools: Codex Review

## Scope

Проведен review optional module `:runtime-diagnostics-tools` из commit `b09cc78` на ветке `ai/codex-review-runtime-diagnostics-tools` относительно базы `ai/claude-runtime-diagnostics-tools-module`.
Изменения ограничены модулем runtime diagnostics и этим отчетом.

## Checked

- Gradle isolation: module не зависит от `:app`; wiring в `app/build.gradle` только через `debugImplementation`; `releaseImplementation` для него отсутствует.
- Gradle toggle: `-PincludeRuntimeDiagnosticsTools=false` исключает dependency из debug APK graph; runtime module tasks при отключенной сборке не выполняются.
- Manifest: provider использует `${applicationId}.runtime-diagnostics-init`, имеет `exported=false`; activity имеет намеренный `exported=true` для ручного adb запуска debug APK.
- `RuntimeDiagnosticsInitProvider`: nullable `context` обработан; CRUD methods являются безопасными no-op; установка идемпотентна через `AtomicBoolean`.
- `RuntimeDiagnostics`: сохраняет previous uncaught handler, пишет crash event до delegation, не swallowing crash; lifecycle callbacks регистрируются только для `Application` и логируют только имя класса/transition.
- `RuntimeDiagnosticsStore`: app-specific `filesDir`, синхронизация, чтение пустого/отсутствующего файла, clear, bounded throwable representation и byte cap.
- `RuntimeDiagnosticsActivity`: нет app-only imports; heartbeat, clipboard copy, clear и пустое состояние реализованы напрямую через store/framework APIs.
- Merged manifest: компоненты runtime module проверены в вариантах toggle enabled и disabled; конфликтов authority с `.fileprovider` и `.androidx-startup` нет.

## Finding And Fix

1. Fixed: `RuntimeDiagnosticsStore` не обеспечивал заявленный hard cap файла.

   До исправления одна большая строка могла навсегда оставить log больше 1 MB, а сохранение последних половины строк не гарантировало размер ниже cap. Это создавало storage/memory risk для debug instrumentation, включая crash path.
   Исправлено ограничением входов до serialization, полным swallowing внутренних write/serialization failures в `append()`, и trimming по UTF-8 bytes с сохранением последних полных событий в пределах 1 MB.

Других подтвержденных дефектов runtime diagnostics module по проверенному scope не найдено.

## Changed Files

- `runtime-diagnostics-tools/src/main/java/com/fersaiyan/cyanbridge/runtime_diagnostics_tools/RuntimeDiagnosticsStore.kt`: исправлены bounded event serialization, hard cap trimming и безопасный `readRecent(..., maxLines <= 0)`.
- `CODEX_RUNTIME_DIAGNOSTICS_REVIEW.md`: зафиксирован review и результаты проверки.

## Commands

```bash
git diff --check
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace
java -version
readlink -f /usr/bin/java
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace
rg -n "RuntimeDiagnosticsInitProvider|RuntimeDiagnosticsActivity|runtime-diagnostics-init|android:authorities=" app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludeRuntimeDiagnosticsTools=false
rg -n "RuntimeDiagnosticsInitProvider|RuntimeDiagnosticsActivity|runtime-diagnostics-init" app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml app/build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:dependencies --configuration releaseRuntimeClasspath --no-daemon --no-watch-fs -PincludeRuntimeDiagnosticsTools=true
```

`/opt/android-studio/jbr`, предписанный project notes, отсутствует в этом workspace environment; обязательные Gradle checks повторены с доступным Java 21 из `/usr/lib/jvm/java-21-openjdk-amd64`.

## Results

- `git diff --check`: PASS.
- `:app:compileDebugKotlin`: PASS с Java 21 после замены недоступного configured JDK path.
- `:app:assembleDebug` с module enabled: PASS; merged manifest содержит provider (`exported=false`) и activity (`exported=true`) с корректным resolved authority.
- `:app:assembleDebug -PincludeRuntimeDiagnosticsTools=false`: PASS; configuration log сообщает skip dependency, runtime module tasks отсутствуют.
- Disabled merged/packaged manifests: provider, activity и `runtime-diagnostics-init` отсутствуют.
- `releaseRuntimeClasspath` при property=true: `project :runtime-diagnostics-tools` отсутствует; module не попадает в release graph.

## Residual Risks

- `RuntimeDiagnosticsActivity` exported в debug APK намеренно: любое приложение на тестовом устройстве может открыть экран, пока установлен этот debug build.
- Uncaught handler проверен статически и сборкой; корректное delegate-поведение при реальном process crash требует phone test.
- File cap и clipboard behavior проверены по реализации и компиляции; instrumentation/device test в этой среде не запускался.

## Phone Test

1. Установить debug APK с включенным module и force-stop/relaunch приложение.
2. Запустить экран через `adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.runtime_diagnostics_tools.RuntimeDiagnosticsActivity`.
3. Проверить наличие app-start/lifecycle events и пустое состояние после `Clear logs`.
4. Нажать `Add heartbeat event` и `Copy runtime diagnostic bundle`; проверить event и содержимое clipboard.
5. При наличии безопасного воспроизводимого debug crash проверить наличие crash event после следующего запуска и обычное завершение crashed process.
6. Установить APK, собранный с `-PincludeRuntimeDiagnosticsTools=false`, и убедиться, что adb launch activity завершается ошибкой отсутствия компонента.

## Scope Guard

Не изменялись core app flow, glasses SDK integration и перечисленные в задаче app/media/BLE/P2P/automation/transcription classes.

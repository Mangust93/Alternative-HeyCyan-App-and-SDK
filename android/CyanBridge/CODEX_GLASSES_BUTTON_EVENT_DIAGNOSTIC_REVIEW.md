# Codex Review: Glasses Button Event Diagnostic

Дата проверки: 2026-05-26
Ветка: `ai/codex-review-glasses-button-event-diagnostic`
База: `ai/claude-glasses-button-event-diagnostic`

## Область проверки

На момент начала review ветка и база указывали на один commit (`fc3f8a9`,
`Add optional glasses button event diagnostic module`), поэтому исходное добавление
модуля проверено как diff этого commit относительно его родителя (`fc3f8a9^..fc3f8a9`).

Исходный diff добавляет только:

- `glasses-button-event-tools/`;
- conditional wiring в `settings.gradle.kts` и `app/build.gradle`;
- документ расследования `CLAUDE_GLASSES_BUTTON_EVENT_INVESTIGATION.md`.

Файлы core/glasses/media/translation flow (`MainActivity`, `DeviceBindActivity`,
`PictureVm`, `AlbumDownloader`, `BleIpBridge`, P2P, `NativeAutomationEngine`,
`ChatThreadActivity`, Moonshine и media flow) модулем не изменены.

## Что проверено

- `:glasses-button-event-tools` является самостоятельным Android library module.
- В `glasses-button-event-tools/build.gradle` имеется только зависимость на
  `androidx.appcompat:appcompat`; зависимости на `:app`, SDK и
  `:conversation-translation` отсутствуют.
- В `:app` модуль подключается только как `debugImplementation
  project(":glasses-button-event-tools")`.
- `settings.gradle.kts` и `app/build.gradle` используют property
  `includeGlassesButtonEventTools` с default `true`.
- Activity экспортирована только для ручного запуска через `adb`; поскольку manifest
  поступает из debug-only dependency, release wiring модуль не включает.
- Manifest diagnostic-модуля не объявляет runtime permissions, включая `READ_LOGS`.
- Reader фильтрует только tag `DeviceNotify` и не регистрирует SDK listener, BLE
  callback или media/translation hook.
- UI хранит не более 200 отображаемых событий, показывает raw строку и разобранный
  результат; кнопка `Очистить` очищает список и обновляет статус.

## Что исправлено

Изменён только
`glasses-button-event-tools/src/main/java/com/fersaiyan/cyanbridge/glasses_button_event_tools/GlassesButtonEventDiagnosticsActivity.kt`.

- Чтение `logcat` перенесено с main thread на single-thread executor.
- Snapshot ограничен последними 500 строками `DeviceNotify`, а dedup snapshot больше
  не растёт без ограничения на протяжении жизни Activity.
- Активный `logcat` process уничтожается, executor завершается в `onStop()` и
  `onDestroy()`; устаревшие async results игнорируются через generation guard.
- Ошибка запуска/чтения `logcat` остаётся некритичной и отображается в статусе.
- Parser больше не сдвигает индексы при malformed token: malformed, отсутствующий или
  короткий `loadData` безопасно отображается вместе с raw строкой.
- Unknown opcode не помечается как подтверждённое нажатие кнопки.
- Для `0x03` и `0x0c` маркер `КНОПКА` показывается только для press-state
  (`loadData[7] == 1`), как в существующем разборе producer.

## Gradle И Manifest

Использован `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, поскольку требуемый
проектной инструкцией `/opt/android-studio/jbr` отсутствует в данном окружении.

- `./gradlew projects --no-daemon --no-watch-fs --stacktrace`: успешно;
  `:glasses-button-event-tools` присутствует в default graph.
- `./gradlew projects --no-daemon --no-watch-fs --stacktrace
  -PincludeGlassesButtonEventTools=false`: успешно; модуль отсутствует в graph, а
  конфигурация `:app` сообщает `skipping :glasses-button-event-tools dependency`.
- После default `:app:assembleDebug` merged/packaged debug manifest содержит
  `GlassesButtonEventDiagnosticsActivity`.
- После disabled `:app:assembleDebug -PincludeGlassesButtonEventTools=false`
  merged/packaged debug manifest не содержит `GlassesButtonEventDiagnosticsActivity`.

## Результаты сборок

- `git diff --check`: успешно, whitespace errors не обнаружены.
- `./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace`:
  `BUILD SUCCESSFUL`.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace`:
  `BUILD SUCCESSFUL`.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace
  -PincludeGlassesButtonEventTools=false`: `BUILD SUCCESSFUL`.

## Остаточные риски

- Физическая проверка на очках не выполнена в build-среде; соответствие передней,
  задней и сенсорной кнопок конкретным notify opcode нужно подтвердить на телефоне.
- Экран зависит от существующей строки `DeviceNotify` в `MainActivity`; если producer
  перестанет логировать raw frame, диагностика покажет отсутствие событий.
- Reader опрашивает ограниченный snapshot раз в секунду; аномальная очередь более 500
  новых `DeviceNotify` строк между опросами может привести к пропуску старейших строк.
- В существующем producer (`MainActivity.MyDeviceNotifyListener`) до этого модуля уже
  есть прямое обращение к `response.loadData[6]`; его hardening не выполнялся, так как
  core/glasses flow явно исключён из области этой задачи.

## Phone-Test Checklist

1. Подключить очки к приложению.
2. Открыть diagnostics activity через adb:
   `adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.glasses_button_event_tools.GlassesButtonEventDiagnosticsActivity`.
3. Нажать переднюю кнопку очков.
4. Нажать заднюю кнопку очков.
5. Нажать сенсорную кнопку очков.
6. Проверить появление `DeviceNotify`, raw строки и parsed event для каждого нажатия.


# Codex Feature Integration Shell Review

## Контекст

- Проверяемое состояние: локальная ветка `ai/claude-feature-integration-shell` (`HEAD` `94f1403`). Запрошенная ветка `ai/codex-review-feature-integration-shell` в рабочей копии не была выбрана/не существует локально.
- Feature shell введён коммитами `71ef5c2` и `94f1403` поверх `b8a5a02`; review scope определён как `b8a5a02..HEAD` плюс точечные исправления ниже.

## Finding И Исправление

### Исправлено: optional Activity были экспортированы внешним приложениям

- Все шесть optional Activity имели `android:exported="true"`, хотя launcher предназначен только для вызова внутри host APK через `Intent(action).setPackage(packageName)`.
- Это позволяло стороннему приложению открыть debug/diagnostic UI по известному action, включая debug logs и runtime diagnostics.
- Исправлено минимально: в manifest каждого optional module Activity установлен `android:exported="false"`; actions и `DEFAULT` categories сохранены. Комментарии manifest приведены в соответствие с внутренним launcher-контрактом.

## Что Проверено

- `FeatureIntents.kt`, `ToolsActivity.kt`, `activity_tools.xml` и `item_tool_card.xml` находятся в Git index (`git ls-files`) и не попадают под `.gitignore` (`git check-ignore` не нашёл правил).
- `SettingsActivity` открывает только core `ToolsActivity`; entry point не добавляет вызовов glasses/media/SDK logic.
- В исходниках `:app` нет импорта или ссылки на классы Activity optional-модулей.
- Условные `debugImplementation project(":...")` используются только для включения optional APK content; core Kotlin source не имеет type-level зависимости от feature Activity classes.
- Шесть строк actions из `FeatureIntents` совпадают с actions в module manifests.
- Каждый optional Activity manifest содержит action и `android.intent.category.DEFAULT`.
- `ToolsActivity` создаёт `Intent(action).setPackage(packageName)`.
- `ToolsActivity` вызывает `packageManager.resolveActivity(...)` как для status/disabled card, так и повторно непосредственно перед запуском.
- Недоступный module отображается disabled (`tools_status_unavailable`) и не запускается.
- `startActivity(intent)` находится внутри `runCatching` с пользовательским Toast при ошибке запуска.
- При выключенных optional-модулях `ToolsActivity` продолжает компилироваться и собираться без ссылок на отсутствующие классы.
- Scope feature commit не включает `DeviceBindActivity`, `PictureVm`, `AlbumDownloader`, `BleIpBridge`, Wi-Fi P2P, `NativeAutomationEngine`, `MainActivity`, `ConversationTranslationActivity` implementation или diagnostic implementation classes.
- Внесённые Codex-исправления касаются только manifest visibility/комментариев шести optional Activity.

## Actions И Availability

| Module | Action | Default build | Flag-off build |
| --- | --- | --- | --- |
| `:conversation-translation` | `CONVERSATION_TRANSLATION` | присутствует, `exported=false` | отсутствует |
| `:headset-button-tools` | `HEADSET_BUTTON_DIAGNOSTIC` | присутствует, `exported=false` | отсутствует |
| `:glasses-button-event-tools` | `GLASSES_BUTTON_EVENT_DIAGNOSTIC` | присутствует, `exported=false` | отсутствует |
| `:debug-log-tools` | `DEBUG_LOGS` | присутствует, `exported=false` | отсутствует |
| `:runtime-diagnostics-tools` | `RUNTIME_DIAGNOSTICS` | присутствует, `exported=false` | отсутствует |
| `:phone-test-tools` | `PHONE_TEST_TOOLS` | присутствует, `exported=false` | отсутствует |

Проверка выполнена по `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` сразу после соответствующей сборки. Для default build итоговый manifest содержит все шесть package-scoped entry points. Для каждой disabled build выключенный action отсутствует, поэтому `resolveActivity()` возвратит отсутствие карточки-модуля в host application context.

## Результаты Сборок

Все успешные Gradle-команды выполнялись с `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, поскольку указанного в проектных заметках `/opt/android-studio/jbr` в данной среде нет. Попытка с `/opt/android-studio/jbr` завершилась до запуска Gradle сообщением `JAVA_HOME is set to an invalid directory`.

| Проверка | Результат |
| --- | --- |
| `git diff --check` | PASS |
| `:app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace` | PASS |
| `:app:assembleDebug --no-daemon --no-watch-fs --stacktrace` | PASS |
| `:app:assembleDebug ... -PincludeConversationTranslation=false` | PASS; action отсутствует |
| `:app:assembleDebug ... -PincludeHeadsetButtonTools=false` | PASS; action отсутствует |
| `:app:assembleDebug ... -PincludeGlassesButtonEventTools=false` | PASS; action отсутствует |
| `:app:assembleDebug ... -PincludeConversationTranslation=false -PincludeHeadsetButtonTools=false -PincludeGlassesButtonEventTools=false` | PASS; три actions отсутствуют |
| `:app:assembleDebug ... -PincludeDebugLogTools=false` | PASS; action отсутствует |
| `:app:assembleDebug ... -PincludeRuntimeDiagnosticsTools=false` | PASS; action отсутствует |
| `:app:assembleDebug ... -PincludePhoneTestTools=false` | PASS; action отсутствует |

Gradle сообщает существующее предупреждение о deprecated features для Gradle 9.0 и отсутствие Moonshine native runtime; это не блокирует feature shell build.

## Подтверждение Слабой Связанности

Core хранит только action constants и launcher UI. Ни один optional Activity class не упоминается в core source. Optional modules входят в debug APK условно через Gradle dependency, а запуск выполняется по package-scoped implicit intent после `resolveActivity()`.

С non-exported Activity этот контракт остаётся работоспособным внутри того же application package и одновременно закрывает внешний доступ к diagnostic screens. Выключение любого проверенного module удаляет его manifest action, но не требует изменения core кода и не ломает сборку.

## Остаточные Риски

- UI navigation и фактический запуск всех шести Activity не проверялись на физическом телефоне; выполнены статическая проверка и build/merged-manifest verification.
- Наличие action в merged manifest подтверждает контракт package-scoped resolve; окончательная runtime проверка `resolveActivity()` должна быть выполнена после установки default и disabled APK на устройство.
- Существующее Gradle deprecation warning следует отдельно устранить до обновления на Gradle 9, оно не введено feature shell.

## Phone-Test Checklist

1. Открыть Настройки.
2. Нажать "Инструменты / Диагностика".
3. Проверить 6 карточек.
4. Открыть Перевод диалога.
5. Открыть диагностику кнопки гарнитуры.
6. Открыть диагностику кнопок очков.
7. Открыть Debug logs.
8. Открыть Runtime diagnostics.
9. Открыть Phone test tools.

## Итог

После минимальной manifest-правки feature integration shell сохраняет Intent-only слабую связанность, безопасно деградирует при исключённых modules и не экспонирует diagnostic Activity сторонним приложениям.

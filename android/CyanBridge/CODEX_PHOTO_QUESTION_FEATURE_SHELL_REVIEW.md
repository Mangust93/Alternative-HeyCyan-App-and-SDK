# Codex review: `:photo-question-tools` feature shell

Ветка: `ai/codex-review-photo-question-feature-shell`
База: `ai/claude-photo-question-feature-shell` (`9ee753f`)
Feature commit: `9ee753f` (`Add optional photo question feature shell`)

## Итог

После одной минимальной правки feature shell соответствует optional-module boundary и не вводит реального AI/networking вызова.
Core app остаётся независимым от классов модуля и корректно собирается при отключённом `:photo-question-tools`.

## Что проверено

- Gradle boundary: `photo-question-tools/build.gradle` содержит только `androidx.appcompat:appcompat:1.7.1`; зависимостей на `:app`, glasses SDK, BLE/media flow или `:conversation-translation` нет.
- App boundary: `:app` подключает модуль только как optional `debugImplementation`; поиск по `app/src/main/java` не находит импортов/ссылок на `PhotoQuestionActivity` или package модуля.
- Intent contract: `FeatureIntents.PHOTO_QUESTION` и action в module manifest совпадают: `com.fersaiyan.cyanbridge.feature.PHOTO_QUESTION`.
- Availability: `ToolsActivity` создаёт package-scoped `Intent(action)`, проверяет `resolveActivity()` и для отсутствующего модуля показывает `Модуль не включён` с disabled-кнопкой.
- Export safety: `PhotoQuestionActivity` имеет `android:exported="false"`; внешнее открытие экрану не требуется.
- Picker / permissions: модуль использует `ActivityResultContracts.GetContent("image/*")` и не декларирует storage permission.
- Network behavior: в модуле нет networking dependency, `INTERNET` permission либо реального внешнего запроса; responder возвращает локальный placeholder.
- Input / lifecycle: без выбранного фото и с пустым вопросом отправка не выполняется; `shutdown()` инвалидирует callback и останавливает executor в `onDestroy()`.
- Activity compatibility: новая `androidx.activity:activity-ktx` не добавлена, app-wide Activity dependency не bump-нута, существующие nullable `onNewIntent(Intent?)` в `:app` компилируются.
- Scope: feature commit не меняет `DeviceBindActivity`, `PictureVm`, `AlbumDownloader`, `BleIpBridge`/P2P, `NativeAutomationEngine`, glasses/media flow, `ConversationTranslationActivity` или diagnostic implementation classes.

## Исправлено

1. `PhotoQuestionResponder.Request` теперь получает выбранный `imageUri`, а `PhotoQuestionActivity` передаёт его вместе с вопросом и display name.
   Раньше seam получал только имя файла: будущий server/Hermes responder не мог бы прочитать изображение без изменения Activity.
2. Из KDoc `PhotoQuestionActivity` удалена неверная adb-подсказка для прямого запуска non-exported Activity.
   Экран документирован как внутренний entry point через `PHOTO_QUESTION` action из Tools / Diagnostics.

Hermes, OpenRouter, HTTP-клиент, permission или redesign не добавлялись.

## Слабая связанность

- Модуль компилируется как самостоятельная Android library и взаимодействует с host app только через manifest action.
- Host shell знает только строковый action и UI-ресурсы карточки; классы optional module в `:app` не импортируются.
- При `-PincludePhotoQuestionTools=false` Gradle не включает project dependency, action исчезает из APK, а карточка остаётся безопасно недоступной через существующую ветку `resolveActivity()`.

## Сборки и manifest

`/opt/android-studio/jbr`, указанный в инструкции, отсутствует в данном окружении; эквивалентные проверки выполнены с установленным Java 21: `/usr/lib/jvm/java-21-openjdk-amd64`.

| Проверка | Результат |
| --- | --- |
| `git diff --check` | PASS |
| `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace` | Не стартует: указанный JDK directory отсутствует |
| `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace` | PASS |
| `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace` | PASS |
| Default debug merged/packaged manifest | PASS: `PhotoQuestionActivity` и `PHOTO_QUESTION` присутствуют; activity `exported=false` |
| `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludePhotoQuestionTools=false` | PASS; Gradle сообщает `skipping :photo-question-tools dependency` |
| Disabled debug merged/packaged manifest | PASS: `PhotoQuestionActivity` и `PHOTO_QUESTION` отсутствуют |

## Остаточные риски

- Физический phone test не выполнялся в этой среде; необходимо подтвердить picker, отображение превью и переход из карточки на устройстве.
- При configuration change экран пересоздаётся безопасно, но выбранное фото и placeholder-state явно не сохраняются; для раннего shell это UX-ограничение, не crash path.
- `imageUri` достаточен для responder в пределах текущего экрана; если будущая реализация перенесёт обработку в длительную background-задачу, потребуется отдельно проверить lifetime URI grant.

## Phone-test checklist

1. Открыть Настройки.
2. Открыть Инструменты / Диагностика.
3. Найти карточку «Фото и вопрос».
4. Открыть экран.
5. Выбрать фото.
6. Ввести вопрос.
7. Нажать «Спросить».
8. Увидеть placeholder-ответ.


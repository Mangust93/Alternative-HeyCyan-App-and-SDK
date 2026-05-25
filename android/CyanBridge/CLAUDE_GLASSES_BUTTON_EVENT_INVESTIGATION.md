# Glasses Button Event — Investigation

Ветка: `ai/claude-glasses-button-event-diagnostic`
Дата: 2026-05-25

## Постановка задачи

Стандартный headset/media-button diagnostic (`:headset-button-tools`) НЕ увидел событий
`KEYCODE_HEADSETHOOK` / `MEDIA_PLAY_PAUSE` / `VOICE_ASSIST` при нажатии кнопок очков.
Гипотеза архитектора: кнопки очков идут не как Android media key events, а через
HeyCyan SDK / BLE notify / device callbacks. Нужно проверить и, если есть безопасный
путь, сделать минимальный диагностический модуль.

## Где искал

- `settings.gradle.kts`, `app/build.gradle` — структура модулей и подключение SDK.
- `app/src/main/java/.../MainActivity.kt` (4508 строк) — основной потребитель SDK.
- `app/src/main/java/.../ui/SettingsActivity.kt` — существующий сбор логов.
- AAR `app/libs/glasses_sdk_20250723_v01.aar` — распакован, классы `com.oudmon.ble.*`
  изучены через `javap -p`.
- Существующие optional-модули: `:headset-button-tools`, `:debug-log-tools` — как
  образец паттерна standalone-модуля.

## Какие классы / файлы проверил

SDK (`com.oudmon.ble.*`, из AAR):

| Класс | Роль |
|---|---|
| `communication.bigData.resp.GlassesDeviceNotifyListener` | Слушатель notify очков; `parseData(cmdType, GlassesDeviceNotifyRsp)` |
| `communication.bigData.resp.GlassesDeviceNotifyRsp` | `getLoadData(): byte[]` — **сырой payload notify** |
| `communication.LargeDataHandler` | `addOutDeviceListener(cmdType, listener)` — регистрация слушателя |
| `communication.responseImpl.InnerCameraNotifyListener` | Слушатель `CameraNotifyRsp` |
| `communication.rsp.CameraNotifyRsp` | `ACTION_INTO_CAMERA_UI / ACTION_TAKE_PHOTO / ACTION_FINISH`, `getAction()` |
| `communication.responseImpl.MusicCommandListener` | Принимает `MusicCommandRsp`, внутри вызывает `controlMusic(..., KeyEvent)` |
| `communication.req.CameraReq`, `req.GlassModelControlReq` | Команды на очки (исходящие) |
| `communication.bigData.resp.GlassesTouchSupportRsp` | Флаги возможностей (translation/wear/volume), не события |

В приложении (`MainActivity.kt`):

- `MyDeviceNotifyListener : GlassesDeviceNotifyListener` (строка ~4351) — главный хук.
- Регистрация: `LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)`
  (строки 328 и 641).
- `DownloadNotifyListener` (строка ~4101) — узкий слушатель `cmdType=2` для импорта/скачивания.
- `SettingsActivity.collectLogcat()` (строка ~442): `logcat -d -t 500 -s ... DeviceNotify:* ...`.

## Какие SDK / BLE callbacks найдены

Кнопки очков приходят как **BLE notify-кадры через HeyCyan SDK**, попадая в
`MyDeviceNotifyListener.parseData(cmdType, GlassesDeviceNotifyRsp)`. Опкод события —
байт `loadData[6]`. Существующий разбор в `MainActivity`:

| `loadData[6]` | Событие | Кнопка? |
|---|---|---|
| `0x02` | AI Photo / быстрое распознавание (лог: "AI Photo Button Pressed") | **да** |
| `0x03` (`loadData[7]==1`) | AI / активация микрофона (лог: "AI Button Pressed") | **да** |
| `0x0c` (`loadData[7]==1`) | Событие паузы | **да** |
| `0x05` | Отчёт о батарее | нет |
| `0x04` | Прогресс OTA | нет |
| `0x0d` | Отвязка приложения | нет |
| `0x0e` | Мало памяти | нет |
| `0x10` | Пауза перевода | нет |
| `0x12` | Изменение громкости | нет |
| `0x08` / `0x09` | WiFi IP / P2P ошибка (data download) | нет |

`CameraNotifyRsp` (ACTION_TAKE_PHOTO) и `MusicCommandListener` (внутри SDK конвертит в
`KeyEvent` через `controlMusic`) — отдельные SDK-каналы, но в текущем приложении
`MyDeviceNotifyListener` через `cmdType=100` — это рабочий и достаточный источник.

## Есть ли явные события кнопок

Да. Передняя/AI/микрофонная/пауза-кнопки уже распознаются по `loadData[6]`. Headset/media
diagnostic ничего не видел, потому что эти нажатия **не проходят через Android KeyEvent /
ACTION_MEDIA_BUTTON** — они приходят BLE-уведомлением и обрабатываются внутри приложения.

## Есть ли raw notify события

Да, и это ключевой факт для безопасного модуля. `MyDeviceNotifyListener.parseData`
(MainActivity.kt:4355) логирует **каждый** кадр notify ещё до `when`:

```kotlin
Log.i("DeviceNotify",
    "cmdType=$cmdType, loadData=${response.loadData.joinToString(",") { it.toInt().toString() }}")
```

То есть сырой payload (включая неизвестные опкоды новых кнопок) уже доступен в logcat под
тегом `DeviceNotify`, без каких-либо изменений в коде.

## Можно ли сделать отдельный module

Да — безопасно и без зависимости от `:app` и от SDK. Найдена готовая read-only точка:
приложение уже само читает свой logcat (`SettingsActivity.collectLogcat()` →
`logcat -d -t 500 -s ... DeviceNotify:* ...`). Приложение (на Android 4.1+) всегда может
читать СВОИ логи без разрешения `READ_LOGS`.

Поэтому отдельный модуль `:glasses-button-event-tools`:
- tail-ит собственный logcat по тегу `DeviceNotify`;
- парсит строки `cmdType=..., loadData=...`;
- декодирует `loadData[6]` в человекочитаемую метку (таблица выше, продублирована в модуле);
- показывает raw + parsed, статус, кнопку «Очистить», инструкцию;
- НЕ регистрирует SDK-слушатель, НЕ трогает BLE/медиа-flow/SDK/переводчик.

## Риски

- **Минимальные.** Модуль строго read-only (читает свой logcat), не пишет в BLE/SDK,
  не привязывает кнопки к переводу, изолирован от `:conversation-translation`.
- Зависит от наличия лог-строки `Log.i("DeviceNotify", ...)` в `MainActivity` (уже есть).
  Если её удалят — модуль перестанет видеть кадры (а не «сломается»).
- Logcat-буфер ограничен и ротируется — для ручной диагностики приемлемо.
- Декодирование опкодов продублировано из `MainActivity` (нет compile-зависимости от
  `:app`); при изменении протокола таблицу в модуле нужно обновить вручную.

## Минимальный следующий шаг (выполнено в этом спринте)

Создан optional модуль `:glasses-button-event-tools`:
- gate `-PincludeGlassesButtonEventTools=false` (по умолчанию включён, как у соседних модулей);
- `debugImplementation` only (никогда не в release);
- экран `GlassesButtonEventDiagnosticsActivity`, exported для запуска по adb:
  ```
  adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.glasses_button_event_tools.GlassesButtonEventDiagnosticsActivity
  ```

Дальнейшие возможные шаги (НЕ сделаны, требуют отдельного решения):
- При желании — добавить кнопку входа в экран из основного debug-UI (сейчас только adb).
- Если потребуется ловить `CameraNotifyRsp` / `MusicCommandRsp` напрямую — это уже не
  read-only и потребует регистрации SDK-слушателя; делать только при явной необходимости.

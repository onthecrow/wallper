# План миграции архитектуры Wallper

> **Коротко.** Wallper переехал из одного модуля с пакетами «по слоям» на схему NomadRates, OnthecrowVPN и elixapp:
> - каждая фича разложена на модули `logic-api` / `logic-impl` / `ui-api` / `ui-impl`;
> - DI на Hilt, навигация на Navigation3, MVI с reducer'ом на `runningFold`.
>
> Фичи две: **главный экран** (`feature:wallpapers`) и **редактор**, он же экран добавления (`feature:editor`). Сервис обоев, OpenGL и плеер живут в отдельных `core`-модулях, FFmpeg удалён.
>
> **Статус на 2026-10-21:** этапы 0–9 выполнены. Сборка и unit-тесты зелёные, ничего не закоммичено. Что изменилось в поведении — в [разделе 6](#6-что-изменилось-в-поведении), что отложено — в [разделе 8](#8-todo-и-отложенное).

**Содержание**
1. [Как было](#1-как-было)
2. [Архитектура](#2-архитектура)
3. [OpenGL, Media3, FFmpeg: что вынесено в модули](#3-opengl-media3-ffmpeg-что-вынесено-в-модули)
4. [Ключевые решения](#4-ключевые-решения)
5. [Этапы миграции](#5-этапы-миграции)
6. [Что изменилось в поведении](#6-что-изменилось-в-поведении)
7. [Найдено по пути (отложено)](#7-найдено-по-пути-отложено)
8. [TODO и отложенное](#8-todo-и-отложенное)

---

## 1. Как было

> **Коротко.** Один модуль `:app`, около 6 300 строк кода. Пакеты назывались по слоям, но реальных границ между ними не было: Room-сущность доходила до UI и до сервиса, UI знал о сервисе обоев, во ViewModel жил `Context`.

| # | Проблема | Чем закрыто |
|---|---|---|
| 1 | Room-сущность `WallpaperEntity` работала как доменная модель, `android.graphics.Rect` хранился в JSON | доменные модели в `logic-api`, `CropRect` в `core:model`, Room только в `wallpapers:logic-impl` |
| 2 | `VideoMetadata` из пакета `service` использовали `gl`, `util` и редактор | `MediaInfo` в `core:model`, записывается в БД при создании обоев |
| 3 | Главный экран импортировал класс сервиса | порт `LiveWallpaperController` в `core:engine:api` |
| 4 | Глобальный `object ImagePicker` | `rememberLauncherForActivityResult(PickVisualMedia)` в экране |
| 5 | Одноразовые действия хранились в состоянии (`BaseState.actions`), рядом публичный `Channel` | MVI на `runningFold`, статус экспорта лежит в состоянии |
| 6 | `Context` во ViewModel | `Context` есть только в data-слое и в UI |
| 7 | `MainScope()` в трёх местах | `DispatchersProvider`, `ApplicationScopeProvider`, `callbackFlow` |
| 8 | Строковые маршруты с `URLEncoder` | `@Serializable` Destination, Navigation3 |
| 9 | Мёртвый код: FFmpeg, `StillWallpaperEngine`, `BitmapUtils` и т. д. | удалён |
| 10 | Нет тестов и build-logic, AGP beta | build-logic, 26 unit-тестов, AGP 9.2.0 |

---

## 2. Архитектура

> **Коротко.** Фича — это четыре модуля: `logic-api` (интерфейсы use case'ов и модели), `logic-impl` (реализация и данные), `ui-api` (только Destination), `ui-impl` (экран, ViewModel, reducer). У чужой фичи модуль видит только `-api`. Сервис обоев, GL и плеер лежат в `core` за интерфейсами. `:app` только собирает граф.

### 2.1. Модули

```
Wallper/
├── build-logic/                    convention-плагины + проверка границ
├── app/                            Application, MainActivity, стартовый Destination
├── core/
│   ├── model/                      чистый Kotlin (JVM): CropRect, MediaInfo, MediaType, ClipRange
│   ├── coroutines/                 DispatchersProvider, ApplicationScopeProvider
│   ├── mvi/                        Event, State, Reducer, BaseViewModel
│   ├── navigation/api/             Destination, Navigator, FeatureEntry + featureEntry(), NavigationProvider
│   ├── navigation/impl/            хост NavDisplay (Navigation3), NavigatorImpl, @StartDestination
│   ├── designsystem/               WallperTheme, FAB, app_name
│   ├── resources/                  ресурсы бренда без кода и без Compose: иконка, цвет фона, мото
│   ├── storage/                    MediaStorage: файлы медиа в device-protected storage
│   ├── gl/                         EGL-поток, шейдеры, текстуры, UV-математика, рендереры
│   ├── player/                     createLeanExoPlayer(), ExoPlayerFactory, Watchhog
│   └── engine/
│       ├── api/                    LiveWallpaperController, порт ActiveWallpaperSource, WallpaperSpec
│       └── impl/                   WallperWallpaperService, движки GL / pre-cropped / placeholder
└── feature/
    ├── wallpapers/                 «Главный экран» и владелец домена обоев
    │   ├── logic-api/              (JVM) Wallpaper, NewWallpaper, Observe/Activate/Delete/Save
    │   ├── logic-impl/             Room (wallpapers.db), репозиторий, ActiveWallpaperSourceImpl
    │   ├── ui-api/                 WallpapersDestination
    │   └── ui-impl/                экран, VM, reducer, карточка, выбор медиа
    └── editor/                     «Добавление»
        ├── logic-api/              (JVM) PrepareMedia, IsMediaSupported, CreateWallpaper, ExportStatus
        ├── logic-impl/             MediaInspector, экспорт через Media3 Transformer
        ├── ui-api/                 EditorDestination(uri)
        └── ui-impl/                экран, VM, reducer, компонент кроппера, превью
```

Чего нет специально:
- **`core:database`.** БД одна, её создаёт `feature:wallpapers:logic-impl`. Фабрика понадобится, когда появится вторая БД.
- **`core:datastore`.** Раньше в DataStore лежало только разрешение экрана. Теперь редактор считает пропорции через `WindowMetricsCalculator`, и DataStore удалён. Модуль появится вместе с первой настройкой.
- **`ScreenResultDispatcher`.** Экраны не возвращают друг другу результатов: список обновляется сам через Flow из БД.

### 2.2. Правила зависимостей

```
app ──────────────────────► все *-impl (только ради DI) + wallpapers:ui-api (старт)

feature:X:ui-impl ────────► feature:X:logic-api, feature:Y:ui-api, feature:Y:logic-api,
                            core:mvi, core:navigation:api, core:designsystem, core:coroutines,
                            core:engine:api (главный экран), core:player (редактор)

feature:X:logic-impl ─────► feature:X:logic-api, feature:Y:logic-api,
                            core:storage, core:coroutines, core:model, core:engine:api

core:engine:impl ─────────► core:engine:api, core:gl, core:player, core:resources
core:gl ──────────────────► core:model            (без DI, без Room, без ExoPlayer)
core:player ──────────────► media3-exoplayer
```

1. У другой фичи модуль видит только `ui-api` (навигация) и `logic-api` (домен). Модули `-impl` видит только `:app`.
2. `core` не зависит от `feature`. Сервису нужны данные обоев, поэтому `core:engine:api` объявляет порт `ActiveWallpaperSource`, а `wallpapers:logic-impl` его реализует.
3. `-api`-модули не зависят от `-impl`.
4. `core:model`, `core:coroutines`, `core:storage`, `core:gl`, `core:player` и `core:engine:*` не зависят от Compose и от `core:designsystem`: сервис стартует в direct boot и не имеет UI.
5. Всё, что читает сервис, лежит в device-protected storage (DPS): БД, медиафайлы, превью.

Правила 1–4 проверяет `enforceModuleBoundaries()` в `build-logic`: нарушение роняет конфигурацию Gradle. Проверено на `editor:ui-impl → wallpapers:logic-impl` и `engine:impl → designsystem`.

### 2.3. Внутри фичи

```
feature/wallpapers/ui-impl/src/main/kotlin/com/onthecrow/wallper/wallpapers/
├── WallpapersScreen.kt        stateless (state, eventFlow, onEvent) + превью
├── WallpapersViewModel.kt     BaseViewModel, побочные эффекты в коллекторе eventFlow
├── WallpapersReducer.kt       чистая функция (state, event) -> state
├── WallpapersState.kt
├── WallpapersEvent.kt         действия пользователя, внутренние результаты, one-shot для экрана
├── model/  mapper/  view/
└── di/WallpapersUiModule.kt   @Provides @IntoSet FeatureEntry: hiltViewModel + collectAsStateWithLifecycle
```

- Kotlin-пакет общий для всех модулей фичи (`com.onthecrow.wallper.wallpapers`), как в NomadRates. `namespace` у каждого модуля свой (`…wallpapers.ui`, `…wallpapers.logic`).
- В `logic-impl` классы `*UseCaseImpl` лежат в корне пакета, рядом `data/`, `mapper/`, `di/`. Всё объявлено `internal`.
- Имена файлов не должны совпадать между модулями одной фичи, иначе классы `*Kt` столкнутся при сборке dex (`WallpaperEntityMapper` и `WallpaperItemMapper`).

---

## 3. OpenGL, Media3, FFmpeg: что вынесено в модули

> **Коротко.** FFmpeg удалён. OpenGL вынесен в `core:gl`, ExoPlayer в `core:player`. Transformer остался в `feature:editor:logic-impl`. Сервис обоев оформлен как `core:engine:api/impl`.

**Правило.** Код получает свой модуль в одном из двух случаев:
- (а) он нужен двум и более модулям;
- (б) это отдельная подсистема со своим жизненным циклом и потоками.

Иначе код остаётся пакетом внутри фичи. Модули не уменьшают APK и не экономят память: это по-прежнему один процесс. Они дают проверку границ при компиляции, кеш сборки и изолированные тесты.

| Что | Где | Почему |
|---|---|---|
| **FFmpeg** | удалён | 3.1 |
| **OpenGL** | `core:gl` | подсистема (б) |
| **ExoPlayer** + `Watchhog` | `core:player` | два потребителя (а): сервис и превью редактора |
| **Transformer** | `feature:editor:logic-impl/export/` | потребитель один |
| **MediaMetadataRetriever** | `feature:editor:logic-impl/media/` | `MediaInfo` теперь пишется в БД при создании, сервису он не нужен |
| **WallpaperService** | `core:engine:api` + `:impl` | подсистема (б), фичам нужен узкий интерфейс |

### 3.1. FFmpeg: удалён
- Код был закомментирован целиком, зависимость удалили ещё в `e4ec7ae`.
- FFmpegKit официально закрыт, его готовые бинарники в 2025 году сняли с публикации.
- Против него ещё нативные `.so` на десятки мегабайт, требование Google Play к 16 KB page size и GPL/LGPL.
- Запасной путь уже есть: без «Additional processing» обои сохраняются без экспорта, и GL кропает видео на лету. Интерфейс `VideoExporter` оставлен, вторую реализацию можно добавить, не трогая фичи.

### 3.2. `core:gl`
- Модуль знает только `core:model` и Android-примитивы:
  - `VideoWallpaperRenderer(VideoRenderParams, onFrameAvailable, onVideoSurfaceCreated)` отдаёт `Surface` через колбэк, плеер к нему подключает движок;
  - `BitmapWallpaperRenderer(width, height, createBitmap)` рисует любой битмап, а плейсхолдер передаёт в него брендинг.
- Наружу видны только `WallpaperGLThread`, `GLWallpaperRenderer`, два рендерера и `VideoRenderParams`. Остальное (`OpenGLScene`, текстуры, шейдеры, `UVCoords`) — `internal`.
- `UVCoordsTest` фиксирует текущую математику для поворотов 0/90/180/270.

### 3.3. `core:player`
- `createLeanExoPlayer(context)` — единственное место, где строится ExoPlayer: экономный `LoadControl`, без звука, по кругу. Им пользуются и сервис, и превью редактора. Раньше настройка была продублирована.
- `ExoPlayerFactory` (для сервиса) добавляет клиппинг и `Watchhog`.
- `@UnstableApi` в Media3 — это lint-аннотация, а не Kotlin `@RequiresOptIn`. Разрешить её на весь модуль через флаги компилятора нельзя, поэтому аннотации остались в файлах.

### 3.4. `core:engine`
- **`api`:** `ActiveWallpaperSource` (`Flow<WallpaperSpec?>`), `WallpaperSpec`, `LiveWallpaperController.createSetWallpaperIntent()`.
- **`impl`:**
  - `WallperWallpaperService` (`@AndroidEntryPoint`) со своим `<service>` в манифесте модуля;
  - движки `GLWallpaperEngine`, `VideoWallpaperEngine` (pre-cropped), `PlaceholderWallpaperEngine`;
  - `WallpaperColors`.
- Сервис не знает о Room: `ActiveWallpaperSource` реализует `wallpapers:logic-impl`.
- **После релиза имя класса `com.onthecrow.wallper.core.engine.impl.WallperWallpaperService` менять нельзя:** система хранит его как выбранный live wallpaper.

---

## 4. Ключевые решения

> **Коротко.** Hilt вместо Koin, Navigation3 по образцу NomadRates, MVI на `runningFold`, но с неограниченным буфером, нейминг модулей как в NomadRates и VPN, build-logic как в elixapp.

| Вопрос | Решение | Почему |
|---|---|---|
| DI | **Hilt** | См. 4.1. `registerScreen` из NomadRates превратился в `@Provides @IntoSet FeatureEntry<*>` |
| Навигация | **androidx Navigation3** + `Navigator` + `FeatureEntry` | Как в NomadRates. Вместо самописного `NavigationScopeController` используется официальный `rememberViewModelStoreNavEntryDecorator()`: Hilt нужна owner с фабрикой по умолчанию, иначе `hiltViewModel()` и `SavedStateHandle` не работают |
| Аргументы экрана | `@HiltViewModel(assistedFactory)` + `hiltViewModel<VM, Factory>(creationCallback)` | Destination передаётся во ViewModel напрямую, без `SavedStateHandle` и без строковых ключей |
| MVI | `BaseViewModel` из NomadRates и VPN (`runningFold` + `eventFlow`) | По твоему решению. Отличия: буфер событий неограниченный (вместо 64 + `DROP_OLDEST`), чтобы события слайдера и кропа не терялись. Начальное состояние передаётся в конструктор: состояние из аргументов экрана ещё недоступно, пока выполняется конструктор базового класса |
| Имена модулей | `logic-api` / `logic-impl` / `ui-api` / `ui-impl` | Как в NomadRates и VPN |
| build-logic | 6 convention-плагинов + `enforceModuleBoundaries()` | По образцу elixapp |
| Тулчейн | AGP 9.2.0, Kotlin 2.4.0, KSP 2.3.10, Hilt 2.60.1, Gradle 9.6.1 | Как в elixapp. `android.newDsl=false` больше не нужен. Включены configuration cache и build cache |
| Тесты | `kotlin-test` + `coroutines-test`, фейки руками | Как во всех трёх проектах |
| Room | Схема v1 с нуля, `exportSchema` включён, файл `wallpapers.db` | По твоему решению. Новое имя файла: старая `wallper-db` несовместима и просто остаётся лежать, приложение не падает |

### 4.1. Hilt и «весь граф строится на старте»
Это не так, причём ни в Hilt, ни в Dagger, ведь у них одна и та же кодогенерация.
- **Что происходит на старте.** Создаётся только объект компонента, это дёшево (Hilt включает `fastInit`).
- **Когда создаются зависимости.** Каждая — при первом запросе, `@Singleton` тоже. Для отложенного создания внутри графа есть `Lazy<T>` и `Provider<T>`.

Что в Hilt правда ограничено:
- `SingletonComponent` один на всё приложение. Его собирает `:app` из `@InstallIn`-модулей всех Gradle-модулей. Поэтому `:app` перекомпилируется, когда меняются биндинги в фичах.
- Иерархия компонентов фиксированная. Свои компоненты делаются через `@DefineComponent`, это громоздко.
- Dynamic feature modules не поддерживаются.

Для Wallper (один APK, `Singleton` + `ViewModelComponent` + сервис) ничего из этого не мешает. Голый Dagger дал бы ту же модель, только с ручной обвязкой Android-классов.

---

## 5. Этапы миграции

> **Коротко.** Этапы 0–9 выполнены в одном заходе. После каждого этапа модуль собирался и прогонялись его тесты. В конце полная сборка debug и release (R8) плюс 26 unit-тестов.

| Этап | Что сделано | Тесты |
|---|---|---|
| 0. Подготовка ✅ | AGP 9.2.0 / Kotlin 2.4.0 / KSP 2.3.10 / Hilt 2.60.1 / Gradle 9.6.1. Удалены FFmpeg-кроппер, fallback-цепочка, `StillWallpaperEngine`, `BitmapUtils`, `SimpleBenchmark`, неиспользуемые `RendererParams`, случайный `glClearColor` | — |
| 1. build-logic ✅ | `wallper.android.application` / `.library` / `.compose` / `.room`, `wallper.hilt`, `wallper.jvm.library`, `enforceModuleBoundaries()`, typesafe project accessors | проверка границ |
| 2. Базовые core ✅ | `core:model`, `core:coroutines`, `core:mvi`, `core:navigation:api/impl`, `core:designsystem`, `core:resources`, `core:storage` | — |
| 3. Домен обоев ✅ | `wallpapers:logic-api/impl`, схема v1, `core:engine:api` | 3 |
| 4. `core:player` ✅ | `createLeanExoPlayer`, `ExoPlayerFactory`, `Watchhog` | — |
| 5. `core:gl` ✅ | рендереры без ExoPlayer и `R`, `internal` всё лишнее | 3 (UV) |
| 6. `core:engine:impl` ✅ | сервис, движки, `LiveWallpaperControllerImpl`, свой манифест | — |
| 7. Главный экран ✅ | `wallpapers:ui-api/impl`, PickVisualMedia, eventFlow для Toast и Intent | 7 |
| 8. Редактор ✅ | `editor:logic-api/impl`, `editor:ui-api/impl`, кроппер внутри `ui-impl` | 4 + 9 |
| 9. Тонкий `:app` ✅ | `MainActivity` + `AppModule` (`@StartDestination`), старые `data/`, `domain/`, `navigation/` и т. д. удалены | — |

### Проверка
- `./gradlew assembleDebug testDebugUnitTest` — зелёные. 26 тестов: домен обоев 3, UV-математика 3, главный экран 7 (reducer + VM), создание обоев 4, редактор 9 (reducer + VM).
- `./gradlew :app:assembleRelease` (R8) — APK 3,2 МБ.
- **Smoke-чеклист на устройстве ещё не пройден**: на этой машине нет рабочего system image для эмулятора.

### Smoke-чеклист (руками, после каждого изменения архитектуры)
1. Добавить видео, выставить кроп, сохранить — с «Additional processing» и без. Обои появились в списке.
2. Активировать обои. На домашнем экране и экране блокировки правильный кроп и правильный отрезок.
3. Видео, снятое вертикально (поворот 90 или 270).
4. Удалить активные и неактивные обои.
5. Перезагрузить и не разблокировать — обои видны (direct boot).
6. Оставить на ночь (doze) — после разблокировки видео играет (Watchhog).
7. Переключать обои при открытом системном предпросмотре.

---

## 6. Что изменилось в поведении

> **Коротко.** Почти всё поведение сохранено. Изменения — следствие новой схемы БД, нового имени класса сервиса и замены пикера.

1. **Старые обои не переносятся.** Новая БД — новый файл `wallpapers.db`. На тестовых устройствах обои нужно создать заново, старые файлы остаются лежать в DPS.
2. **Live wallpaper нужно выбрать заново.** Класс сервиса переехал в `com.onthecrow.wallper.core.engine.impl`. До релиза это ничего не стоит, после релиза менять его нельзя.
3. **Пикер.** Вместо `ACTION_OPEN_DOCUMENT` с `*/*` используется системный photo picker (`PickVisualMedia(ImageAndVideo)`). Отмена выбора больше не показывает «File isn't supported».
4. **Диалог экспорта** показывает только прогресс. Строки «Remaining/Elapsed time: N/A» были наследием FFmpeg и всегда показывали N/A.
5. **Копирование файлов** — через `InputStream.copyTo` / `File.copyTo`. Старый цикл по `available()` мог обрезать файл или дописать мусор, а `readBytes()` грузил видео в память целиком.
6. **Пропорции кропа** считаются через `WindowMetricsCalculator.computeMaximumWindowMetrics`, а не через сохранённое в DataStore разрешение Activity.
7. **Метаданные видео** читаются один раз при создании обоев и лежат в БД. Раньше сервис читал их при каждом старте движка (TODO из `GLWallpaperEngine`).
8. **Сохранение** идёт на IO. Раньше копирование и чтение метаданных шли на main-потоке.
9. **Ошибка подготовки файла** (не удалось скопировать или прочитать) показывает диалог ошибки, а раньше приложение могло упасть.

---

## 7. Найдено по пути (отложено)

> **Коротко.** По твоему решению сейчас не трогаем, но до релиза это нужно закрыть.

**Блокеры релиза**
1. В [ErrorDialog.kt](feature/editor/ui-impl/src/main/kotlin/com/onthecrow/wallper/editor/view/ErrorDialog.kt) осталась тестовая строка, которую нельзя показывать пользователям. Да и весь диалог со stack trace не годится для релиза.
2. В [app/build.gradle.kts](app/build.gradle.kts) release-сборка подписывается debug-ключом.
3. Картинки: редактор их принимает, а сервис показывает вместо них плейсхолдер (см. TODO).
4. Пункт «Редактировать» в меню ничего не делает (см. TODO).

**Проверить**
1. Стоит `allowBackup="true"`, а `backup_rules` и `data_extraction_rules` шаблонные. В бэкап попадёт БД с путями к файлам, а видео не влезут в квоту.
2. Превью (`thumbnails/`) лежат во внешнем каталоге DPS-контекста, а сервис читает их для `WallpaperColors`. Проверить в direct boot.
3. Компонент кроппера, похоже, основан на SmartToolFactory/Compose-Cropper. Проверить лицензию и атрибуцию.

---

## 8. TODO и отложенное

> **Коротко.** Четыре задачи отложены. В коде на них стоят `TODO` со ссылкой на этот файл.

1. **Картинки как обои** (решение 7-5). Нужны `ImageWallpaperEngine` в `core:engine:impl` на базе `BitmapWallpaperRenderer` с кропом и превью картинки в редакторе. Сейчас картинки уходят в плейсхолдер — `WallpaperEngineFactory.kt`.
2. **Редактирование** существующих обоев. `EditorDestination` получает `source: New(uri) | Existing(id)`, в `wallpapers:logic-api` появляется `GetWallpaperUseCase`. Пере-экспорт делается, только если изменились кроп или клип. Точки входа: `EditorDestination.kt`, пункт меню в `WallpapersScreen.kt`.
3. **Экспорт в фоне** (решение 7-4). Сейчас экспорт привязан к экрану и отменяется, если с него уйти. Вариант — WorkManager. Тогда `VideoExporter` можно вынести в отдельный модуль: у него появится второй потребитель.
4. **Опционально:** Konsist-правила из elixapp (`internal` в `-impl`, Navigation3 только в `navigation:impl`), detekt, тест Hilt-графа, Room-миграции с тестами после релиза.

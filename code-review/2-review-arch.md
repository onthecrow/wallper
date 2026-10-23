# Ревью: Senior Android Architect (ARCH)

## Вердикт
Основа архитектуры хорошая: порты, sealed-спецификации, политики на чистом JVM, защита плеера и GL от зависаний. Чёрная дыра встроена в неё без нарушения правил зависимостей. Слабое место — сама чёрная дыра. Её движок на 464 строки скопировал супервизию GL из видеодвижка, и тестов на него нет. Общий рантайм процедурных обоев заперт в `core:blackhole:*`. Настройки и кеши отданы наружу как конкретные синглтоны из GL-модуля. Поэтому вторые встроенные обои сегодня — это копирование порядка тысячи строк (оценка). Отдельно: в кеше превью есть баг, который видит пользователь (ARCH-2). А `ModuleBoundaries.kt` проверяет меньше, чем обещает CLAUDE.md.

## Находки

**ARCH-1 · major · god-class движка ЧД, супервизия GL продублирована**
- **Где:** `core/engine/impl/.../engine/BlackHoleWallpaperEngine.kt` (весь файл); `GLWallpaperEngine.kt:71,128-160,162-185,188-191`.
- **Проблема.** В одном классе три слоя:
  - Android: SensorManager, receiver power save, thermal listener, Choreographer, `setFrameRate`, Display;
  - политика кадров, качества и таблиц: `stillDrawn`, `pacedFps`, `applyQuality→loadTables`;
  - жизненный цикл GL-потока: inner `Gl`, heartbeat, restart, `GlRestartPolicy`, `SURFACE_DESTROY_WAIT_MS`.
- **Третий слой — копия из `GLWallpaperEngine`, и копии уже разошлись:**
  - в видео heartbeat идёт через `collectLatest(framesExpected)` + `expectedSinceMs`, то есть отсчёт с момента, когда кадры снова ожидаются;
  - в ЧД (:397-407) это опрос раз в 500 мс со `startedAtMs` и чтением `visible`/`appliedFrameRate`;
  - константы скопированы: :459-462 против :188-191.
- **Тестов на оркестровку нет.** При этом для такого же кода (supervisor, RenderGuard) проект сам держит правило «Android за портами, JVM-тесты на виртуальном времени».
- **Почему важно:**
  - восстановление GL уже переписывали дважды; теперь каждую правку нужно делать в двух местах, а со вторыми встроенными обоями — в трёх;
  - гипотеза: в ЧД возможен ложный рестарт сразу после выхода из STILL, если GL-поток спал в `lock.wait()` больше 2 с. Окно маленькое.
- **Фикс:**
  1. Вынести `SupervisedGlThread` в `core:gl`, рядом с `GlRestartPolicy` и `RenderGuard`. Он создаёт `WallpaperGLThread` по фабрике рендерера, держит heartbeat (вариант из видеодвижка) и restart policy, отвечает за `onSurfaceChanged/Destroyed(wait)` и `setFramesExpected`, а при отказе дёргает `onGiveUp → host.showPlaceholder()`. На нём живут видеодвижок, ЧД и плейсхолдер.
  2. Решения ЧД вынести в чистый `BlackHoleFrameLoop` на JVM.
     - Входы: `setVisible`, `onThermal`, `onPowerSave`, `onSettings`, `onVsync(nanos)`, `onGpuTime`.
     - Выход — порт: `draw`, `setFrameRate`, `setQuality`, `requestTables`, `setGyroEnabled`.
     - В движке остаётся Android-адаптер примерно на 150 строк. Тесты — по образцу `EngineSupervisorTest`.

**ARCH-2 · major · кеш превью встроенных: нестабильный ключ и устаревание**
- **Где:** `core/engine/impl/.../BuiltInPreviewsImpl.kt:38,41`; `feature/wallpapers/ui-impl/.../WallpapersViewModel.kt:41,49,89-90`.
- **Проблема (а), ключ.** Имя PNG строится из `settings.hashCode()`. В `BlackHoleSettings` есть enum `quality: QualitySetting`. У enum `hashCode()` — identity hash; на ART его зерно берётся от времени, поэтому в каждом процессе он другой. Что происходит на каждом холодном старте:
  - имя файла новое;
  - старый PNG удаляется (:41);
  - картинка заново рендерится через EGL pbuffer, вместе с загрузкой таблиц.
- **Проблема (б), устаревание.** VM запрашивает превью один раз за жизнь (`requestedPreviews`). Экран списка — стартовый и живёт всю сессию.
- **Сценарий:** пользователь меняет экспозицию и жмёт «назад» — в списке старая картинка до перезапуска приложения. А после перезапуска — секунды GPU и CPU впустую, хотя ничего не менялось.
- **Фикс:**
  - стабильный ключ: канонический JSON настроек → CRC32/SHA-1, или явный `cacheKey()`;
  - порт сделать потоковым: `observe(wallpaper): Flow<String?>` = `settings.debounce().mapLatest { render }`; VM подписывается один раз;
  - Mutex по ключу, чтобы два одновременных рендера не писали в один файл.

**ARCH-3 · major · общий рантайм процедурных обоев заперт в `core:blackhole:*`**
- **Где:** `physics/FramePacing.kt`, `GyroTracker.kt`, `Motion.kt`, `Geometry.kt`; `renderer/Gl.kt` (`ShaderLibrary`, `GlProgram`, `RenderTarget`, `GpuTimer`, `GlCaps`), `Bakes.kt` (`BloomChain`), `BlackHolePreviewRenderer.kt` (EGL pbuffer).
- **Проблема: «physics» — это не физика.** Там FramePacing, QualityController, ThermalLevel, Spring, OneEuroFilter, Phases, Quaternion, GyroTracker. Их сигнатуры без нужды завязаны на типы ЧД:
  - `FramePacing.targetFps(frames: FrameParams, quality: QualityLevel)` использует только fps-поля и `maxFps*`;
  - `GyroTracker.onSample(params: InputParams)` использует только gyro-поля;
  - `QualityLevel` смешивает общее (`renderScale`, `maxFps*`) со специфичным для ЧД (`tableAlpha`, `diskCrossings`, `starLayers`);
  - `ShaderLibrary` жёстко привязан к `assets/blackhole/`.
- **Почему важно.** MOON_EARTH_RESEARCH §9 сам оценивает вынос в 2–3 дня. Без него вторые обои либо тянут `core:blackhole:*` (вместе с LensMath), либо копируют код.
- **Фикс.** Полный вынос — первым шагом вторых встроенных обоев, не раньше: сейчас потребитель один.
  - `core:scene` (JVM): Spring, OneEuroFilter, Phases, Vec3/Quaternion, `GyroTracker(GyroParams)`, `FramePacing(FrameBudget)`, `QualityController`, ThermalLevel;
  - ES3-тулкит в `core:gl`: `ShaderLibrary(assetDir)`, `GlProgram`, `RenderTarget`, `GpuTimer`, `GlCaps`, `BloomChain`, `OffscreenEgl`.
  - Сейчас — только дешёвая подготовка: убрать типы ЧД из сигнатур `FramePacing` и `GyroTracker`.

**ARCH-4 · major · настройки и кеши — конкретные синглтоны в GL-модуле**
- **Где:** `core/blackhole/renderer/.../BlackHoleSettings.kt:24,158`, `LensTablesRepository.kt`; `feature/blackhole/ui-impl/.../source/BlackHoleSettingsSource.kt`, `LensTablesSource.kt`.
- **Проблема.**
  - Модель настроек — чистый Kotlin, импортирует только physics. Она и хранилище на SharedPreferences (`@Singleton`, Hilt) лежат в модуле рендерера, и рендерер из-за этого стал ещё и Hilt- и data-модулем.
  - Ядро отдаёт конкретные классы. Поэтому фича ради фейков сама заводит интерфейсы-обёртки `BlackHoleSettingsSource` и `LensTablesSource` — зависимость перевёрнута.
  - У других потребителей (`engine:impl`, `BuiltInPreviewsImpl`) таких швов нет, и они без тестов.
- **Фикс:**
  - `BlackHoleSettings` + `applyTo` + диапазоны перенести в `core:blackhole:physics` (JVM);
  - в ядре объявить интерфейсы `BlackHoleSettingsRepository` и `LensTablesSource`;
  - реализации (prefs и файловый кеш) положить в небольшой `core:blackhole:data` с `@Binds`;
  - рендерер оставить чистым GL без Hilt, обёртки в `feature/blackhole/ui-impl/source/` удалить.

**ARCH-5 · major · `ModuleBoundaries.kt` не обеспечивает правила из CLAUDE.md**
- **Где:** `build-logic/.../ModuleBoundaries.kt:18-28,60-80`.
- **Что не проверяется:**
  - «Only :app depends on -impl»: проверяются только `api → impl` и зависимость от чужой фичи. Проходят `feature:x:ui-impl → feature:x:logic-impl`, `feature:* → core:engine:impl`, `core:a → core:b:impl`.
  - Направление слоёв внутри фичи: `logic-* → ui-*` пройдёт.
  - `UI_FREE_MODULES` — ручной allowlist. Новые `core:scene`/`core:render` из ARCH-3 выпадут из проверки, пока их не впишут.
  - Смотрятся только прямые зависимости, и только `:core:designsystem` и группа `androidx.compose`. Но `:core:navigation:api` сам собран с Compose-плагином (`FeatureEntry` с `@Composable`), и зависимость UI-free модуля от него проверка пропустит.
  - «`logic-api` — JVM» не проверяется.
  - «Всё в `-impl` — `internal`» не проверяется и уже нарушено: публичные `ImageCropper`, `CropDefaults`, `DynamicCropState`, `Modifier.crop`, `VideoFrameDecoder` в `feature/editor/ui-impl/.../cropper/`; публичный `sealed class WallpaperEngine` в `engine:impl`.
- **Сценарий:** ради `Destination` для реестра встроенных (ARCH-6) сервис подключают к `core:navigation:api`. Compose попадает в сервис, работающий в direct boot, и сборка не падает.
- **Фикс:**
  1. Зависимость на `impl`/`-impl` разрешить только `:app`.
  2. Внутри фичи разрешить только `ui-impl → ui-api|logic-api` и `logic-impl → logic-api`.
  3. Инвертировать проверку: всё в `core` — UI-free, кроме явного `UI_MODULES` = {designsystem, navigation:*, mvi}; зависимость UI-free → `UI_MODULES` запрещена.
  4. Для `logic-api` требовать плагин `wallper.jvm.library`.
  5. `internal` в `-impl` проверять через Konsist или detekt (это уже было в TODO удалённого документа).

**ARCH-6 · minor, со скрытым багом · знание о встроенных обоях размазано**
- **Где:**
  - `WallpaperEngineFactory.kt:30-33` — конструктор обрастает зависимостями ЧД;
  - `BuiltInPreviewsImpl.kt:25-27,36-41`;
  - `WallperWallpaperService.kt:57-67,189-190`;
  - `WallpapersViewModel.kt:98-100` — плюс зависимость `wallpapers:ui-impl → blackhole:ui-api`;
  - `WallpaperEngine.kt:3` — `sealed`, хотя `when` по подтипам нигде нет; это запирает все движки в `engine:impl`, а `EngineHost` — `internal`.
- **Что нормально.** Exhaustive `when` по enum заставит компилятор напомнить про фабрику, превью и настройки. Для одной встроенной обоины этого хватает.
- **Баг.** `builtInColors()` вызывается для любого `WallpaperSpec.BuiltIn` (:189-190) и всегда отдаёт чёрно-янтарные цвета ЧД. Вторые обои молча получат чужие `WallpaperColors`.
- **Фикс сейчас:** выбирать цвета по `BuiltInWallpaper` через exhaustive `when`.
- **Фикс при вторых встроенных:**
  - `BuiltInWallpaperProvider { createEngine; colors; renderPreview }` через `@IntoMap` из модуля каждой обоины;
  - `Map<BuiltInWallpaper, Destination>` из `ui-impl` фичи — `WallpapersViewModel` перестаёт знать о ЧД;
  - `WallpaperEngine` и `EngineHost` — публичный SPI;
  - движки создавать через `@AssistedFactory`, а не ручным конструктором на 6 аргументов.

**ARCH-7 · minor · встроенная обоина как строка медиатаблицы**
- **Где:** `SeedBuiltInWallpapersCallback.kt:15-21`, `WallpaperEntity.kt`, `DeleteWallpaperUseCaseImpl.kt:14`, `ObserveWallpapersUseCaseImpl.kt`.
- **Проблема.**
  - В строке встроенной обоины 14 NOT NULL медиаколонок заполнены фиктивными значениями: `type='IMAGE'`, `original_path=''`, нули.
  - Любой код, который читает `WallpaperEntity` в обход маппера, обязан сначала проверить `builtIn`. Delete это делает. Следующий потребитель — очистка осиротевших файлов, бэкап, «Редактировать» — может забыть.
  - Уникальность `built_in` держится только на `WHERE NOT EXISTS`, ограничения в схеме нет.
  - «Встроенные первыми» сортируются в памяти, а не в запросе.
- **Почему сейчас (гипотеза: приложение ещё не в сторе).** До релиза пересборка таблицы бесплатна. После — миграция и тест на каждое изменение.
- **Фикс:**
  - в ещё не выпущенной v2 сделать медиаколонки nullable (миграция через новую таблицу и copy);
  - `CREATE UNIQUE INDEX ON wallpapers(built_in)` (NULL в SQLite не конфликтуют) + `INSERT OR IGNORE`;
  - `ORDER BY built_in IS NULL, id` в DAO;
  - сам подход «строка на встроенную + сид при открытии» оставить: он удачно переиспользует активацию.

**ARCH-8 · minor · MVI: эффекты читают `state.value` по неявному контракту**
- **Где:** `core/mvi/.../BaseViewModel.kt`; `BlackHoleSettingsViewModel.kt:55,63`; `BlackHoleDebugViewModel.kt:40`.
- **Проблема.** Эффекты считают, что reducer уже свернул это же событие (в коде прямо написано «The reducer has folded the event in already»). Гарантия держится на трёх неявных вещах:
  - подписчик `stateIn(Eagerly)` создаётся в конструкторе базы раньше, чем `onEach` в наследнике;
  - `Main.immediate` резюмирует их по порядку слотов;
  - `suspend reduce` фактически не суспендится.
  Нигде это не задокументировано.
- **Сценарий:** reducer уходит в `withContext(Default)` → на `OnSettingsChanged` эффект берёт `wantedTablesKey` от старых настроек → новые таблицы не грузятся, `isTablesLoading` висит.
- **Фикс:**
  - в базе сделать один конвейер: `runningFold` по парам `(state, event)` и `protected suspend fun handle(event, newState)` после свёртки;
  - либо брать данные из события: здесь `lensTableKey(event.settings.applyTo(...))`;
  - зафиксировать правило в CLAUDE.md.

**ARCH-9 · minor · три хоста одного рендерера, у каждого своя обвязка**
- **Где:**
  - обои: `BlackHoleWallpaperEngine` + `WallpaperGLThread`;
  - настройки и debug: `feature/blackhole/ui-impl/.../gl/BlackHolePreview.kt:46`, `GLSurfaceView` в `RENDERMODE_CONTINUOUSLY`, своё время, freeze и статистика в `PreviewGlRenderer`;
  - список: `BlackHolePreviewRenderer.kt:53-75,109-120`, свой EGL и `chooseConfig`, копия `WallpaperGLThread.chooseEglConfig`.
- **Последствия.** Превью настроек рисует на частоте дисплея (90/120 Гц) на HIGH, без `FramePacing` и `QualityController`. Показанные FPS и GPU-время — не те, что у обоев, и экран настроек греет телефон. EGL-код живёт в двух копиях.
- **Фикс:**
  - `OffscreenEgl` в `core:gl`;
  - превью настроек на `WHEN_DIRTY` + Choreographer + тот же frame loop из ARCH-1 (минимум — `FramePacing.isDue`).

**ARCH-10 · minor · обработка гироскопа продублирована**
- **Где:** `BlackHoleWallpaperEngine.kt:83-84,367-394` против `feature/blackhole/ui-impl/.../gl/PreviewGyroscope.kt:27-29,49-70,74`.
- **Что повторяется:**
  - выбор сенсора `GAME_ROTATION_VECTOR → ROTATION_VECTOR`;
  - `getQuaternionFromVector` с перестановкой w/xyz;
  - dt по `event.timestamp`;
  - перевод поворота дисплея в градусы;
  - период 20 000 мкс.
- **Расхождение уже есть:** движок берёт дисплей через `DisplayManager.DEFAULT_DISPLAY`, превью — через `view.display`.
- **Фикс:** Android-класс `RotationInput` → `GyroSample(q, dtSec, rotationDeg)` (или сразу владеющий `GyroTracker`) в общем Android-модуле; оба потребителя на нём.

**ARCH-11 · minor, частично гипотеза · direct boot: thumbnails и temp во внешнем хранилище**
- **Где:** `core/storage/.../MediaStorageImpl.kt:18-20`; `WallperWallpaperService.kt:196-197`.
- **Проблема.**
  - `createDeviceProtectedStorageContext().getExternalFilesDir(null)` указывает во внешнее хранилище, а не в DE.
  - Сервис читает thumbnail в `computeColors`. Гипотеза: на FBE эмулированное external-хранилище недоступно до первой разблокировки, тогда цвета будут null.
  - Проверено по коду: `getExternalFilesDir` может вернуть null → `File(null, "temp")` превращается в относительный путь `temp`.
  - Этот пункт стоял в «Проверить» удалённого документа и так и остался.
- **Фикс:** thumbnails → `filesDir/thumbnails` в DE, temp → DE `cacheDir`.

**ARCH-12 · minor · у плейсхолдера осталась гонка при уничтожении surface**
- **Где:** `PlaceholderWallpaperEngine.kt:51-52`.
- **Проблема.** Этот diff закрыл гонку для видео и ЧД через `onSurfaceDestroyed(waitMs = 1_000)`. Плейсхолдер по-прежнему вызывает его с `waitMs = 0`. А показывается плейсхолдер как раз после исчерпания ремонтов, то есть когда устройство уже в плохом состоянии.
- **Фикс:** уйдёт само вместе с ARCH-1. До этого — передать тот же `waitMs`.

**ARCH-13 · minor · таблицы линзы держатся в памяти**
- **Где:** `LensTablesRepository.kt:34-40,75`; `Bakes.kt:13`; `BlackHoleRenderer.kt:129`.
- **Проблема.**
  - На HIGH одна таблица — 2 × 1024 × 1024 float = 8 МБ.
  - Синглтон держит до двух ключей, то есть около 16 МБ кучи, всё время жизни процесса обоев.
  - Каждый рендерер вдобавок держит свою таблицу через `LensTextures.tables`/`pendingTables` и после загрузки в GPU.
  - `onTrimMemory` не обрабатывается.
- **Почему важно:** долгоживущий процесс обоев с лишними 16 МБ раньше попадёт под LMK.
- **Фикс:**
  - CPU-копию держать только до загрузки в текстуру;
  - при потере контекста брать таблицу заново из репозитория (файловый кеш читается быстро);
  - в репозитории — `SoftReference` или одна запись, сброс по `onTrimMemory` из сервиса.

**ARCH-14 · minor · хранение настроек — ручной бойлерплейт**
- **Где:** `BlackHoleSettings.kt:173-252`, `model/SettingsSlider.kt`, `model/DebugControls.kt`.
- **Проблема.**
  - Одна новая настройка — 6–8 правок: поле, `applyTo`, KEY, `put*`, `get*`, слайдер или событие, reducer+VM, экран.
  - 35 ключей расписаны вручную, с переводами Float↔Double туда и обратно.
  - Диапазоны в `DebugSlider` — дубли литералами, а не `BlackHoleSettings.*_RANGE`: FOV 30..90, DISK_BRIGHTNESS 0..3, DISK_OPACITY 0..1, DISK_ROLL −45..45, BLOOM 0..2, EXPOSURE 0.25..4.
- **Фикс:**
  - `@Serializable BlackHoleSettings`, хранить одной JSON-строкой (`ignoreUnknownKeys`, `coerceInputValues`); `kotlinx-serialization-json` уже есть в каталоге;
  - legacy-ключ `star_sharpness` мигрировать один раз;
  - для вторых обоев — общий `JsonSettingsStore<T>`.

**ARCH-15 · minor · дублирование в VM**
- **Где:** `BlackHoleSettingsViewModel.kt:44-63,77`, `BlackHoleSettingsReducer.kt:16-27`, `BlackHoleDebugViewModel.kt:49`.
- **Проблема.**
  - `loadTables` (`requestedKey`, cancel, обработка ошибки) скопирован в обе VM.
  - Девять правок настроек описаны дважды: в reducer (`edit { copy }`) и в VM (`save { copy }`). Разойтись им ничего не мешает.
- **Фикс:**
  - события-правки как `sealed interface SettingsEdit { fun apply(s): s }` — reducer и VM вызывают один и тот же `apply`;
  - загрузку таблиц вынести в общий `LensTablesLoader` на `mapLatest`.

**ARCH-16 · minor · документация разошлась с кодом**
- **Проблема.** `ARCHITECTURE_MIGRATION.md` удалён в закоммиченном f03b9c9, но на него ссылаются:
  - `CLAUDE.md:4` — «Architecture, decisions … live in…»;
  - `ModuleBoundaries.kt:31`;
  - TODO в `WallpaperEngineFactory.kt:41`, `WallpapersScreen.kt:141`, `EditorDestination.kt:9`;
  - `WALLPER_PLAYER_DESIGN.md:9`.
- **Доки не в git.** `WALLPER_PLAYER_DESIGN.md`, `BLACKHOLE_PLAN.md`, `BLACKHOLE_TZ.md` из CLAUDE.md не отслеживаются: в git лежит единственный .md — сам CLAUDE.md.
- **Потеряно:** правило выделения модулей, причины «почему нет `core:database`», список релизных блокеров.
- **Фикс:** вернуть короткий `docs/ARCHITECTURE.md` и закоммитить дизайн-доки вместе с веткой.

**ARCH-17 · nit · debug-экран попадает в release**
- **Где:** `source/DebuggableBuild.kt:4-5`, `di/BlackHoleUiModule.kt:47-57`.
- **Проблема.** В release едут debug-экран, `reference.frag`/`diff.frag` и `LensMode`/`DebugView` внутри `BlackHoleRenderer`; скрыта только кнопка. KDoc утверждает обратное.
- **Фикс:** модуль `feature:blackhole:debug` через `debugImplementation` — или честно поправить KDoc.

**ARCH-18 · nit · мёртвый `ExoPlayerFactory`**
- **Где:** `core/player/.../ExoPlayerFactory.kt`.
- **Проблема:** публичный `@Inject`-класс нигде не используется и дублирует клиппинг из `ExoEngineFactory.mediaItem()`.
- **Фикс:** удалить.

**ARCH-19 · nit · мелочи**
- `WallperWallpaperService.kt:188-190`: цвета считаются по `activeWallpaper.value`, а не по показанной обоине. После `showPlaceholder()` упавшей ЧД система получает цвета ЧД для плейсхолдера.
- `BlackHoleSettingsState.params`, `wantedTablesKey`, `isTablesLoading` — геттеры, которые на каждом вызове заново строят `BlackHoleParams`.
- `BlackHoleSettingsStore` синхронно читает prefs в конструкторе, то есть на main-потоке.

## Что сделано хорошо
- **Порты и направление зависимостей.** `ActiveWallpaperSource`, `LiveWallpaperController` и `BuiltInPreviews` лежат в `engine:api`. `WallpaperSpec` и `Wallpaper` — sealed. Неизвестный ключ встроенной обоины превращается в null, так что откат на старую версию безопасен. Встроенная активируется тем же `UPDATE … is_active = (id = :id)`, без спецслучаев.
- **Математика и политики ЧД — на чистом JVM, с тестами:** FramePacing, QualityController, точное решение пружины, фазы из Long-наносекунд. Граница Android/логика правильная, осталось дотянуть её до оркестровки (ARCH-1).
- **Защита плеера и GL от зависаний.** EngineSupervisor, StallWatchdog, RetryPolicy и RenderGuard спрятаны за `ManagedEngine`/`RenderRepairs` и тестируются на виртуальном времени. Это образцовые швы.
- **Тест миграции на JVM** через настоящий `WallpapersDatabase.builder`, экспортированную схему и bundled SQLite: проверяются миграция, валидация схемы и сидинг.
- **Дисциплина direct boot.** БД, prefs, кеш таблиц, превью и лог — в DE. ES3 не помечен `required` в манифесте, без ES3 показывается плейсхолдер. В системном превью (`isPreview`) — качество LOW.

## Топ-5 приоритетов
1. **ARCH-2** — пользовательский баг и лишний расход батареи; исправляется дёшево.
2. **ARCH-1** — заодно закрывает ARCH-12.
3. **ARCH-4** — даёт швы для тестов движка и превью; основа для вторых обоев.
4. **ARCH-5** — нужно до выноса модулей из ARCH-3.
5. **ARCH-7** — до релиза бесплатно, после — миграции.

ARCH-3 и ARCH-6 (кроме бага с цветами) стоит делать первым шагом вторых встроенных обоев, не раньше.

# Ревью: Senior Android Developer (DEV)

Gradle не запускал и ничего не менял: всё ниже установлено чтением кода. То, что не проверено, помечено «гипотеза».

## 1. Вердикт
Код в целом аккуратный:
- физика вынесена в чистый JVM-модуль с содержательными тестами;
- MVI-слой дисциплинирован;
- миграция Room проверена на настоящем SQLite.

Слабое место — Android-обвязка вокруг GL. `BlackHoleWallpaperEngine` (464 строки, без единого теста) содержит реальный баг пейсинга на API < 30. Гонка между перезапуском GL и `release()` есть в нём же и в `GLWallpaperEngine`. Ещё два заметных пользователю бага в превью встроенных обоев (не обновляется после смены настроек; кеш не переживает перезапуск процесса) и путь к крашу в редакторе. Блокеров нет, но DEV-1…DEV-6 стоит закрыть до релиза.

## 2. Находки

**DEV-1 · major** · `core/engine/impl/.../engine/BlackHoleWallpaperEngine.kt:352-363, 397-407`
- **Проблема.** В `applyFrameRate` строка `if (target == appliedFrameRate || SDK_INT < R) return` выходит раньше, чем поле `appliedFrameRate` получает значение. На API < 30 оно навсегда остаётся −1. При этом `updateFramesExpected()` и `watchGlHeartbeat()` (строка 401, `appliedFrameRate == STILL`) используют его как единственный признак «кадров не ждём».
- **Сценарий.** Android 10 (API 29, слушатель thermal там уже есть), `THERMAL_STATUS_SEVERE`:
  1. `FramePacing` возвращает STILL, `framesExpected` становится false.
  2. GL-поток засыпает в `lock.wait()` без таймаута, `lastLoopAtMs` замирает.
  3. Пульс видит −1 ≠ STILL и через 2 с перезапускает GL; так три раза.
  4. `GlRestartPolicy` исчерпан, вызывается `showPlaceholder()`.
  5. Чёрная дыра остаётся заглушкой (`failedWallpaper`), пока пользователь не выберет другие обои, даже когда телефон остынет.
- На API 24–28 то же −1 держит RenderGuard взведённым и в режиме STILL.
- **Фикс.** В `updateFramesExpected` и в пульсе опираться на уже существующий `pacedFps`. `appliedFrameRate` оставить только для дедупликации вызова `Surface.setFrameRate`. Тест — см. DEV-6.

**DEV-2 · major** · `BlackHoleWallpaperEngine.kt:194-211, 409-424`; `GLWallpaperEngine.kt:113-125, 144-158`
- **Проблема.** `restartGl` проверяет `released` только в начале. Дальше он блокируется: `requestExitAndWait` до 1 с, в видео ещё `setVideoSurface(null)` до 2.5 с. После этого он безусловно делает `gl = Gl(...)` и стартует новый `WallpaperGLThread`. `release()` ждёт поток колбэков всего 500 мс и затем гасит тот `gl`, который видит в этот момент, то есть старый.
- **Сценарий.**
  1. GL завис, пульс запускает `restartGl`.
  2. Экран застыл, и пользователь меняет обои (события связаны, это не случайное совпадение).
  3. `release()` выходит по таймауту и останавливает старый поток.
  4. Поток колбэков достаёт новый GL-поток уже после release.
- Новый поток никто не остановит. Он живёт вечно: `framesExpected` = true, просыпается каждые 500 мс. И держит EGL-surface на том же `SurfaceHolder`, на котором сервис тут же создаёт следующий движок (см. DEV-7).
- **Фикс.** Повторно проверять `released` после блокирующих вызовов и перед созданием `Gl`. Надёжнее перенести весь teardown, включая `gl.thread.requestExitAndWait()`, в posted-блок на потоке колбэков, а в `release()` ждать его завершения. Тогда последний `gl` останавливает тот поток, который его создал.

**DEV-3 · major** · `feature/wallpapers/ui-impl/.../WallpapersViewModel.kt:40, 88-95`
- **Проблема.** Превью встроенных обоев запрашивается один раз за жизнь ViewModel (`requestedPreviews`). ViewModel привязана к NavEntry (`rememberViewModelStoreNavEntryDecorator`) и жива, пока поверх списка открыты настройки.
- **Сценарий.** Список → «Настройки» чёрной дыры → изменил диск или звёзды → назад: в карточке старая картинка. Тест `a built-in shows a placeholder until its preview is rendered, which happens once` закрепляет именно это поведение.
- **Фикс.** `BuiltInPreviews` отдаёт `Flow<String?>`: настройки → `debounce` → `mapLatest { render }`. ViewModel собирает его через `collectLatest`. Тест переписать на «после смены настроек превью перерисовано».

**DEV-4 · major** · `core/engine/impl/.../BuiltInPreviewsImpl.kt:38-41`
- **Проблема.** Имя кеш-файла строится из `settings.hashCode()`. В `BlackHoleSettings` есть enum `quality`, а `Enum.hashCode()` — это identity hash, свой в каждом процессе.
- **Сценарий.** При каждом холодном старте имя новое, `exists()` возвращает false, старый PNG удаляется, превью рендерится заново. Это EGL-контекст, компиляция 8 программ, запекание текстур, таблицы MEDIUM. Пользователь всё это время видит заглушку.
- Кроме того, в ключ входят поля, не влияющие на статичный кадр (gyro, launcher, idleFps, quality), и их смена вызывает лишние перерисовки.
- **Фикс.** Стабильный ключ: CRC32 от `toString()` параметров, влияющих на кадр, без input и frames. `toString()` data-классов стабилен, enum печатается по имени.

**DEV-5 · major** (закоммиченный код) · `feature/editor/ui-impl/.../cropper/Player.kt:27-42, 56-68`
- **Проблема.** Длительность берётся хаком `Handler.postDelayed(2000) { initialRange = player.duration }`. Если за 2 с плеер не подготовился, `duration` равен `C.TIME_UNSET` (≈ −9.2e18).
- Следующий `LaunchedEffect(videoRange, seek)` тогда вызывает `setEndPositionMs(отрицательное)`. `ClippingConfiguration.Builder` проверяет `>= 0` и бросает `IllegalArgumentException` в корутине композиции — это краш.
- Если пользователь двигает диапазон в первые 2 с, клип получается [0, 0].
- Отложенный Runnable не отменяется и трогает уже освобождённый плеер.
- **Сценарий.** Большой 4K-ролик на слабом устройстве, prepare дольше 2 с, пользователь двигает диапазон.
- **Фикс.** Длительность уже есть в `PreparedMedia.mediaInfo.durationMs` — передать её параметром. Альтернатива — ждать `STATE_READY` через listener. Позиции дополнительно клампить через `coerceAtLeast(0)`.

**DEV-6 · major** · `BlackHoleWallpaperEngine.kt:213-424` (тестов нет)
- **Проблема.** Вся логика решений перемешана с `Handler`, `Choreographer` и `SensorManager` в одном классе примерно с 15 изменяемыми полями: видимость, thermal, power save, качество, темп, `framesExpected`, пульс, гироскоп. JVM-тестов нет.
- CLAUDE.md для плеера и GL требует обратного: «Keep Android types behind ManagedEngine / RenderRepairs», тесты на виртуальном времени. DEV-1 сидит ровно здесь и поймался бы первым же тестом.
- **Фикс.** Вынести чистый `BlackHolePacer` в `core:blackhole:physics`, рядом с `FramePacing`.
  - Входы: `onVisible`, `onThermal`, `onPowerSave`, `onSettings`, `onVsync(nanos)`, `onGpuTime`.
  - Выходы: `targetFps`, `framesExpected`, `heartbeatArmed`, «рисовать ли кадр», «сменить ли качество».
  - Движок остаётся тонкой обвязкой. Тесты на фейковых часах, включая «API < 30 без setFrameRate».

**DEV-7 · major (гипотеза)** · `core/gl/.../WallpaperGLThread.kt:160-170, 348`; `BlackHoleWallpaperEngine.kt:417-420`; `GLWallpaperEngine.kt:154-158`
- **Проблема.** Если поток завис в драйвере, перезапуск бросает его после `join(1000)` и создаёт новый поток, который вызывает `eglCreateWindowSurface` на том же `ANativeWindow`. Окно принимает одного подключённого producer'а. Пока брошенный поток держит свой EGL-surface, подключение, по моему пониманию, падает (`EGL_BAD_ALLOC` / «already connected»).
- **Сценарий.** Цепочка: `surfaceBroken` → `ContextLost` → recreateContext → unrecoverable → новый restart → заглушка. Заглушка — тоже GL на том же окне и тоже не подключится. Итог — чёрный экран, пока система сама не пересоздаст surface.
- **Проверка на устройстве.** Debug-флаг с `Thread.sleep(10_000)` в `onDrawFrame`, затем смотреть logcat на `eglCreateWindowSurface`.
- **Если подтвердится.** Перезапуск имеет смысл, только если старый поток реально вышел (`!isAlive` после join). В остальных случаях сразу сдаваться. Это ещё и сократит машинерию перезапуска, а с ней и окно для гонок вроде DEV-2.

**DEV-8 · minor** · `GLWallpaperEngine.kt` и `BlackHoleWallpaperEngine.kt` целиком
- **Проблема.** Инфраструктура скопирована:
  - `HandlerThread` + `Handler` + scope;
  - `inner class Gl` с пересозданием;
  - `GlRestartPolicy`, цикл пульса, `release()` на `CountDownLatch`;
  - одинаковые константы.
- Копии уже разошлись. Видео считает тишину от `maxOf(lastLoopAtMs, expectedSinceMs)`, а это значение сбрасывается при каждом «ждём кадры». Чёрная дыра считает от `Gl.startedAtMs`, что защищает только 2 с после создания потока. Баг DEV-1 есть только в одной из копий.
- **Фикс.** Общий `RestartableGl` в `core:gl`: владеет текущим потоком, пульсом, политикой перезапусков и корректным release, так что DEV-2 чинится в одном месте. Движки передают фабрику рендерера и `framesExpected: StateFlow<Boolean>`.

**DEV-9 · minor** · `core/blackhole/renderer/.../LensTablesRepository.kt:36-41, 59-72, 75`
- **Проблема.** Одновременно живут три ключа таблиц:
  - обои — HIGH;
  - превью списка — MEDIUM (захардкожено в `BlackHolePreviewRenderer.kt:35`);
  - превью системного выбора обоев — LOW.
- Память — FIFO на 2 записи, а `write()` удаляет файлы всех ключей, которых нет в памяти.
- **Сценарий.**
  1. Открыли список и системный выбор обоев → HIGH вытеснен из памяти.
  2. Следующая генерация любого ключа удаляет файл HIGH.
  3. После перезагрузки обои генерируют HIGH заново в direct boot: секунды на всех ядрах.
- Заодно синглтон держит до ~14 МБ float'ов (8 МБ HIGH + 6 МБ MEDIUM) в куче вечно живого процесса, а `onTrimMemory` не обрабатывается.
- **Фикс.** Удалять файлы только при смене `(r_c, K, FORMAT_VERSION)`, но не quality. Память — LRU (`accessOrder = true`) со сбросом по `onTrimMemory`; CPU-копию после заливки в GPU можно перечитать с диска.

**DEV-10 · minor** · `BlackHoleWallpaperEngine.kt:78, 299-312`
- **Проблема.** У scope нет `CoroutineExceptionHandler`. Исключение в `tablesRepository.get` или `onSettings` (например, OOM на 8 МБ) уходит в uncaught handler и роняет процесс с обоями.
- `tablesKey = key` выставляется до успеха загрузки, поэтому при ошибке повтора не будет. `render` будет только очищать экран: обои чёрные навсегда, заглушки нет.
- **Фикс.** Обработчик, который логирует и вызывает `host.showPlaceholder()`. `tablesKey` ставить после успеха или сбрасывать в catch, как уже сделано в `BlackHoleSettingsViewModel.loadTables`.

**DEV-11 · minor** · `BlackHoleWallpaperEngine.kt:314-335`
- **Проблема.** `onVsync` перепланирует себя на каждый vsync, пока обои видны, даже при 24–30 fps. На 120 Гц это 120 пробуждений потока в секунду, из них около 90 пустых. Для always-on обоев это мешает CPU уходить в глубокий idle.
- **Фикс.** После отрисовки вызывать `postFrameCallbackDelayed` до следующего нужного кадра.

**DEV-12 · minor (горячий путь)** · `BlackHoleRenderer.kt:220, 257-262, 266, 272-289`; `BlackHoleScene.kt:183-189`; `BlackHoleWallpaperEngine.kt:381, 438`; `Gl.kt:62, 210-216`
- **Проблема.** На каждый кадр аллоцируется то, что зависит только от `params`:
  - `ramp.take(4)` + `List(...)`, `FloatArray(12)`;
  - `layers.take().map{}`, `FloatArray(4n)`, `FloatArray(3n)` с `averageRadiance` на слой;
  - `floatArrayOf().mapIndexed { floatArrayOf()[i] }` — боксинг в `List<Float>` плюс 4 массива.
- Остальное по кадру и по сенсору:
  - `doubleArrayOf(6)` в сцене;
  - `handler.post { onGpuTime(ms) }` на кадр (лямбда + Message + boxed Double);
  - 3 × `IntArray(1)` в `GpuTimer.poll`;
  - `FloatArray(4)` на каждый сэмпл гироскопа при 50 Гц;
  - около 50 lookup'ов `HashMap<String, Int>` с боксингом на кадр.
- В сумме около 1–2 тыс. объектов в секунду в неубиваемом процессе.
- **Фикс.** Uniform-массивы считать в `setParams`/`setQuality` по dirty-флагу. Scratch-массивы держать полями. Локации uniform'ов кешировать в поля сразу после линковки.

**DEV-13 · minor** · `BlackHoleRenderer.kt:360`; `BlackHoleWallpaperEngine.kt:273-276, 438`
- **Проблема.** `GpuTimer` создаётся всегда, потому что движок всегда задаёт `onGpuTimeMs`. При качестве не AUTO, в системном превью и после `QualityController.settled` результат выбрасывается, но запросы к GPU и пост сообщения на каждый кадр продолжаются вечно.
- **Фикс.** Ставить колбэк только для `AUTO && !isPreview`; после `settled` выключать таймер.

**DEV-14 · minor** · `BlackHoleRenderer.kt:385-405`
- **Проблема.** Обои компилируют все 8 программ, включая отладочные `reference.frag` (RK4 на 3000 шагов поверх sky/disk) и `diff.frag`, хотя рисуют только `LensMode.TABLE`. Компиляция и запекание текстур идут в одном `drawFrame` после каждого `setQuality` и пересоздания контекста.
- **Гипотеза.** На холодном шейдерном кеше слабого драйвера это может занять больше 2 с. `lastLoopAtMs` обновляется только в `awaitWork`, и пульс примет такую компиляцию за зависание.
- **Фикс.** Создавать отладочные программы лениво.

**DEV-15 · minor** · `feature/blackhole/ui-impl/.../BlackHoleSettingsScreen.kt:57-103`; `BlackHoleSettingsState.kt:35-41`
- **Проблема.** `OnFrameStats` приходит дважды в секунду, и около 30 строк `SettingsList` перекомпонуются ради одной цифры fps.
- `params` — геттер с `applyTo(BlackHoleParams())` (глубокое копирование), который за одну рекомпозицию вызывается дважды: через `toPreviewConfig` и через `isTablesLoading`. При перетаскивании слайдера — на каждый кадр.
- **Фикс.** Передавать в `SettingsList` только `settings` и нужные флаги. Статистику читать отложенно лямбдой или вынести в отдельный `StateFlow`. `params` и `wantedTablesKey` считать в редьюсере.

**DEV-16 · minor** · `feature/blackhole/ui-impl/.../gl/BlackHolePreview.kt:42-47`
- **Проблема.** Режим `RENDERMODE_CONTINUOUSLY`: превью на экране настроек рисует HIGH на частоте дисплея, до 120 fps, хотя обои ограничены 60/30. Телефон греется, а показанные fps и GPU-время не соответствуют тому, что будет на обоях.
- **Фикс.** `WHEN_DIRTY` с тем же пейсингом, что у обоев, или хотя бы `setFrameRate(60f)`.

**DEV-17 · minor** · `core/blackhole/renderer/.../BlackHoleSettings.kt:167-212, 215-259`
- **(а) Память и диск расходятся.** В память кладётся `next` с точными Double, на диск уходят Float. После Reset обои генерируют таблицы для `rc44.2`, а после перезапуска читают `44.20000076…`. Ключ другой — две лишние генерации.
- **(б) Listener не нужен.** Процесс один и писатель один. При этом `apply()` на главном потоке вызывает listener синхронно на каждый изменённый ключ (на Reset — до 35 полных `read()`) и порождает лишнюю эмиссию округлённых значений.
- **(в) Дублирование ключей.** 35 ключей перечислены вручную в двух местах; пропущенный ключ молча сбрасывает настройку.
- **(г)** `update` не атомарен.
- **Фикс.** Одна версионированная JSON-строка (`kotlinx.serialization` уже есть в каталоге), убрать listener, `_settings.update {}`.

**DEV-18 · minor** (закоммиченный код) · `core/engine/impl/.../WallperWallpaperService.kt:85-92, 183-209`
- **Проблема.** `stateIn(..., null)` смешивает состояния «ещё грузится» и «обоев нет». `onSurfaceCreated` почти всегда приходит раньше ответа Room. Поэтому при каждом старте сервиса, в том числе в direct boot, создаются и через миллисекунды выбрасываются:
  - `PlaceholderWallpaperEngine` — GL-поток и полноэкранный ARGB-битмап брендинга, около 18 МБ на 1440×3200;
  - ещё один полноэкранный битмап в `computeColors`.
- Заглушка мелькает на экране.
- Кроме того, `computeColors` в IO-корутине читает `activeWallpaper.value`, а не обои своего движка. Это редкая гонка с `onEach`.
- **Фикс.** Отдельное состояние Loading вместо null; `computeColors(wallpaper)`; для расчёта цветов брать битмап около 112×112.

**DEV-19 · minor** · `scripts/validate_blackhole_shaders.py:48-49`
- **Проблема.** Шейдеры проверяются как `#version 310 es`, а рантайм — `300 es`, так что возможности 3.1 пройдут проверку и упадут на устройстве. Проверяется только набор `K_MAX=3, STAR_LAYERS=3`; LOW собирается с `2/2` и не проверяется никогда. Скрипт не встроен в сборку.
- **Фикс.** `glslangValidator` без `-V` понимает `300 es` напрямую. Перебирать defines всех `QualityLevel`. Повесить на `check` с пропуском, если NDK нет.

**DEV-20 · minor** (закоммиченный код) · `feature/editor/logic-impl/.../export/TransformerVideoExporter.kt:71, 113, 138-146`
- **Проблема.** Из-за `flowOn(main)` `MediaMetadataRetriever.setDataSource` идёт на главном потоке. `release()` стоит в `.also` после бросающего `extractFloatMetadata`, поэтому при ошибке ретривер утекает. Ширина, высота и поворот при этом уже есть в `MediaInfo`.
- **Фикс.** Передавать `MediaInfo` из `PreparedMedia`, либо читать метаданные до `flowOn` на IO внутри `use {}`.

**DEV-21 · nit** · `BlackHoleWallpaperEngine.kt:380-394`; `feature/blackhole/ui-impl/.../gl/PreviewGyroscope.kt:30, 44-66`
- **Проблема.** Связка «rotation vector → кватернион → поворот дисплея → `GyroTracker`» написана дважды. Слушатель превью зарегистрирован без `Handler` и работает на главном потоке с частотой 50 Гц.
- **Фикс.** Один хелпер `GyroTracker.onRotationVector(values, ts, rotation, params)`; в превью передавать фоновый `Handler`.

**DEV-22 · nit** · `BlackHolePreview.kt:35-56`
- **Проблема.** `GLSurfaceView` создаётся в `remember` вне `AndroidView.factory`. Если узел пересоздадут (`movableContentOf`, lazy-контейнер), будет «child already has a parent». `dragMode` захватывается один раз.
- **Фикс.** Создавать view внутри `factory`.

**DEV-23 · nit** · `feature/blackhole/ui-impl/.../source/DebuggableBuild.kt:4-5`; `di/BlackHoleUiModule.kt:47-56`
- **Проблема.** В релизе debug-экран скрыт, но entry регистрируется всегда, а `reference.frag` и `diff.frag` поставляются и компилируются (см. DEV-14). Проверка флага в рантайме не даёт R8 вырезать этот код.
- **Фикс.** Регистрировать entry только в debug source set.

**DEV-24 · nit** · разное
- FQN вместо импорта: `BlackHolePreviewRenderer.kt:47, 109`.
- Синхронизированный `by lazy` для поля одного потока: `BlackHoleWallpaperEngine.kt:110`, нужен `LazyThreadSafetyMode.NONE`.
- `thermalListener: Any?` с кастами вместо вложенного `@RequiresApi(Q)`-класса: `:129-133`.
- Не-daemon executor держится вечно: `BlackHolePreviewRenderer.kt:31`.
- Шаблонные `app/src/test/java/com/onthecrow/wallper/ExampleUnitTest.kt` и `app/src/androidTest/.../ExampleInstrumentedTest.kt`.

## 3. Что сделано хорошо
- **Передача кадра между потоками.** Неизменяемый `FrameState` идёт через `AtomicReference` (`BlackHoleGlRenderer.submit`). Фазы считаются из `Long`-наносекунд через `floorMod` (`Phases.fraction`), так что float не дрейфует за дни аптайма. Пружина интегрируется точно и устойчива к любому dt.
- **Тестируемое ядро.** `core:blackhole:physics` — чистый JVM-модуль с содержательными тестами (`FramePacingTest`, `MotionTest`, `LensMathTest`, `SceneInputTest`). `RenderGuard` и `EngineSupervisor` тестируются на фейковых часах.
- **Миграция БД.** `WallpapersMigrationTest` гоняет боевой `WallpapersDatabase.builder` на bundled SQLite из экспортированной схемы v1, включая сидинг и повторное открытие.
- **Кеши.** Запись атомарная (tmp + rename), формат таблиц версионирован и в имени файла, и в заголовке (magic + `FORMAT_VERSION` + ключ).
- **Асинхронщина и жизненный цикл.**
  - Устаревшие ответы отбрасываются по ключу (`OnTablesLoaded`), старые загрузки отменяются.
  - `CancellationException` везде пробрасывается.
  - `onSurfaceDestroyed(waitMs)` честно выполняет контракт WallpaperService: EGL-surface уничтожается до возврата.

## 4. Топ-5 приоритетов
1. **DEV-2** — утечка GL-потока, которая ломает и следующие обои.
2. **DEV-1** — API 29 + перегрев → вечная заглушка.
3. **DEV-5** — краш в редакторе.
4. **DEV-3 + DEV-4** — превью встроенных обоев: одна зона, дешёвый фикс.
5. **DEV-6** — вынести пейсер и покрыть тестами; закрывает весь класс багов вида DEV-1.

Перед тем как вкладываться в DEV-2 и DEV-8, стоит проверить DEV-7 на устройстве: если гипотеза подтвердится, часть машинерии перезапуска GL можно просто удалить.

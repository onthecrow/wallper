# Wallper

Android live wallpaper app: the user crops a video, Wallper plays it as the live wallpaper.
Design docs: [WALLPER_PLAYER_DESIGN.md](WALLPER_PLAYER_DESIGN.md) (video playback and stalls), [BLACKHOLE_PLAN.md](BLACKHOLE_PLAN.md) (the black hole). Open review findings and the release plan: [CODE_REVIEW.md](CODE_REVIEW.md).

## Build and test

```bash
./gradlew assembleDebug unitTest lintDebug
```

- `unitTest` (from the convention plugins) runs `test` in plain Kotlin modules and `testDebugUnitTest` in Android ones; plain `testDebugUnitTest` skips the JVM modules.
- Lint warnings fail the build. Older ones are kept in each module's `lint-baseline.xml`; don't add to it, fix new warnings.
- CI (`.github/workflows/ci.yml`) runs `unitTest lintDebug assembleRelease validateBlackHoleShaders` on every pull request.
- Release signing reads the Play upload key from `~/.gradle/gradle.properties` or the environment: `WALLPER_UPLOAD_STORE_FILE`, `WALLPER_UPLOAD_STORE_PASSWORD`, `WALLPER_UPLOAD_KEY_ALIAS`, `WALLPER_UPLOAD_KEY_PASSWORD`. Without them the release build is unsigned; never put them in the repository. Publish with `./gradlew bundleRelease`.

## Modules

- `:app`: Application, MainActivity, `@StartDestination`. No feature code.
- `:core:*`: `model` (pure Kotlin), `diagnostics` (pure Kotlin), `coroutines`, `mvi`, `navigation:api|impl`, `designsystem`, `resources`, `storage`, `gl`, `player`, `engine:api|impl` (the WallpaperService). Core knows no built-in wallpaper by name.
- `:feature:<name>:{logic-api, logic-impl, ui-api, ui-impl}`: `wallpapers` (main screen, owns the wallpaper domain and Room), `editor` (add screen, media prep, Transformer export), `blackhole` (the built-in black hole: physics and the screens' ports in `logic-api`, the GLES 3.0 renderer, its engine and the settings store in `logic-impl`, settings and debug screens in `ui-impl`), `settings` (the app's settings screen; only `ui-api` and `ui-impl` until it keeps data of its own).

## Dependency rules (enforced by `build-logic/.../ModuleBoundaries.kt`)

- A feature sees another feature only through `ui-api` (navigation) and `logic-api` (domain). Only `:app` depends on `-impl`.
- Inside a feature, logic never depends on UI: `ui-impl` → `ui-api`, `logic-api`; `logic-impl` → `logic-api`. `logic-api` is plain Kotlin (`wallper.jvm.library`).
- `core` never depends on `feature`. When core needs feature data, core declares a port (`ActiveWallpaperSource`) and the feature implements it.
- `core:model`, `coroutines`, `storage`, `gl`, `player`, `engine:*` and every feature `logic-*` stay free of Compose and `core:designsystem`.
- Everything in `-impl` is `internal`; Hilt modules are the only public surface.

## Invariants

- The wallpaper service is `directBootAware`: everything it reads (Room DB, media files, thumbnails) lives in device-protected storage. `Application.onCreate` and the Hilt graph must not touch credential-encrypted storage.
- The editor's scratch files (`MediaStorage.temp*`: the copy of the picked media, its thumbnail, the export) live in device-protected `files/editor`, not `cacheDir`: the system empties the cache whenever it needs space, also mid-edit. They are cleared when an edit starts (`PrepareMediaUseCase`) and when the editor closes (`DiscardPreparedMediaUseCase`), and copied to permanent storage only once the wallpaper is saved; a failed save deletes its copies. No file outlives a wallpaper that wasn't saved.
- No cloud backup (`allowBackup="false"`, `data_extraction_rules.xml`): the videos don't fit its quota. A device-to-device transfer still moves everything.
- GL calls only from the EGL thread (`WallpaperGLThread`). Transformer only from the main looper (`TransformerVideoExporter` uses `flowOn(main)`).
- After the store release:
  - never rename `com.onthecrow.wallper.core.engine.impl.WallperWallpaperService`; the system stores it as the selected wallpaper;
  - every Room schema change needs a migration and a migration test; schemas are exported to `<module>/schemas`.

## Stall protection (see WALLPER_PLAYER_DESIGN.md)

The player side and the GL side recover independently; there is no shared abstraction between them.

- **Player (`core:player`).** `WallperPlayer` is a plain player to its consumers: `setVideoSurface`, `play`, `pause`, `release`, `state`. It never exposes its ExoPlayer engines.
  - Consumers hand it a surface and show the placeholder on `WallperPlayerState.Failed`.
  - `setVideoSurface` blocks until the decoder has let go of the previous surface, like ExoPlayer's, so the caller may release that surface right after.
  - `EngineSupervisor` keeps two engines, both created first thing in `run()` (on the player thread; ExoPlayer throws on any other): the active one on the surface and a standby one, prepared and paused without a surface. On a stall it releases the active one, hands the surface to the standby (`setSurface`, `seekTo`, `play`) and creates a new standby in place of the stalled one. Engines are recreated only at a switch and after a long pause (> 15 s, both are released); there is no background work.
  - `StallWatchdog` only reports a `StallCause`; it never fixes anything. The supervisor decides: switch to the standby, or `Failed` (content errors, or no standby left).
  - `RetryPolicy` paces switches: 4 with delays of 0, 1, 3 and 5 s before each, reset 30 s after the last one. A standby that broke while waiting is recreated at the switch and costs an attempt.
- **GL (`core:gl`).**
  - `VideoWallpaperRenderer` owns one video texture per EGL context and reports its surface through `onVideoSurfaceCreated`. A lost context's texture is released only after the new one shows a frame.
  - `RenderGuard` inside `WallpaperGLThread` repairs EGL.
  - `RestartableGl` (`core:gl`) owns the GL thread of the video and black-hole engines: the heartbeat (it runs only while frames are expected, so a hidden wallpaper never wakes for it; the first frames after a shader compile get 10 s), restarts limited by `GlRestartPolicy` (3 per 2 min), the teardown on the callback thread. It replaces a thread only once it has exited: one stuck in the driver still holds the window, so the engine gives up. The video engine detaches the player (`setVideoSurface(null)`) before the old GL thread exits.
  - A wallpaper that gave up shows the placeholder until the user picks a wallpaper, that one too (`ActiveWallpaperSource.activations`), or until it is visible again 5 min later.
- Every ExoPlayer call happens on the player thread (`setLooper`). GL objects are touched only on the GL thread.
- The supervisor, watchdog, policies and `RenderGuard` are tested on the JVM, with virtual coroutine time or fake clocks and fake engines. Keep Android types behind `ManagedEngine` / `RenderRepairs`.

## Built-in wallpapers and the black hole (see BLACKHOLE_PLAN.md)

- A built-in wallpaper is a row of `wallpapers` with `built_in = BuiltInWallpaper.key`. `built_in` has a unique index (schema v3), and the database seeds missing built-in rows on every open (`SeedBuiltInWallpapersCallback`, `INSERT OR IGNORE`); they are listed first (`ORDER BY built_in IS NULL, id`), activate like any wallpaper and are never deleted (the use case refuses too). Never rename a `BuiltInWallpaper.key`.
- `WallpaperSpec` is sealed: `Media` or `BuiltIn`. A built-in's feature binds a `BuiltInWallpaperProvider` (`core:engine:api`) with `@IntoSet`: its engine, colours, list stills and whether the device can draw it. The service, `WallpaperEngineFactory`, `BuiltInPreviews` and `LiveWallpaperController.isSupported` look it up by key (`BuiltInWallpapers`); a key without a provider shows the placeholder. A new built-in needs no change in core beyond its `BuiltInWallpaper` key.
- Every tunable of the black hole lives in `BlackHoleParams` (`feature:blackhole:logic-api`). Defaults start from BLACKHOLE_TZ.md §10; the look was tuned on a phone (BLACKHOLE_PLAN.md §9). User settings are applied on top with `BlackHoleSettings.applyTo`.
- `BlackHoleSettings` (`logic-api`, with its ranges) is `@Serializable`. `BlackHoleSettingsStore` keeps it as one JSON string in device-protected SharedPreferences, only the fields that differ from the defaults. A new setting is a new field with a default: old JSON still reads, unknown keys are ignored. Never rename a field; its name is the stored key.
- Lens tables depend only on `(r_c, N_α, N_φ, K)`. `LensTablesRepository` generates them once and caches them in device-protected `cacheDir/blackhole` (`LensTableFiles`: every quality of the current r_c, K and format). Nothing keeps them in memory: the renderer drops its copy once they are on the GPU and asks again after a lost context (`onTablesLost`). Bump `LensTables.FORMAT_VERSION` when the layout or the physics changes.
- Built-ins the device can't draw (no ES 3.0) are hidden from the list (`LiveWallpaperController.isSupported`).
- The camera orbits about the disk axis; the gyroscope and the launcher turn it on top in screen axes (`CameraPose.turned`), then the elevation is clamped. Their recoil is a principal-point shift (`FrameState.recoilX/Y`, half screen heights). The settings preview's drag turns the orbit itself (`setOrbitOffset`), with no recoil.
- `BlackHoleRenderer` (GL thread only) is shared by the wallpaper, the list preview (`BlackHolePreviewRenderer`, EGL pbuffer) and the settings and debug screens. The screens see it as `BlackHoleFrameRenderer` from `BlackHoleRendererFactory` (`logic-api`), handed to the preview through `LocalBlackHoleRenderers` by the navigation entries. Frames are paced by `Choreographer` on the engine's callback thread; animation phases come from `Long` nanoseconds, never a growing float.
- The setting «Optimised rendering» (`FrameParams.optimizedShaders`, on by default) picks the `OPTIMIZED` shader paths: the disk shaded only where a ray crosses it, a texel-for-texel composite at scale 1, no trigonometry per crossing, cheaper star maths. The original paths stay behind `#else` to compare; keep both compiling (the shader script checks both).
- The sky lens: `LensParams.strength` scales the physical bending (1 is physical), and `fadeStart`/`fadeEnd` (shadow radii, infinite end by default) fade it out; the shadow and the disk images stay physical. The settings' «Shape the lens» switch applies its three sliders; off keeps the defaults.
- `FrameParams.maxRenderScale` (the «Render resolution» slider) caps the quality level's render scale (`effectiveRenderScale`). Star σ is in screen pixels, so a lower scale only softens the stars.
- Hidden, the black hole does no work: no frames, no gyroscope, no GL heartbeat, and new lens tables (heat, settings) wait until it shows again. Only listeners that cost nothing until they fire stay registered.
- Frames are paced by a whole number of vsyncs (`FramePacing.vsyncsPerFrame`). A still frame (power save, heat) wakes only for an input that moves the picture, and the frame that draws it starts the power-save parallax.
- Shaders live in `feature/blackhole/logic-impl/src/main/assets/blackhole` (`#include` is resolved by `ShaderLibrary`). After editing them run `./gradlew validateBlackHoleShaders` (also part of `check`): it compiles them for every quality level without a device, as GLSL ES 3.00 with glslangValidator, or as 3.10 with the NDK's glslc when that is all there is.

## Editor preview and the trim bar

- `TrimBar` (`core:designsystem`) knows nothing of video: the playhead stays at its centre, it reports scrubs and handle moves (`onSeek`, `onTrimChange`) between `onDragStart` and `onDragEnd`, and reads the position through a lambda while drawing. Its geometry lives in `TrimBarMath.kt` and is tested on the JVM.
- `EditorPlayer` is one ExoPlayer with the whole video, so a handle can show any frame; it loops `EditorState.trim` itself, starting from its start. It plays while `isPlaying` (the user's choice), no drag is on and no export runs; drags turn on scrubbing mode. Pictures get no player.
- A player error goes to `Timber.e` and, as `OnPreviewFailed`, to the editor's error dialog; closing it leaves the editor, like media that can't be prepared.
- The strip's frames are key frames from `MediaMetadataRetriever` on `Dispatchers.IO`, one per tile within a pixel budget (`rememberVideoFrames`), held while an export runs.
- The player, its position and the frames live in the screen's composition, not in the ViewModel: the position changes every frame, which `runningFold` shouldn't carry, and frames are Bitmaps, which `logic-api` (plain Kotlin) can't. `EditorState` keeps only what the export and the user's choices need: `trim` and `isPlaying`.
- `ClipRange` with an end not after its start plays to the end of the video (`endMsOrNull`): the editor saves `0..0` when it doesn't know the length.

## Diagnostics (`core:diagnostics`)

- `Timber.w` is a breadcrumb and `Timber.e` a crash report: `ReportingTree` in `:app` forwards both to `Diagnostics.reporter` (`CrashReporter`). Log a failure the app recovers from with `Timber.e`, and the reason in the message.
- `Diagnostics.reporter` is global on purpose: GL threads and engines aren't built by Hilt, and it must work in direct boot. Context keys: `gl_renderer` (`WallpaperGLThread`), `black_hole_quality`.
- No crash reporting service yet: release builds log reports to logcat (`adb logcat -s WallperReport`). A service goes behind `CrashReporter`, initialized by hand in `Application.onCreate` without touching credential-encrypted storage.
- `ExitReasons` reports crashes and ANRs of earlier runs from `ApplicationExitInfo` (API 30+).

## MVI (`core:mvi`)

- `BaseViewModel(initialState, reducer)`: every event is folded by the reducer through `runningFold`. The event buffer is unbounded, so no event is dropped.
- Side effects live in the ViewModel's `eventFlow.onEach { when (event) ... }`. Their results come back as internal events through `onEvent(...)`.
- An effect takes its data from its event, never from `state.value`: the reducer folds the same event on another collector, and which one runs first isn't guaranteed. An effect that follows the state collects `state.map { ... }.distinctUntilChanged()`.
- One-off UI effects (Toast, starting an Intent) are events the screen collects from `eventFlow`. Navigation goes through the injected `Navigator`.
- The reducer is pure and returns `state` for side-effect-only events. Screens are stateless `(state, [eventFlow], onEvent, modifier)`.
- Test the reducer directly. Test the ViewModel with hand-written fakes and `Dispatchers.setMain(UnconfinedTestDispatcher())`. No mocking library.

## Adding a screen

1. `ui-api`: `@Serializable data class/object XDestination : Destination`.
2. `ui-impl`:
   - `XState : State`, `XEvent : Event`, `XReducer @Inject constructor()`;
   - `@HiltViewModel XViewModel`; use an assisted factory when it needs the destination;
   - `XScreen`.
3. `ui-impl/di/XUiModule`: `@Provides @IntoSet fun provideXEntry(): FeatureEntry<*> = featureEntry<XDestination> { destination, modifier -> ... }`.
4. `:app` depends on the new `ui-impl`, plus its `logic-impl` if there is one.
5. Reducer and ViewModel tests.

File names must be unique across the modules of one feature: they share a Kotlin package, and clashing `*Kt` classes break dex merging.

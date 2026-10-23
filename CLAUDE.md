# Wallper

Android live wallpaper app: the user crops a video, Wallper plays it as the live wallpaper.
Architecture, decisions and the migration log live in [ARCHITECTURE_MIGRATION.md](ARCHITECTURE_MIGRATION.md).

## Build and test

```bash
./gradlew assembleDebug testDebugUnitTest
```

JVM-only modules (`:core:model`, `*:logic-api`) run with `test`, not `testDebugUnitTest`.

## Modules

- `:app`: Application, MainActivity, `@StartDestination`. No feature code.
- `:core:*`: `model` (pure Kotlin), `coroutines`, `mvi`, `navigation:api|impl`, `designsystem`, `resources`, `storage`, `gl`, `player`, `engine:api|impl` (the WallpaperService), `blackhole:physics` (pure Kotlin) and `blackhole:renderer` (GLES 3.0).
- `:feature:<name>:{logic-api, logic-impl, ui-api, ui-impl}`: `wallpapers` (main screen, owns the wallpaper domain and Room), `editor` (add screen, media prep, Transformer export), `blackhole` (settings and debug screens of the built-in black hole; UI only).

## Dependency rules (enforced by `build-logic/.../ModuleBoundaries.kt`)

- A feature sees another feature only through `ui-api` (navigation) and `logic-api` (domain). Only `:app` depends on `-impl`.
- `core` never depends on `feature`. When core needs feature data, core declares a port (`ActiveWallpaperSource`) and the feature implements it.
- `core:model`, `coroutines`, `storage`, `gl`, `player`, `engine:*`, `blackhole:*` stay free of Compose and `core:designsystem`.
- Everything in `-impl` is `internal`; Hilt modules are the only public surface.

## Invariants

- The wallpaper service is `directBootAware`: everything it reads (Room DB, media files) lives in device-protected storage. `Application.onCreate` and the Hilt graph must not touch credential-encrypted storage.
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
  - `RestartableGl` (`core:engine:impl`) owns the GL thread of the video and black-hole engines: the heartbeat (armed only while frames are expected; the first frames after a shader compile get 10 s), restarts limited by `GlRestartPolicy` (3 per 2 min), the teardown on the callback thread. It replaces a thread only once it has exited: one stuck in the driver still holds the window, so the engine gives up. The video engine detaches the player (`setVideoSurface(null)`) before the old GL thread exits.
  - A wallpaper that gave up shows the placeholder until the user picks a wallpaper, that one too (`ActiveWallpaperSource.activations`), or until it is visible again 5 min later.
- Every ExoPlayer call happens on the player thread (`setLooper`). GL objects are touched only on the GL thread.
- The supervisor, watchdog, policies and `RenderGuard` are tested on the JVM, with virtual coroutine time or fake clocks and fake engines. Keep Android types behind `ManagedEngine` / `RenderRepairs`.

## Built-in wallpapers and the black hole (see BLACKHOLE_PLAN.md)

- A built-in wallpaper is a row of `wallpapers` with `built_in = BuiltInWallpaper.key`. The database seeds missing built-in rows on every open (`SeedBuiltInWallpapersCallback`); they are listed first, activate like any wallpaper and are never deleted (the use case refuses too). Never rename a `BuiltInWallpaper.key`.
- `WallpaperSpec` is sealed: `Media` or `BuiltIn`. `WallpaperEngineFactory` maps `BuiltIn(BLACK_HOLE)` to `BlackHoleWallpaperEngine`.
- Every tunable of the black hole lives in `BlackHoleParams` (`core:blackhole:physics`). Defaults start from BLACKHOLE_TZ.md §10; the look was tuned on a phone (BLACKHOLE_PLAN.md §9). User settings (`BlackHoleSettingsStore`, device-protected SharedPreferences) are applied on top with `BlackHoleSettings.applyTo`.
- Lens tables depend only on `(r_c, N_α, N_φ, K)`. `LensTablesRepository` generates them once and caches them in device-protected `cacheDir/blackhole`. Bump `LensTables.FORMAT_VERSION` when the layout or the physics changes.
- The camera orbits about the disk axis; the gyroscope and the launcher turn it on top in screen axes (`CameraPose.turned`), then the elevation is clamped. Their recoil is a principal-point shift (`FrameState.recoilX/Y`, half screen heights). The settings preview's drag turns the orbit itself (`setOrbitOffset`), with no recoil.
- `BlackHoleRenderer` (GL thread only) is shared by the wallpaper, the list preview (`BlackHolePreviewRenderer`, EGL pbuffer) and the debug screen. Frames are paced by `Choreographer` on the engine's callback thread; animation phases come from `Long` nanoseconds, never a growing float.
- Shaders live in `core/blackhole/renderer/src/main/assets/blackhole` (`#include` is resolved by `ShaderLibrary`). After editing them run `scripts/validate_blackhole_shaders.py` (the NDK's glslc); it catches compile errors without a device.

## MVI (`core:mvi`)

- `BaseViewModel(initialState, reducer)`: every event is folded by the reducer through `runningFold`. The event buffer is unbounded, so no event is dropped.
- Side effects live in the ViewModel's `eventFlow.onEach { when (event) ... }`. Their results come back as internal events through `onEvent(...)`.
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

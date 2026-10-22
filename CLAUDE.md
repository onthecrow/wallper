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
- `:core:*`: `model` (pure Kotlin), `coroutines`, `mvi`, `navigation:api|impl`, `designsystem`, `resources`, `storage`, `gl`, `player`, `engine:api|impl` (the WallpaperService).
- `:feature:<name>:{logic-api, logic-impl, ui-api, ui-impl}`: `wallpapers` (main screen, owns the wallpaper domain and Room), `editor` (add screen, media prep, Transformer export).

## Dependency rules (enforced by `build-logic/.../ModuleBoundaries.kt`)

- A feature sees another feature only through `ui-api` (navigation) and `logic-api` (domain). Only `:app` depends on `-impl`.
- `core` never depends on `feature`. When core needs feature data, core declares a port (`ActiveWallpaperSource`) and the feature implements it.
- `core:model`, `coroutines`, `storage`, `gl`, `player`, `engine:*` stay free of Compose and `core:designsystem`.
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
  - `GLWallpaperEngine` recreates a broken or hung GL thread without touching the player, limited by `GlRestartPolicy` (3 per 2 min). It detaches the player (`setVideoSurface(null)`) before the old GL thread exits.
- Every ExoPlayer call happens on the player thread (`setLooper`). GL objects are touched only on the GL thread.
- The supervisor, watchdog, policies and `RenderGuard` are tested on the JVM, with virtual coroutine time or fake clocks and fake engines. Keep Android types behind `ManagedEngine` / `RenderRepairs`.

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

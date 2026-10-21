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

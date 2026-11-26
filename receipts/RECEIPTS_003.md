# Vian Maps Implementation Receipts (003)

> Continuation of `receipts/RECEIPTS_002.md`. Strict 500-line cap enforced per file.

---

### Entry 2026-11-23T21:59:00Z — Phase AB-1: Independent Map / Notes Activation Boundary
* **Summary**: Implement a host-level Map/Notes destination shell and independent Map activation/deactivation boundary with process-level MapLibre SDK tracking, per-session ViewModelStore & resource teardown, durable camera/preference flush across transitions, and Placemarks long-press entry into a minimal Notes placeholder.
* **Exact files touched**:
  - `app/src/main/java/com/example/host/AppHostShell.kt` (created `AppHostDestination` [`MAP`, `NOTES`], `EXTRA_INITIAL_DESTINATION`, `resolveInitialHostDestination`, `AppHostShell`, and minimal `NotesPlaceholderScreen` with `Return to Map` button and `BackHandler`)
  - `app/src/main/java/com/example/MainActivity.kt` (resolved initial destination before conditional `mapFeature.activate(this)` and rendered `AppHostShell` instead of unconditional `mapFeature.Content()`)
  - `app/src/main/java/com/example/map/activation/MapActivationSeam.kt` (added `MapDeactivationResult`, `isRuntimeInitialized`, `ProcessMapRuntimeState`, `DefaultMapRuntimeInitializer` once-per-process guard, immutable `ActiveMapSession` with per-session `SessionViewModelStoreOwner`, `CompositionLocalProvider(LocalViewModelStoreOwner)` + `key(sessionId)`, idempotent `deactivate()`, and `activeSessionContent` test hook)
  - `app/src/main/java/com/example/map/engine/MapEngineController.kt` (added `isReleased`, `suppressCameraCallbacks`, `applyRestoredCamera(...)`, listener release guards, and idempotent `release()`)
  - `app/src/main/java/com/example/ui/screens/MapViewModel.kt` (added `hasLoadedPreferences`, local override guards in `init`, `launchPreferenceWrite` with `NonCancellable`, `flushPendingStateBlocking()`, `isCleared`, and fallback flush in `onCleared()`)
  - `app/src/main/java/com/example/data/preferences/AppPreferencesRepository.kt` (replaced top-level `preferencesDataStore` delegate with canonical-path `ConcurrentHashMap` DataStore factory cache preventing cross-test deleted-directory stale singletons while keeping a single process-wide DataStore instance on device)
  - `app/src/main/java/com/example/diagnostics/NetworkDiagnosticsMonitor.kt` (exposed `internal val isCallbackRegistered`)
  - `app/src/main/java/com/example/ui/screens/MainMapScreen.kt` (added `onOpenNotes` & `onRegisterControllerTeardown`, `LaunchedEffect(uiState.hasLoadedPreferences)` camera sync, `controller.isReleased` guard in `getMapAsync`, `controller.release()` in `DisposableEffect.onDispose`, and wired `BottomNavigationBar.onPlacesLongClick` to `onOpenNotes`)
  - `app/src/main/java/com/example/ui/components/BottomNavigationBar.kt` (updated comments for Placemarks long-press -> Notes without UI changes)
  - `app/src/main/res/values/strings.xml` (added `notes_placeholder_title`, `notes_placeholder_subtitle`, `notes_return_to_map` while preserving `app_name`)
  - `app/src/test/java/com/example/MapActivationSeamTest.kt` (added `ab1_01`–`ab1_07` covering cold-launch Notes independence, default Map activation, Map -> Notes session teardown, Notes -> Map re-activation & camera/style/internet restoration, Tap Placemarks & bottom bar preservation, repeated switching & idempotent deactivation, and activation failure/retry)
  - `BLUEPRINT.md`, `receipts/RECEIPTS_002.md`, `receipts/RECEIPTS_003.md` (updated blueprint & receipts audit trail)
* **What was actually done**:
  - Separated host destination routing (`AppHostShell`) from `MapFeatureActivation`, initializing `Map` only when `Map` is the active destination and keeping `MainMapScreen` removed from composition when `Notes` is displayed.
  - Tracked process-level native `MapLibre` initialization via `ProcessMapRuntimeState` so `MapLibre.getInstance()` and `OfflineManager` ambient cache setup run once per process while per-session `MapRuntimeDependencies`, `SessionViewModelStoreOwner` (`MapViewModel`), `MapEngineController`, `VisibleTileCoordinator`, `NetworkDiagnosticsMonitor`, and `MapView` are torn down cleanly on `Map -> Notes` and recreated cleanly on `Notes -> Map`.
  - Flushed pending `AppPreferencesRepository` camera/preference writes deterministically via `flushPendingStateBlocking()` before `viewModelStore.clear()`, and changed `DataStore` instance caching from a fixed single delegate to canonical file path so Robolectric tests remain isolated across test sandboxes while sharing one DataStore per file in production.
  - Preserved Room `map_app_master.db` at version 3, `Tap Placemarks -> PlacesPopupContent -> Open Memory Palace Workspace`, all Phase 4.3.1 features, and UI styling.
* **How it was verified**:
  - Local JVM/Robolectric unit tests (`JAVA_TOOL_OPTIONS="-Drobolectric.offline=true -Drobolectric.dependency.dir=/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7" gradle :app:testDebugUnitTest --console=plain`): `TEST-com.example.ExampleUnitTest.xml` 65/65, `TEST-com.example.MapActivationSeamTest.xml` 17/17, `TEST-com.example.PoiCategoryHighlightTest.xml` 8/8 — 90/90 passed (`failures="0"`, `errors="0"`, `skipped="0"`). Note: `MapActivationSeamTest` tests `MainActivity` cold-launch into `Notes` directly against `MapLibre.hasInstance() == false`, and exercises interactive `Map <-> Notes` Compose lifecycle with `MapActivationCoordinator`'s injectable `MapRuntimeInitializer` + `activeSessionContent` (substituting native GL `MapView`, since `libmaplibre.so` cannot execute on Linux JVM).
  - Container applet compilation (`compile_applet`): Verified after final edits.
  - On-device verification: Not tested on physical Android hardware or streaming emulator in this container (no emulator/ADB available).
* **Deviations from request**: None.
* **Known issues / follow-up**: Native `MapView` GL surface recreation/destruction and live MapLibre `OfflineManager` reuse across repeated `Map -> Notes -> Map` transitions on physical Android hardware require manual on-device QA. Workspace `.git` directory is absent in this AI Studio container (comparison against remote commit `20c03ee76d2c35d21d6f01fc33067ed96e1218d2` could not be executed via Git CLI); pre-existing 0-byte `remix-vian-helper.zip` at root and `.build-outputs/app-debug.apk` were left untouched. Stop at Phase AB-1 pending explicit approval for the next Notes phase.

---

### Entry 2026-11-24T13:00:00Z — Phase AB-1 Correction Pass: Ordered Async Camera Shutdown, MapView Lifecycle & Activation Recovery
* **Summary**: Execute the three focused Phase AB-1 corrections requested from source review: replace blocking `runBlocking` camera shutdown with ordered `suspend` persistence, fix state-aware `MapView` `onPause -> onStop -> onDestroy` sequencing on internal `Map -> Notes` disposal, and add visible Material 3 recovery UI for synchronous/asynchronous Map activation failures.
* **Exact files touched**:
  - `app/src/main/java/com/example/map/engine/MapViewLifecycleCoordinator.kt` (created `MapViewLifecycleTarget`, `NativeMapViewLifecycleTarget`, and `MapViewLifecycleCoordinator` tracking `isCreated`, `isStarted`, `isResumed`, and `isDestroyed`)
  - `app/src/main/java/com/example/ui/screens/MapViewModel.kt` (removed `flushPendingStateBlocking()` and unowned `CoroutineScope` in `onCleared()`; added `cameraStateLock`, `preferenceWriteMutex`, `cameraPersistenceFrozen`, and `suspend fun prepareAndFlushForDeactivation(): Result<Unit>`; routed composition preference writes through `launchPreferenceWrite`)
  - `app/src/main/java/com/example/map/activation/MapActivationSeam.kt` (changed `MapActivationBoundary.deactivate()` to `suspend fun deactivate(): MapDeactivationResult`, added `MapDeactivationResult.Failed`, preserved active session on flush failure or cancellation, and added `MapActivationLoadingScreen` / `MapActivationRecoveryScreen` instead of blank `Box` when failed/activating)
  - `app/src/main/java/com/example/host/AppHostShell.kt` (switched `openNotes` to coroutine-driven `mapFeature.deactivate()` with re-entrancy guard, kept `Map` active and surfaced `map_deactivation_error_dialog` on flush failure, and rendered `MapActivationRecoveryScreen` / `MapActivationLoadingScreen` on `MAP` when `Failed` or `Activating`)
  - `app/src/main/java/com/example/ui/screens/MainMapScreen.kt` (routed `MapView` `onCreate`, `LifecycleEventObserver`, `ComponentCallbacks2`, and `DisposableEffect.onDispose` through `MapViewLifecycleCoordinator` with `controller.release()` running inside `beforeDestroy`)
  - `app/src/main/java/com/example/data/preferences/AppPreferencesRepository.kt` (added optional `dataStore: DataStore<Preferences>` constructor parameter defaulting to canonical-path `getOrCreateDataStore`)
  - `app/src/main/java/com/example/map/activation/MapRuntimeDependencies.kt` (added optional `preferencesRepositoryProvider` constructor parameter preserving default behavior)
  - `app/src/main/res/values/strings.xml` (added recovery, loading, and deactivation error dialog string resources)
  - `app/src/test/java/com/example/MapActivationSeamTest.kt` (updated `ab1_03`/`ab1_04`/`ab1_06`/`ab1_07` for `suspend deactivate()` and added `ab1_fix_01`–`ab1_fix_12`)
  - `BLUEPRINT.md`, `receipts/RECEIPTS_003.md`
* **What was actually done**:
  - Removed blocking `flushPendingStateBlocking()` and unowned `onCleared()` `CoroutineScope`; enforced ordered non-blocking shutdown (`cameraPersistenceFrozen = true` -> capture final camera & dirty flag -> join earlier queued `pendingPreferenceJobs` -> write final camera if dirty inside `preferenceWriteMutex` on `Dispatchers.IO`) and surfaced cancellation/IO failures explicitly without switching to `Notes` on failure.
  - Enforced MapLibre `MapView` lifecycle sequencing across both Activity lifecycle forwarding and internal `Map -> Notes` Compose disposal (`beforeDestroy` stopping `VisibleTileCoordinator` and releasing `MapEngineController` before `onPause() -> onStop() -> onDestroy()`), without duplicating `onPause`/`onStop` after Activity backgrounding and with idempotent `dispatchDestroy`.
  - Replaced silent blank Map screen on activation failure (`Failed` or `Activating` transitioning asynchronously to `Failed`) with Material 3 `MapActivationRecoveryScreen` displaying the error reason, `Retry` (without duplicating runtime init or dependencies), and `Open Notes`.
* **How it was verified**:
  - Local JVM/Robolectric unit tests (`JAVA_TOOL_OPTIONS="-Drobolectric.offline=true -Drobolectric.dependency.dir=/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7" gradle :app:testDebugUnitTest --console=plain`): `TEST-com.example.ExampleUnitTest.xml` `tests="65" skipped="0" failures="0" errors="0"`, `TEST-com.example.MapActivationSeamTest.xml` `tests="29" skipped="0" failures="0" errors="0"`, `TEST-com.example.PoiCategoryHighlightTest.xml` `tests="8" skipped="0" failures="0" errors="0"` — 102/102 passed. Note: `ab1_fix_01`–`ab1_fix_05` exercise real `MapViewModel` + `PreferenceDataStoreFactory` / `ControllableDataStore`; `ab1_fix_06`–`ab1_fix_09` exercise `MapViewLifecycleCoordinator` with a recording JVM `MapViewLifecycleTarget` (`RecordingMapViewTarget`) and real `MapEngineController`; `ab1_fix_10`–`ab1_fix_12` exercise `AppHostShell` + `MapActivationCoordinator` / test `MapActivationBoundary` (native GL `MapView` rendering/destruction cannot run on Linux JVM).
  - Container applet compilation (`compile_applet`): Pending/run after doc update.
  - On-device verification: Not tested on physical Android hardware or streaming emulator in this container.
* **Deviations from request**: None (strictly limited to the three Phase AB-1 corrections, tests, blueprint, and receipts).
* **Known issues / follow-up**: Native GL `MapView.onPause()/onStop()/onDestroy()` ordering and surface teardown/recreation across `Map -> Notes -> Map` transitions on physical Android hardware still require on-device QA before final Phase AB-1 acceptance; workspace `.git` directory is absent in this container, preventing automated `git diff` against remote commit `0d26711f3afdf874216608729825748951970529`.

---

### Entry 2026-11-24T10:01:00-08:00 — Phase AB-1 Final Correction Pass: Deterministic Cancellation Test & Accurate Lifecycle Diagnostics
* **Summary**: Make the Phase AB-1 camera-shutdown cancellation test deterministic against freeze-and-wait state and fix `MapViewLifecycleCoordinator` to emit `DESTROYING` before teardown, `DESTROYED` only after `MapView.onDestroy()` succeeds, and `DESTROY_FAILED` with failure propagation and partial-failure idempotence when any teardown stage throws.
* **Exact files touched**:
  - `app/src/main/java/com/example/ui/screens/MapViewModel.kt` (added narrow internal testing hooks `onAwaitingPendingPreferenceWritesForTesting`, `isCameraPersistenceFrozenForTesting()`, `pendingPreferenceJobCountForTesting()` invoked inside `prepareAndFlushForDeactivation()` after `cameraPersistenceFrozen.set(true)` when awaiting active pending preference writes)
  - `app/src/main/java/com/example/map/engine/MapViewLifecycleCoordinator.kt` (added `DestructionState` [`ACTIVE`, `DESTROYING`, `DESTROYED`, `FAILED`], `MapViewLifecycleDiagnosticSink`, `hasDestroyFailed`, `isTerminated`, `failureStage`, `lastDestroyFailure`, `nativeDestroyInvoked` guard, `DESTROYING` log before teardown, `DESTROYED` log strictly after `target.onDestroy()` returns, and `DESTROY_FAILED` + exception rethrow on any stage throw without double native destruction)
  - `app/src/test/java/com/example/MapActivationSeamTest.kt` (rewrote `ab1_fix_03` deterministically without timing sleeps, added diagnostic stage checks to `ab1_fix_07`, and added `ab1_fix_13`–`ab1_fix_17` covering clean destruction stage ordering, `beforeDestroy`/`onPause`/`onStop`/`onDestroy` failures, non-swallowed exceptions, and partial-failure idempotence)
  - `BLUEPRINT.md`, `receipts/RECEIPTS_003.md`
* **What was actually done**:
  - Wired `onAwaitingPendingPreferenceWritesForTesting` right after `cameraPersistenceFrozen.set(true)` when `jobsSnapshot` contains active prior preference writes, then updated `ab1_fix_03` to block a `ControllableDataStore` write on a `CompletableDeferred` gate, wait until `prepareAndFlushForDeactivation()` signals entry into freeze-and-wait, reject a freeze-time probe camera move, cancel shutdown while the earlier write remains blocked, assert `CancellationException` propagation, active non-cleared session, immediate unfreeze, newer camera update acceptance, stalled write release without overwriting the newer state, and zero leaked preference jobs.
  - Updated `MapViewLifecycleCoordinator` so `dispatchDestroy` emits `DESTROYING` before running `beforeDestroy()`, `onPause()`, `onStop()`, and `target.onDestroy()`, records `DESTROY_FAILED` (with failing stage, reason, and `Throwable`) and rethrows the original exception without emitting `DESTROYED` if any stage fails, preserves best-effort single-shot native cleanup (`nativeDestroyInvoked`) so repeated `dispatchDestroy` calls remain idempotent after partial failure, and verified all paths with `ab1_fix_13`–`ab1_fix_17`.
* **How it was verified**:
  - Local JVM/Robolectric unit tests (`JAVA_TOOL_OPTIONS="-Drobolectric.offline=true -Drobolectric.dependency.dir=/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7" gradle :app:testDebugUnitTest --console=plain`): `TEST-com.example.ExampleUnitTest.xml` `tests="65" skipped="0" failures="0" errors="0"`, `TEST-com.example.MapActivationSeamTest.xml` `tests="34" skipped="0" failures="0" errors="0"`, `TEST-com.example.PoiCategoryHighlightTest.xml` `tests="8" skipped="0" failures="0" errors="0"` — 107/107 passed.
  - Container applet compilation (`compile_applet`): Run after doc updates.
  - On-device verification: Not tested on physical Android hardware or streaming emulator in this container.
* **Deviations from request**: Workspace `.git` directory is absent in this AI Studio container (`ls -la .git` returned `No such file or directory`), so Git branch/commit comparison against `3efdcb9fb414c2b0b9fd4e5561a4f82c38f17220` could not be performed via Git CLI.
* **Known issues / follow-up**: Physical Android hardware verification of live `MapView` teardown/recreation across `Map <-> Notes` and Activity destruction remains outstanding before final Phase AB-1 sign-off. Do not begin Notes implementation.

---

### Entry 2026-11-25T14:19:00-08:00 — Phase Notes-1: Complete Notes Stage 1 Implementation & Internet Toggle Snackbar Fix
* **Summary**: Fix `MapViewModel.toggleInternet()` snackbar state bug, upgrade Room `map_app_master.db` v3->v4 with normalized `notes` and `note_checklist_items`, and build standalone ColorNote-inspired Notes Stage 1 UI inside `AppHostShell` while preserving Phase AB-1 Map isolation and Memory Palace.
* **Exact files touched**:
  - `app/src/main/java/com/example/ui/screens/MapViewModel.kt` (changed `toggleInternet(): Boolean` to return the resulting internet boolean)
  - `app/src/main/java/com/example/ui/screens/MainMapScreen.kt` (updated `onToggleInternet` snackbar message to consume returned boolean)
  - `app/src/main/java/com/example/data/db/AppDatabaseContract.kt` (`CURRENT_VERSION = 4` + v4 table documentation)
  - `app/src/main/java/com/example/data/db/NotesEntities.kt` (created `NoteEntity`, `NoteChecklistItemEntity` with cascade FK + indices, `NoteWithChecklistItems`, `NotesDao`)
  - `app/src/main/java/com/example/data/db/AppDatabase.kt` (registered entities, `notesDao()`, `MIGRATION_3_4`, `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`)
  - `app/src/main/java/com/example/data/notes/NotePalette.kt` (created unified 9-color `NoteColor` palette with `YELLOW` default and contrast-safe header/paper tokens)
  - `app/src/main/java/com/example/data/notes/NotesModels.kt` (created `NoteType`, `NoteSortOrder`, `NoteChecklistItem`, `NoteRecord`, `NoteFormatting`)
  - `app/src/main/java/com/example/data/notes/NotesRepository.kt` (created local-first repository with atomic checklist ops, soft-delete, and Undo restore)
  - `app/src/main/java/com/example/ui/notes/NotesViewModel.kt` (created standalone `AndroidViewModel` with `saveMutex`-serialized autosave/discard, 100-step Undo/Redo, sort/search, multi-select, checklist dialog state, and `NotesShareHelper`)
  - `app/src/main/java/com/example/ui/notes/NotesUiComponents.kt` (created `NotesFiveSlotBottomBar`, `NoteAddChooserDialog`, `NoteColorPickerDialog`, `NotesStage2PlaceholderContent`)
  - `app/src/main/java/com/example/ui/notes/NotesListContent.kt` (created ColorNote flat pastel rows with `7.dp` strip and `NotesMultiSelectBottomBar`)
  - `app/src/main/java/com/example/ui/notes/NoteViewerEditorScreen.kt` (created View-first/Edit-first viewer/editor with Compose ruled paper `drawBehind`, `BasicTextField`, `ChecklistItemDialog`, interactive View checkboxes, overflow Share/Delete/Discard)
  - `app/src/main/java/com/example/ui/notes/NotesHostScreen.kt` (created standalone Notes root host screen with `Return to Map`, Search, Sort, `BackHandler`, and Undo snackbar)
  - `app/src/main/java/com/example/host/AppHostShell.kt` (wired `NotesHostScreen` in `AppHostDestination.NOTES`, removed `NotesPlaceholderScreen`)
  - `app/src/main/res/values/strings.xml` (added Stage 1 Notes string resources)
  - `app/src/test/java/com/example/ExampleUnitTest.kt` (added `MIGRATION_3_4` to `test43`/`test58` builders + `test66_verifyToggleInternetReturnsResultingStateForAccurateSnackbar`)
  - `app/src/test/java/com/example/MapActivationSeamTest.kt` (updated v4 migration & `notes_workspace_screen` assertions)
  - `app/src/test/java/com/example/NotesStage1Test.kt` (added 15 tests `notes_01`–`notes_15` covering migration, CRUD, View/Edit, fallbacks, 9 colors, 4 sorts, search, checklist dialog, Undo/Redo, multi-select, Share, autosave/discard, and AB-1/Stage-2 isolation)
  - `BLUEPRINT.md`, `receipts/RECEIPTS_003.md`
* **What was actually done**:
  - Delivered full Stage 1 Notes persistence (Room v4 non-destructive `MIGRATION_3_4`), ColorNote-inspired flat list + 9-color system, View-first/Edit-first ruled-paper editor with serialized autosave, in-memory Undo/Redo, `NEXT/CANCEL/OK` checklist dialog, long-press multi-select color/soft-delete with Undo snackbar, and native `Intent.ACTION_SEND` Share chooser, strictly separated from Map runtime and Memory Palace; kept Habits/Expense/Calendar as honest planned placeholders and omitted all deferred Stage 1 exclusions.
* **How it was verified**:
  - Local JVM/Robolectric unit tests (`JAVA_TOOL_OPTIONS="-Drobolectric.offline=true -Drobolectric.dependency.dir=/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7" gradle :app:testDebugUnitTest --console=plain`): **123/123 passed** (`TEST-com.example.ExampleUnitTest.xml`: `tests="66" skipped="0" failures="0" errors="0"`, `TEST-com.example.MapActivationSeamTest.xml`: `tests="34" skipped="0" failures="0" errors="0"`, `TEST-com.example.PoiCategoryHighlightTest.xml`: `tests="8" skipped="0" failures="0" errors="0"`, `TEST-com.example.NotesStage1Test.xml`: `tests="15" skipped="0" failures="0" errors="0"`).
  - Container applet compilation (`compile_applet`): `Build succeeded - the applet is compiled`.
  - On-device verification: Not tested on physical Android hardware or streaming emulator in this container (no emulator/ADB available).
* **Deviations from request**: None in functional scope; workspace `.git` directory is absent in this AI Studio container, preventing Git CLI commit/diff verification.
* **Known issues / follow-up**: Manual on-device QA required for ruled-paper scrolling/IME keyboard ergonomics, native `Intent.ACTION_SEND` chooser launch, and physical `Map <-> Notes` surface transitions; deferred features (Archive/Trash UI, tags, attachments, fold sections, code blocks, file import, global search, type filters, grid toggle, checklist drag-reorder, reminders, locks, Habits/Expense/Calendar) remain for later stages.

---

### Entry 2026-11-26T04:49:00-08:00 — Phase Notes-1.1: Notes Editor Ruled-Paper Alignment & Checklist Dual Add Item Corrections
* **Summary**: Fix ruled-paper baseline positioning, rule line contrast across all 9 `NoteColor` palettes, and Edit-mode top+bottom scrollable Checklist Add Item rows without changing Room schema, persistence, Undo/Redo, or AB-1 boundaries.
* **Exact files touched**:
  - `app/src/main/java/com/example/data/notes/NotePalette.kt` (tuned `editorRuleLineColor` across all 9 `NoteColor` entries from `0x1C..0x24` to `0x3D..0x55` while preserving all palette identities, headers, strips, and paper backgrounds)
  - `app/src/main/java/com/example/ui/notes/NoteViewerEditorScreen.kt` (added `computeRuledLinePositions`, `PlatformTextStyle(includeFontPadding = false)`, `LineHeightStyle.Alignment.Bottom`, `onTextLayout` baseline tracking on both Edit `BasicTextField` and View `Text`, `ChecklistAddItemRow` at top and scrollable bottom of `ChecklistNoteBody` in Edit mode with empty space between them and no empty-state text, and `LazyListState` index-0 snap on Edit entry from top of View mode)
  - `app/src/test/java/com/example/NotesStage1Test.kt` (added `notes_16`–`notes_18` verifying dual Add Item controls when empty/scrolled/hidden in View mode, snug baseline rule positioning + wrapped/multi-paragraph View/Edit scroll, and 9-color rule differentiation)
  - `BLUEPRINT.md`, `receipts/RECEIPTS_003.md`
* **What was actually done**:
  - Diagnosed faint/misaligned rules caused by low `11%–14%` rule alpha on pastel paper combined with default `LineHeightStyle.Alignment.Proportional` + unmeasured fixed `topPad + lineHeight` drawing that placed lines halfway between text rows. Synchronized `drawBehind` rules to measured `TextLayoutResult` visual baselines (`baseline + 3.5.sp.toPx()`) and continued empty-space rules at the measured line stride across both View and Edit modes.
  - Extracted `ChecklistAddItemRow`, added `checklist_add_item_bottom_button` as a scrollable `LazyColumn` footer in Edit mode, replaced the Edit-mode `"No items yet"` empty-state text with open space keeping both Add Item rows visible on empty checklists, and kept View mode free of both editing rows.
* **How it was verified**:
  - Local JVM/Robolectric unit tests (`JAVA_TOOL_OPTIONS="-Drobolectric.offline=true -Drobolectric.dependency.dir=/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7" gradle :app:testDebugUnitTest --console=plain`): **126/126 passed** (`ExampleUnitTest`: 66/66, `MapActivationSeamTest`: 34/34, `PoiCategoryHighlightTest`: 8/8, `NotesStage1Test`: 18/18; `0` failures/errors/skipped).
  - Container applet compilation (`compile_applet`): `Build succeeded - the applet is compiled`.
  - On-device / visual verification: Not tested on physical Android hardware or streaming emulator in this container (no emulator/ADB available, and the two referenced screenshots were not present in the turn payload).
* **Deviations from request**: None in code scope; visual inspection against the two reference screenshots on live emulator/device could not run inside the container environment.
* **Known issues / follow-up**: Perform on-device / streaming-emulator visual inspection of ruled-paper text/rule snugness, IME keyboard open/close, multi-line wrapping, all 9 colors, and empty/long checklist Edit-mode Add Item rows against the reference screenshots.



# Performance & Architecture Task Plan

Repository: `amresalehin/fossify-files`
Working branch: `ChatGPT`

This file is the actionable task list for the performance work.  
`PERFORMANCE_TRACKER.md` is the implementation log and decision record.

---

## 0. Baseline & Guardrails

- [x] Record the current architecture before major changes.
- [x] Add `PERFORMANCE_TRACKER.md`.
- [x] Keep changes incremental and reversible.
- [ ] Establish a clean, reproducible Android build in Android Studio/Codespaces.
- [ ] Capture baseline performance measurements on a real Android device.
- [ ] Record baseline:
  - [ ] cold app startup
  - [ ] first visible folder
  - [ ] opening a large folder
  - [ ] scrolling a large folder
  - [ ] opening Gallery
  - [ ] first visible gallery item
  - [ ] scrolling 10k / 50k / 100k media items
  - [ ] memory/RAM usage
  - [ ] CPU usage
  - [ ] storage I/O
  - [ ] thumbnail decode activity

## 1. Startup Responsiveness

Goal: show usable UI immediately without waiting for storage-wide work.

- [ ] Ensure no full-storage scan blocks startup.
- [ ] Ensure Gallery startup does not load the complete MediaStore dataset.
- [ ] Delay nonessential indexing until after first interactive UI.
- [ ] Make background indexing yield aggressively when the user is interacting.
- [ ] Review ViewModel initialization for unnecessary work.
- [ ] Review caches created during startup for memory pressure.
- [ ] Add startup timing instrumentation.
- [ ] Verify cold-start behavior on low-end hardware.

## 2. File Explorer Scalability

Goal: opening a folder should depend primarily on that folder, not total storage size.

- [x] Preserve paged file loading.
- [x] Keep folder/stat caches.
- [x] Keep background indexing separate from interactive folder loading.
- [ ] Verify very large directories (10k+ files).
- [ ] Verify repeated folder navigation does not trigger unnecessary disk reads.
- [ ] Verify sorting/filtering does not materialize the whole storage tree.
- [ ] Review file metadata/stat calls for unnecessary per-item I/O.
- [ ] Improve cancellation when leaving a folder before a load finishes.
- [ ] Measure and optimize scroll/jank on large directories.
- [ ] Add stress tests for nested directories and mixed file types.

## 3. Gallery Scalability — Paging 3

Goal: Gallery must remain responsive with very large media libraries.

### Timeline

- [x] Add AndroidX Paging 3 dependencies.
- [x] Add `MediaStorePagingSource`.
- [x] Add `MediaRepository`.
- [x] Expose gallery Paging flow from `UnifiedViewModel`.
- [x] Replace the main ALL/PHOTOS/VIDEOS timeline grid with Paging.
- [x] Use bounded Paging memory (`maxSize = 360`).
- [ ] Compile and fix any API/import issues.
- [ ] Test Paging against real MediaStore data.
- [ ] Verify refresh behavior.
- [ ] Verify filter switching.
- [ ] Verify pagination while rapidly scrolling.
- [ ] Verify behavior with 100k+ media items.
- [ ] Verify process recreation / configuration changes.

### Albums

- [ ] Remove dependency on the complete `allMediaItems` list.
- [ ] Build album discovery from MediaStore without loading every media item into memory.
- [ ] Create an efficient album model.
- [ ] Load album counts lazily or with provider-side aggregation where practical.
- [ ] Open an album through a filtered Paging source.
- [ ] Preserve fast album thumbnails.
- [ ] Test devices with hundreds/thousands of albums.

### Favorites

- [ ] Define a scalable favorite source.
- [ ] Avoid rebuilding the entire MediaStore dataset just to display favorites.
- [ ] Convert favorites to a Paging-compatible source.
- [ ] Ensure favorite/unfavorite updates invalidate only affected data.
- [ ] Test large favorite collections.

### Fullscreen Viewer

- [ ] Stop relying on the currently loaded Paging snapshot as the complete viewer dataset.
- [ ] Design a dedicated adjacent-item loader.
- [ ] Support previous/next across unloaded pages.
- [ ] Preload only a small number of adjacent items.
- [ ] Keep fullscreen memory bounded.
- [ ] Avoid decoding full-resolution images until needed.

## 4. Thumbnail Pipeline

Goal: thumbnails should be cheap, asynchronous, cancellable, and bounded.

- [x] Use fixed thumbnail request sizing.
- [x] Keep hardware bitmaps where appropriate.
- [x] Keep crossfade disabled for performance.
- [ ] Verify Coil requests are cancelled when grid items leave the viewport.
- [ ] Verify thumbnail cache sizing.
- [ ] Avoid duplicate requests for the same URI.
- [ ] Avoid unnecessary EXIF/metadata reads during thumbnail display.
- [ ] Prefer MediaStore/thumbnail APIs where they reduce decoding work.
- [ ] Review video thumbnail generation separately.
- [ ] Test rapid fling scrolling.
- [ ] Test mixed photo/video grids.
- [ ] Measure decode time and memory churn.
- [ ] Add thumbnail performance counters to the tracker.

## 5. Background I/O Scheduler

Goal: background work must never make interactive storage operations feel slow.

- [x] Keep `IoPriorityCoordinator.yieldIfInteractive()`.
- [x] Keep background indexing out of the main UI path.
- [ ] Audit every storage-heavy background operation.
- [ ] Add cooperative cancellation.
- [ ] Prioritize foreground folder/gallery work over indexing.
- [ ] Prevent multiple background scans from competing for storage.
- [ ] Add a single scheduler/queue for expensive background filesystem tasks.
- [ ] Limit concurrency based on device characteristics.
- [ ] Measure I/O contention during scrolling and folder navigation.

## 6. Search

Goal: fast search without requiring an enormous always-hot database.

- [ ] Audit the current search implementation.
- [ ] Separate filename search from metadata/semantic search.
- [ ] Avoid scanning the entire filesystem on every query.
- [ ] Reuse existing indexed information when available.
- [ ] Make search incremental/cancellable.
- [ ] Add debouncing.
- [ ] Return visible results progressively.
- [ ] Keep search memory bounded.
- [ ] Benchmark small, medium, and huge storage collections.

## 7. Metadata Architecture

Goal: metadata enrichment must not become a prerequisite for basic file/gallery browsing.

- [ ] Separate core filesystem metadata from optional enrichment.
- [ ] Keep EXIF parsing off the critical browsing path.
- [ ] Keep media dimensions/duration retrieval lightweight.
- [ ] Make expensive metadata extraction asynchronous.
- [ ] Persist only metadata that provides measurable UX value.
- [ ] Avoid creating a giant database solely to make basic browsing possible.
- [ ] Define cache invalidation rules.
- [ ] Define metadata versioning/migration rules.

## 8. Memory Management

Goal: predictable RAM usage even with 100k+ files/media.

- [ ] Audit all large in-memory collections.
- [ ] Remove unnecessary duplicate representations of the same dataset.
- [ ] Bound Paging windows.
- [ ] Bound thumbnail caches.
- [ ] Avoid retaining full MediaStore results.
- [ ] Avoid retaining full folder trees.
- [ ] Profile heap usage during rapid scrolling.
- [ ] Profile after repeated navigation.
- [ ] Test process recreation under memory pressure.
- [ ] Check for leaked Activity/Context references.

## 9. Cancellation & Concurrency

Goal: stale work should stop as soon as the user changes context.

- [ ] Audit coroutine scopes.
- [ ] Cancel folder loads when navigation changes.
- [ ] Cancel stale Gallery queries when filters change.
- [ ] Preserve coroutine cancellation instead of converting cancellation into ordinary errors.
- [ ] Cancel thumbnail requests when items leave composition.
- [ ] Prevent duplicate concurrent loads for the same folder/media query.
- [ ] Test rapid tab switching.
- [ ] Test rapid folder navigation.
- [ ] Test rapid Gallery filter changes.

## 10. Permissions & Storage Access

Goal: use the simplest permission model that gives fast, reliable access.

- [ ] Audit current storage permissions.
- [ ] Separate permission requirements for filesystem browsing and MediaStore.
- [ ] Verify Android 13+ media permissions behavior.
- [ ] Verify All Files Access behavior where applicable.
- [ ] Avoid requesting permissions that are not required for the active feature.
- [ ] Ensure permission checks do not trigger expensive rescans.
- [ ] Test denied/revoked permissions.
- [ ] Test removable/secondary storage if supported.

## 11. UI/Jank

Goal: keep Compose work small and predictable.

- [ ] Audit recompositions in File Explorer.
- [ ] Audit recompositions in Gallery.
- [ ] Use stable keys everywhere appropriate.
- [ ] Avoid passing large mutable collections through frequently recomposed UI.
- [ ] Keep expensive transformations outside composition.
- [ ] Verify LazyGrid item content types.
- [ ] Measure frame timing during rapid scrolling.
- [ ] Fix avoidable allocations in item rendering.

## 12. Stress Testing

Required test datasets:

- [ ] 1k files / media
- [ ] 10k files / media
- [ ] 50k files / media
- [ ] 100k files / media
- [ ] Mixed photos + videos
- [ ] Very large single directory
- [ ] Deep directory tree
- [ ] Large album count
- [ ] Large favorites collection
- [ ] Low-RAM device
- [ ] Slow storage
- [ ] Fast storage

For every dataset record:

- [ ] startup time
- [ ] first visible content
- [ ] folder/gallery open latency
- [ ] scroll FPS/jank
- [ ] peak RAM
- [ ] CPU
- [ ] storage reads
- [ ] thumbnail decode time
- [ ] cancellation behavior
- [ ] crash/ANR behavior

## 13. Current Known Technical Follow-ups

These are specifically related to the current Paging implementation.

- [ ] Compile the current branch.
- [ ] Verify `MediaStorePagingSource` query behavior on Android 10+.
- [ ] Verify API <26 query fallback.
- [ ] Verify `BUCKET_ID` / `BUCKET_DISPLAY_NAME` availability.
- [ ] Review use of deprecated `DATA` column and reduce path dependency where possible.
- [ ] Replace path-based Paging keys with a stable URI/type key.
- [ ] Replace numeric video-ID offset with a typed/stable key strategy.
- [ ] Preserve `CancellationException` in PagingSource loads.
- [ ] Verify Paging refresh keys preserve expected scroll position.
- [ ] Remove unnecessary legacy `loadMedia()` calls from the main timeline.
- [ ] Confirm album/favorite legacy loading is isolated from timeline browsing.

## 14. Definition of Done

The performance architecture is considered ready only when:

- [ ] App opens to usable UI without a storage-wide scan.
- [ ] Opening a folder does not depend on total storage size.
- [ ] Gallery timeline does not load the complete media library into memory.
- [ ] Gallery remains usable with 100k+ media items.
- [ ] Albums do not require a complete media list in memory.
- [ ] Favorites do not require a complete media list in memory.
- [ ] Fullscreen navigation works beyond the currently loaded Paging window.
- [ ] Thumbnail memory and decoding remain bounded.
- [ ] Background indexing yields to interactive work.
- [ ] Search is cancellable and does not freeze the UI.
- [ ] Large datasets do not produce avoidable GC/jank.
- [ ] No major operation requires a giant always-hot database.
- [ ] Permissions are minimal and predictable.
- [ ] Real-device stress tests pass without ANR/crash.
- [ ] Performance measurements are recorded before and after optimization.

---

## Current Priority Queue

1. Compile and validate the current Paging implementation.
2. Finish Gallery Albums without full-media loading.
3. Finish Favorites without full-media loading.
4. Fix fullscreen navigation across unloaded pages.
5. Audit cancellation and stale-work behavior.
6. Optimize thumbnail/cache behavior under rapid scrolling.
7. Establish 10k/50k/100k real-device benchmarks.
8. Optimize startup/background indexing.
9. Audit search scalability.
10. Complete memory/I/O stress testing.

Last updated: 2026-09-27

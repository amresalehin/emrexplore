# Performance 1.0 — Implementation Tracker

Working branch: `ChatGPT`
Baseline commit: `412398b4854619dba1d671f2b61b9719e76311da`

## Objective

Turn the current Fossify Files + Gallery implementation into a storage-first, low-memory, low-I/O Android file manager that remains responsive during browsing, scrolling, search, thumbnail loading, and background indexing.

This file is the running implementation log. Update it after each meaningful change.

## Current architecture snapshot

### File Explorer
- Directory listing already has page-based loading through `getFilesPaged()`.
- `UnifiedViewModel` keeps only the currently loaded file pages in UI state.
- Folder/stat caches already exist in `FileRepository`.
- Background indexing already uses `IoPriorityCoordinator.yieldIfInteractive()`.

### Gallery
- Gallery currently calls `getAllMediaData()`.
- `getMediaItems()` queries all MediaStore images and all MediaStore videos.
- The complete result is placed in `UiState.allMediaItems`.
- Filters operate by creating additional in-memory lists.
- Album grouping is performed with `groupBy` over the complete media list.
- Coil thumbnails already use fixed request sizing and disabled crossfade.
- **Main scalability problem:** Gallery is not yet Paging 3 based.

### Startup/indexing
- Initial file loading is asynchronous.
- Storage indexing can start automatically during ViewModel initialization when enabled.
- Indexing is depth-limited and yields during scanning, but it still competes for storage after startup.

### Performance instrumentation
Existing `PerformanceMonitor` records:
- folder-open latency
- paged-load latency
- first-visible latency
- I/O yields
- folder-cache hits
- stat-cache hits
- avoided disk reads

## Decisions

1. Do not rewrite `FileRepository.kt` wholesale.
2. Preserve existing behavior while introducing new performance architecture incrementally.
3. Keep each optimization independently reversible.
4. Gallery must eventually become Paging 3 based rather than loading the entire media library.
5. MediaStore should remain the primary source for gallery media.
6. Thumbnail generation/loading must remain independent from metadata-heavy work.
7. Background indexing must yield to interactive I/O.
8. No global "scan everything before UI" startup path.

## Work log

### Phase 0 — Baseline / architecture audit
Status: **IN PROGRESS**

Findings:
- File Explorer already has manual page loading.
- Gallery remains full-list based.
- Gallery album generation requires the complete media list.
- Background indexing has an I/O-yield mechanism.
- Existing performance metrics provide a useful starting point.

Next:
- Establish repeatable baseline measurements on a real device.
- Record startup, folder-open, gallery-open, scrolling, search, and indexing behavior before major architectural changes.

### Phase 1 — Startup responsiveness
Status: **NOT STARTED**

Target:
- First interactive UI should not depend on storage indexing.
- Defer nonessential indexing and expensive metadata work.
- Verify that folder opening remains responsive while indexing is active.

### Phase 2 — File Explorer scalability
Status: **PARTIALLY COMPLETE**

Already present:
- paged directory loading
- folder/stat caches
- background indexing
- interactive I/O yielding

Remaining:
- validate large-directory behavior
- eliminate redundant directory scans
- improve cancellation when navigation changes
- validate cache invalidation correctness
- measure memory and I/O under large folders

### Phase 3 — Gallery scalability
Status: **NOT STARTED**

Target architecture:

```
GalleryScreen
    ↓
GalleryViewModel
    ↓
MediaRepository
    ↓
MediaStorePagingSource
    ↓
MediaStore
```

Requirements:
- Paging 3
- deterministic ordering
- filter-aware queries
- album-aware queries
- bounded in-memory item count
- no `allMediaItems` dependency for normal browsing
- stable item keys
- thumbnail requests sized to rendered cells
- cancellation of obsolete loads

### Phase 4 — Thumbnail pipeline
Status: **PARTIALLY COMPLETE**

Already present:
- Coil
- fixed thumbnail request size
- hardware bitmaps
- crossfade disabled

Remaining:
- visible/near-visible prioritization
- prefetch tuning
- cancellation behavior
- avoid unnecessary duplicate requests
- verify thumbnail cache behavior

### Phase 5 — Background I/O scheduler
Status: **PARTIALLY COMPLETE**

Already present:
- `IoPriorityCoordinator`
- interactive activity tracking
- background yielding
- performance yield counter

Remaining:
- apply consistently to every background scanner/indexer
- improve cancellation
- avoid fixed delays where queue-based coordination is more appropriate
- test contention under sustained indexing

### Phase 6 — Search
Status: **PARTIALLY COMPLETE**

Existing:
- Room-backed fast search option
- live disk search option

Remaining:
- benchmark both paths
- ensure search does not scan unrelated storage
- debounce/cancel stale queries
- verify index freshness

### Phase 7 — Metadata
Status: **NOT STARTED**

Rules:
- no EXIF/XMP parsing during normal folder browsing
- metadata inspection only on demand
- background metadata enrichment must be cancellable
- never block thumbnail display

### Phase 8 — Stress testing
Status: **NOT STARTED**

Required scenarios:
- 100,000+ media items
- 10,000+ files in one directory
- sustained gallery scrolling
- gallery + indexing concurrently
- search during indexing
- rapid folder navigation
- repeated tab switching
- low-memory device
- slow storage

## Current next action

**Phase 0 → establish baseline, then implement the Gallery Paging 3 migration as the first major architectural change.**

Do not mark a phase complete without a measurement or test supporting the claim.

## Change log

| Date | Commit | Change | Result |
|---|---|---|---|
| 2026-09-27 | 412398b | Starting point audited | File Explorer paging exists; Gallery remains full-list based |

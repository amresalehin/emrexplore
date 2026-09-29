package com.example.ui.viewmodel

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExplorerPreferencesEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.IndexStatusEntity
import com.example.data.local.RecentEntity
import com.example.data.local.TrashEntity
import com.example.data.metadata.MetadataExtractor
import com.example.data.metadata.MetadataReport
import com.example.data.model.CategoryType
import com.example.data.model.FileItem
import com.example.data.model.MediaAlbum
import com.example.data.model.MediaItem
import com.example.data.model.SortOption
import com.example.data.model.StorageStats
import com.example.data.model.ViewMode
import com.example.data.media.MediaFilter
import com.example.data.media.MediaRepository
import com.example.data.media.FullscreenMediaSource
import com.example.data.media.MediaViewerWindow
import com.example.data.model.ConflictResolution
import com.example.data.model.FileOperationProgress
import com.example.data.model.OperationStatus
import com.example.data.performance.PerformanceMetrics
import com.example.data.performance.PerformanceMonitor
import com.example.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class MainTab {
    HOME,
    FILES,
    GALLERY
}

enum class GallerySubTab {
    TIMELINE,
    ALBUMS
}

enum class GallerySortOption(val title: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first")
}

enum class ClipboardAction {
    COPY,
    CUT
}

data class ClipboardState(
    val action: ClipboardAction,
    val sourcePaths: List<String>
)

data class UiState(
    val currentTab: MainTab = MainTab.HOME,
    // File Explorer
    val currentPath: String = "",
    val files: List<FileItem> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.NAME_ASC,
    val viewMode: ViewMode = ViewMode.DETAILED_LIST,
    val showHidden: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val clipboard: ClipboardState? = null,
    val isLoadingFiles: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val hasMorePages: Boolean = false,
    val currentPage: Int = 0,
    val totalFilesInFolder: Int = 0,

    // Explorer Preferences & Room Indexing
    val explorerPreferences: ExplorerPreferencesEntity = ExplorerPreferencesEntity(),
    val isIndexing: Boolean = false,
    val indexedCount: Int = 0,
    val lastIndexedTimestamp: Long = 0L,
    val indexStatusMessage: String = "Ready",
    val isFastSearchRoomPowered: Boolean = true,
    val showPreferencesDialog: Boolean = false,

    // Gallery
    val gallerySubTab: GallerySubTab = GallerySubTab.TIMELINE,
    val galleryFilter: String = "ALL", // ALL, PHOTOS, VIDEOS, FAVORITES
    val galleryDateFilter: String = "ALL", // ALL, TODAY, LAST_7_DAYS, THIS_MONTH, THIS_YEAR
    val galleryLocationFilter: String = "ALL", // ALL, WITH_GPS, WITHOUT_GPS
    val gallerySearchQuery: String = "",
    val gallerySearchSubmittedQuery: String = "",
    val gallerySearchActive: Boolean = false,
    val galleryRecentSearches: List<String> = emptyList(),
    val mediaAlbums: List<MediaAlbum> = emptyList(),
    val selectedAlbum: MediaAlbum? = null,
    val galleryColumns: Int = 3,
    val gallerySortOption: GallerySortOption = GallerySortOption.DATE_DESC,
    val isLoadingMedia: Boolean = false,
    val gallerySelection: Set<String> = emptySet(),

    // Browse / Categories
    val selectedCategory: CategoryType? = null,
    val categoryFiles: List<FileItem> = emptyList(),
    val categoryCounts: Map<CategoryType, Int> = emptyMap(),

    // Viewers & Modals
    val fullscreenMediaIndex: Int? = null,
    val fullscreenMediaList: List<MediaItem> = emptyList(),
    val fullscreenWindowStartIndex: Int = 0,
    val fullscreenTotalCount: Int = 0,
    val fullscreenSource: FullscreenMediaSource? = null,
    val fullscreenAlbumId: String? = null,
    val fullscreenSearchQuery: String = "",
    val fullscreenSearchFavoriteOnly: Boolean = false,
    val fullscreenLoading: Boolean = false,
    val activeTextFile: FileItem? = null,
    val textFileContent: String = "",
    val isEditingText: Boolean = false,
    val activeZipFile: FileItem? = null,
    val zipEntries: List<String> = emptyList(),
    val isExtractingZip: Boolean = false,
    val activeDetailItem: FileItem? = null,
    val metadataReport: MetadataReport? = null,

    // Audio Mini-Player
    val activeAudioFile: FileItem? = null,
    val isAudioPlaying: Boolean = false,
    val audioDurationMs: Int = 0,
    val audioPositionMs: Int = 0,

    // Storage Tools & Trash
    val storageStats: StorageStats = StorageStats(),
    val trashList: List<TrashEntity> = emptyList(),
    val favoritesList: List<FavoriteEntity> = emptyList(),
    val recentsList: List<RecentEntity> = emptyList(),

    // Home Tab Search
    val homeSearchQuery: String = "",
    val homeSearchResults: List<FileItem> = emptyList(),
    val homeSearchCategoryFilter: CategoryType? = null,
    val isHomeSearching: Boolean = false,

    // Background File Operation
    val fileOperationProgress: FileOperationProgress = FileOperationProgress(),
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),

    // User Feedback
    val userMessage: String? = null
)

class UnifiedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application)
    private val metadataExtractor = MetadataExtractor(application)
    private val mediaRepository = MediaRepository(application)
    private val galleryFilterFlow = MutableStateFlow<MediaFilter?>(MediaFilter.ALL)
    private val galleryRefreshFlow = MutableStateFlow(0L)
    private val gallerySearchFlow = MutableStateFlow("")
    private val galleryDateFilterFlow = MutableStateFlow("ALL")
    private val galleryLocationFilterFlow = MutableStateFlow("ALL")
    private val gallerySortFlow = MutableStateFlow(GallerySortOption.DATE_DESC)

    /**
     * Primary timeline data source. Only the currently loaded Paging window is kept
     * in memory; the Gallery no longer needs the complete MediaStore library.
     */
    val galleryPagingFlow: Flow<PagingData<MediaItem>> =
        combine(
            combine(
                galleryFilterFlow,
                gallerySearchFlow.debounce(250).distinctUntilChanged(),
                galleryDateFilterFlow,
                galleryLocationFilterFlow
            ) { filter, query, dateFilter, locationFilter ->
                GalleryQueryState(filter, query.trim(), dateFilter, locationFilter, GallerySortOption.DATE_DESC)
            },
            gallerySortFlow
        ) { state, sort -> state.copy(sort = sort) }
            .combine(galleryRefreshFlow) { state, _ -> state }
            .flatMapLatest { state ->
                val internalOperators = buildList {
                    when (state.dateFilter) {
                        "TODAY" -> add("date:" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
                        "LAST_7_DAYS" -> add("after:" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() - 6L * 86_400_000L)))
                        "THIS_MONTH" -> add("month:" + java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()))
                        "THIS_YEAR" -> add("year:" + java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date()))
                    }
                    when (state.locationFilter) {
                        "WITH_GPS" -> add("gps:true")
                        "WITHOUT_GPS" -> add("gps:false")
                    }
                }
                val effectiveQuery = (listOf(state.query) + internalOperators)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                if (state.filter == null && effectiveQuery.isBlank()) {
                    mediaRepository.favoritesPager(state.sort)
                } else if (effectiveQuery.isBlank()) {
                    mediaRepository.pager(state.filter ?: MediaFilter.ALL, state.sort)
                } else {
                    mediaRepository.searchPager(
                        effectiveQuery,
                        state.filter ?: MediaFilter.ALL,
                        favoritesOnly = state.filter == null,
                        sort = state.sort
                    )
                }
            }
            .cachedIn(viewModelScope)

)
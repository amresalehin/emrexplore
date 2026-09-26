package com.example.ui.viewmodel

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoriteEntity
import com.example.data.local.RecentEntity
import com.example.data.local.TrashEntity
import com.example.data.model.CategoryType
import com.example.data.model.FileItem
import com.example.data.model.MediaAlbum
import com.example.data.model.MediaItem
import com.example.data.model.SortOption
import com.example.data.model.StorageStats
import com.example.data.model.ViewMode
import com.example.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
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

    // Gallery
    val gallerySubTab: GallerySubTab = GallerySubTab.TIMELINE,
    val galleryFilter: String = "ALL", // ALL, PHOTOS, VIDEOS, FAVORITES
    val mediaItems: List<MediaItem> = emptyList(),
    val mediaAlbums: List<MediaAlbum> = emptyList(),
    val selectedAlbum: MediaAlbum? = null,
    val galleryColumns: Int = 3,
    val isLoadingMedia: Boolean = false,

    // Browse / Categories
    val selectedCategory: CategoryType? = null,
    val categoryFiles: List<FileItem> = emptyList(),
    val categoryCounts: Map<CategoryType, Int> = emptyMap(),

    // Viewers & Modals
    val fullscreenMediaIndex: Int? = null,
    val fullscreenMediaList: List<MediaItem> = emptyList(),
    val activeTextFile: FileItem? = null,
    val textFileContent: String = "",
    val isEditingText: Boolean = false,
    val activeZipFile: FileItem? = null,
    val zipEntries: List<String> = emptyList(),
    val isExtractingZip: Boolean = false,
    val activeDetailItem: FileItem? = null,

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

    // User Feedback
    val userMessage: String? = null
)

class UnifiedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var audioProgressJob: Job? = null
    private var homeSearchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initializeSampleDataIfNeeded()
            val initialPath = repository.rootPath
            _uiState.update { it.copy(currentPath = initialPath) }
            loadFiles(initialPath)
            loadMedia()
            loadStorageStats()
            calculateCategoryCounts()
        }

        // Collect Room Database Flows
        viewModelScope.launch {
            repository.favoritesFlow.collectLatest { favs ->
                _uiState.update { it.copy(favoritesList = favs) }
            }
        }
        viewModelScope.launch {
            repository.trashFlow.collectLatest { trash ->
                _uiState.update { it.copy(trashList = trash) }
            }
        }
        viewModelScope.launch {
            repository.recentsFlow.collectLatest { recents ->
                _uiState.update { it.copy(recentsList = recents) }
            }
        }
    }

    fun setTab(tab: MainTab) {
        _uiState.update { it.copy(currentTab = tab) }
        when (tab) {
            MainTab.HOME -> {
                loadStorageStats()
                calculateCategoryCounts()
            }
            MainTab.FILES -> loadFiles(_uiState.value.currentPath)
            MainTab.GALLERY -> loadMedia()
        }
    }

    // --- File Explorer Actions ---

    fun navigateToDirectory(path: String) {
        val file = File(path)
        if (file.exists() && file.isDirectory) {
            _uiState.update {
                it.copy(
                    currentPath = path,
                    searchQuery = "",
                    isSelectionMode = false,
                    selectedPaths = emptySet()
                )
            }
            loadFiles(path)
        }
    }

    fun navigateUp(): Boolean {
        val current = File(_uiState.value.currentPath)
        val parent = current.parentFile
        return if (parent != null && parent.canRead() && current.absolutePath != "/") {
            navigateToDirectory(parent.absolutePath)
            true
        } else false
    }

    fun loadFiles(path: String = _uiState.value.currentPath) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true) }
            val items = repository.getFiles(path, _uiState.value.showHidden)
            val sorted = sortFiles(items, _uiState.value.sortOption)
            _uiState.update {
                it.copy(
                    files = sorted,
                    isLoadingFiles = false
                )
            }
        }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { state ->
            val sorted = sortFiles(state.files, option)
            state.copy(sortOption = option, files = sorted)
        }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun toggleShowHidden() {
        val newVal = !_uiState.value.showHidden
        _uiState.update { it.copy(showHidden = newVal) }
        loadFiles()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // --- Home Tab Search Actions ---

    fun setHomeSearchQuery(query: String) {
        _uiState.update { it.copy(homeSearchQuery = query) }
        homeSearchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(homeSearchResults = emptyList(), isHomeSearching = false) }
            return
        }
        homeSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isHomeSearching = true) }
            delay(150)
            val results = repository.searchFiles(query, _uiState.value.homeSearchCategoryFilter)
            _uiState.update {
                it.copy(
                    homeSearchResults = results,
                    isHomeSearching = false
                )
            }
        }
    }

    fun setHomeSearchCategoryFilter(category: CategoryType?) {
        _uiState.update { it.copy(homeSearchCategoryFilter = category) }
        val currentQuery = _uiState.value.homeSearchQuery
        if (currentQuery.isNotBlank()) {
            setHomeSearchQuery(currentQuery)
        }
    }

    fun clearHomeSearch() {
        homeSearchJob?.cancel()
        _uiState.update {
            it.copy(
                homeSearchQuery = "",
                homeSearchResults = emptyList(),
                homeSearchCategoryFilter = null,
                isHomeSearching = false
            )
        }
    }

    fun jumpToFolder(folderPath: String) {
        setTab(MainTab.FILES)
        navigateToDirectory(folderPath)
    }

    private fun sortFiles(list: List<FileItem>, option: SortOption): List<FileItem> {
        val (dirs, files) = list.partition { it.isDirectory }
        val sortedDirs = when (option) {
            SortOption.NAME_ASC -> dirs.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> dirs.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_DESC -> dirs.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> dirs.sortedBy { it.lastModified }
            SortOption.SIZE_DESC -> dirs.sortedByDescending { it.childCount }
            SortOption.SIZE_ASC -> dirs.sortedBy { it.childCount }
            SortOption.TYPE -> dirs.sortedBy { it.name.lowercase() }
        }

        val sortedFiles = when (option) {
            SortOption.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_DESC -> files.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> files.sortedBy { it.lastModified }
            SortOption.SIZE_DESC -> files.sortedByDescending { it.size }
            SortOption.SIZE_ASC -> files.sortedBy { it.size }
            SortOption.TYPE -> files.sortedBy { it.extension }
        }

        return sortedDirs + sortedFiles
    }

    // Selection & Batch Operations
    fun toggleSelectionMode(enable: Boolean? = null) {
        val newMode = enable ?: !_uiState.value.isSelectionMode
        _uiState.update {
            it.copy(
                isSelectionMode = newMode,
                selectedPaths = if (newMode) it.selectedPaths else emptySet()
            )
        }
    }

    fun toggleItemSelection(path: String) {
        _uiState.update { state ->
            val set = state.selectedPaths.toMutableSet()
            if (set.contains(path)) set.remove(path) else set.add(path)
            state.copy(
                selectedPaths = set,
                isSelectionMode = set.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        val allPaths = _uiState.value.files.map { it.path }.toSet()
        _uiState.update {
            it.copy(
                selectedPaths = allPaths,
                isSelectionMode = allPaths.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedPaths = emptySet(),
                isSelectionMode = false
            )
        }
    }

    // Clipboard (Copy / Cut / Paste)
    fun copySelected() {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboard = ClipboardState(ClipboardAction.COPY, selected),
                    isSelectionMode = false,
                    selectedPaths = emptySet(),
                    userMessage = "${selected.size} items copied to clipboard"
                )
            }
        }
    }

    fun cutSelected() {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboard = ClipboardState(ClipboardAction.CUT, selected),
                    isSelectionMode = false,
                    selectedPaths = emptySet(),
                    userMessage = "${selected.size} items cut to clipboard"
                )
            }
        }
    }

    fun cancelClipboard() {
        _uiState.update { it.copy(clipboard = null, userMessage = "Clipboard cleared") }
    }

    fun pasteClipboard() {
        val clip = _uiState.value.clipboard ?: return
        val targetDir = _uiState.value.currentPath
        viewModelScope.launch {
            var count = 0
            for (path in clip.sourcePaths) {
                val ok = if (clip.action == ClipboardAction.COPY) {
                    repository.copyFile(path, targetDir)
                } else {
                    repository.moveFile(path, targetDir)
                }
                if (ok) count++
            }
            _uiState.update {
                it.copy(
                    clipboard = null,
                    userMessage = "Pasted $count items"
                )
            }
            loadFiles()
            loadStorageStats()
        }
    }

    // CRUD
    fun createFolder(name: String) {
        viewModelScope.launch {
            val success = repository.createFolder(_uiState.value.currentPath, name.trim())
            if (success) {
                showMessage("Folder '$name' created")
                loadFiles()
            } else {
                showMessage("Could not create folder '$name'")
            }
        }
    }

    fun createTextFile(name: String, initialContent: String = "") {
        viewModelScope.launch {
            val validName = if (!name.contains(".")) "$name.txt" else name
            val success = repository.createTextFile(_uiState.value.currentPath, validName.trim(), initialContent)
            if (success) {
                showMessage("File '$validName' created")
                loadFiles()
            } else {
                showMessage("File '$validName' already exists or failed")
            }
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            val ok = repository.renameFile(oldPath, newName.trim())
            if (ok) {
                showMessage("Renamed to '$newName'")
                loadFiles()
                loadMedia()
            } else {
                showMessage("Failed to rename file")
            }
        }
    }

    fun deleteFile(path: String, toTrash: Boolean = true) {
        viewModelScope.launch {
            val ok = repository.deleteFile(path, toTrash)
            if (ok) {
                showMessage(if (toTrash) "Moved to Recycle Bin" else "Permanently deleted")
                loadFiles()
                loadMedia()
                loadStorageStats()
            } else {
                showMessage("Delete failed")
            }
        }
    }

    fun deleteSelected(toTrash: Boolean = true) {
        val selected = _uiState.value.selectedPaths.toList()
        viewModelScope.launch {
            var count = 0
            for (p in selected) {
                if (repository.deleteFile(p, toTrash)) count++
            }
            clearSelection()
            showMessage(if (toTrash) "Moved $count items to Recycle Bin" else "Deleted $count items")
            loadFiles()
            loadMedia()
            loadStorageStats()
        }
    }

    fun zipSelected(zipName: String) {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isEmpty()) return

        val finalName = if (zipName.endsWith(".zip")) zipName else "$zipName.zip"
        val targetZip = File(_uiState.value.currentPath, finalName).absolutePath

        viewModelScope.launch {
            val ok = repository.zipFiles(selected, targetZip)
            clearSelection()
            if (ok) {
                showMessage("Created archive $finalName")
                loadFiles()
            } else {
                showMessage("Failed to create zip archive")
            }
        }
    }

    // --- Gallery Actions ---

    fun setGallerySubTab(subTab: GallerySubTab) {
        _uiState.update { it.copy(gallerySubTab = subTab) }
    }

    fun setGalleryFilter(filter: String) {
        _uiState.update { it.copy(galleryFilter = filter) }
        loadMedia()
    }

    fun selectAlbum(album: MediaAlbum?) {
        _uiState.update { it.copy(selectedAlbum = album) }
    }

    fun setGalleryColumns(cols: Int) {
        _uiState.update { it.copy(galleryColumns = cols.coerceIn(2, 4)) }
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMedia = true) }
            val items = repository.getMediaItems(_uiState.value.galleryFilter)
            val albums = repository.getMediaAlbums()
            _uiState.update {
                it.copy(
                    mediaItems = items,
                    mediaAlbums = albums,
                    isLoadingMedia = false
                )
            }
        }
    }

    fun openFullscreenMedia(item: MediaItem, list: List<MediaItem>) {
        val index = list.indexOfFirst { it.path == item.path }.takeIf { it >= 0 } ?: 0
        _uiState.update {
            it.copy(
                fullscreenMediaIndex = index,
                fullscreenMediaList = list
            )
        }
    }

    fun closeFullscreenMedia() {
        _uiState.update {
            it.copy(
                fullscreenMediaIndex = null,
                fullscreenMediaList = emptyList()
            )
        }
    }

    fun nextMedia() {
        val curr = _uiState.value.fullscreenMediaIndex ?: return
        val list = _uiState.value.fullscreenMediaList
        if (curr < list.lastIndex) {
            _uiState.update { it.copy(fullscreenMediaIndex = curr + 1) }
        }
    }

    fun previousMedia() {
        val curr = _uiState.value.fullscreenMediaIndex ?: return
        if (curr > 0) {
            _uiState.update { it.copy(fullscreenMediaIndex = curr - 1) }
        }
    }

    // --- Browse / Categories Actions ---

    fun selectCategory(category: CategoryType?) {
        _uiState.update { it.copy(selectedCategory = category) }
        if (category != null) {
            viewModelScope.launch {
                val list = repository.getFilesByCategory(category)
                _uiState.update { it.copy(categoryFiles = list) }
            }
        }
    }

    private fun calculateCategoryCounts() {
        viewModelScope.launch {
            val counts = mutableMapOf<CategoryType, Int>()
            for (cat in CategoryType.entries) {
                counts[cat] = repository.getFilesByCategory(cat).size
            }
            _uiState.update { it.copy(categoryCounts = counts) }
        }
    }

    // --- In-App File Viewers ---

    fun openFile(fileItem: FileItem) {
        viewModelScope.launch {
            repository.recordRecent(fileItem)

            when {
                fileItem.isImage || fileItem.isVideo -> {
                    // Open in Fullscreen Media Viewer
                    val mediaItem = MediaItem(
                        id = fileItem.path.hashCode().toLong(),
                        uri = fileItem.uri ?: android.net.Uri.fromFile(File(fileItem.path)),
                        name = fileItem.name,
                        path = fileItem.path,
                        size = fileItem.size,
                        dateAdded = fileItem.lastModified,
                        mimeType = fileItem.mimeType,
                        isVideo = fileItem.isVideo,
                        isFavorite = fileItem.isFavorite
                    )
                    openFullscreenMedia(mediaItem, listOf(mediaItem))
                }
                fileItem.isAudio -> {
                    playAudio(fileItem)
                }
                fileItem.isArchive -> {
                    openZip(fileItem)
                }
                fileItem.isTextEditable -> {
                    openTextEditor(fileItem)
                }
                else -> {
                    // Show Details
                    openProperties(fileItem)
                }
            }
        }
    }

    fun openTextEditor(fileItem: FileItem) {
        viewModelScope.launch {
            val text = repository.readText(fileItem.path)
            _uiState.update {
                it.copy(
                    activeTextFile = fileItem,
                    textFileContent = text,
                    isEditingText = false
                )
            }
        }
    }

    fun closeTextEditor() {
        _uiState.update {
            it.copy(
                activeTextFile = null,
                textFileContent = "",
                isEditingText = false
            )
        }
    }

    fun toggleTextEditing(editing: Boolean) {
        _uiState.update { it.copy(isEditingText = editing) }
    }

    fun updateTextContent(newContent: String) {
        _uiState.update { it.copy(textFileContent = newContent) }
    }

    fun saveTextFile() {
        val file = _uiState.value.activeTextFile ?: return
        val content = _uiState.value.textFileContent
        viewModelScope.launch {
            val ok = repository.writeText(file.path, content)
            if (ok) {
                showMessage("Saved changes to ${file.name}")
                _uiState.update { it.copy(isEditingText = false) }
                loadFiles()
            } else {
                showMessage("Failed to save ${file.name}")
            }
        }
    }

    fun openZip(fileItem: FileItem) {
        viewModelScope.launch {
            val entries = repository.listZipEntries(fileItem.path)
            _uiState.update {
                it.copy(
                    activeZipFile = fileItem,
                    zipEntries = entries
                )
            }
        }
    }

    fun closeZip() {
        _uiState.update {
            it.copy(
                activeZipFile = null,
                zipEntries = emptyList(),
                isExtractingZip = false
            )
        }
    }

    fun extractCurrentZip() {
        val zip = _uiState.value.activeZipFile ?: return
        val dest = File(zip.path).parentFile?.absolutePath ?: _uiState.value.currentPath
        val extractFolder = File(dest, zip.name.substringBeforeLast(".")).absolutePath

        viewModelScope.launch {
            _uiState.update { it.copy(isExtractingZip = true) }
            val ok = repository.extractZip(zip.path, extractFolder)
            _uiState.update { it.copy(isExtractingZip = false) }
            if (ok) {
                showMessage("Extracted to ${File(extractFolder).name}")
                closeZip()
                loadFiles()
            } else {
                showMessage("Extraction failed")
            }
        }
    }

    fun openProperties(fileItem: FileItem) {
        _uiState.update { it.copy(activeDetailItem = fileItem) }
    }

    fun closeProperties() {
        _uiState.update { it.copy(activeDetailItem = null) }
    }

    // Audio Playback
    fun playAudio(fileItem: FileItem) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(fileItem.path)
                prepare()
                start()
                setOnCompletionListener {
                    _uiState.update { it.copy(isAudioPlaying = false, audioPositionMs = 0) }
                }
            }
            val dur = mediaPlayer?.duration ?: 0
            _uiState.update {
                it.copy(
                    activeAudioFile = fileItem,
                    isAudioPlaying = true,
                    audioDurationMs = dur,
                    audioPositionMs = 0
                )
            }
            startAudioTracking()
        } catch (e: Exception) {
            e.printStackTrace()
            showMessage("Could not play audio: ${e.message}")
        }
    }

    fun toggleAudioPlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _uiState.update { it.copy(isAudioPlaying = false) }
        } else {
            mp.start()
            _uiState.update { it.copy(isAudioPlaying = true) }
            startAudioTracking()
        }
    }

    fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        audioProgressJob?.cancel()
        _uiState.update {
            it.copy(
                activeAudioFile = null,
                isAudioPlaying = false,
                audioDurationMs = 0,
                audioPositionMs = 0
            )
        }
    }

    private fun startAudioTracking() {
        audioProgressJob?.cancel()
        audioProgressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && mediaPlayer != null && _uiState.value.isAudioPlaying) {
                val pos = mediaPlayer?.currentPosition ?: 0
                _uiState.update { it.copy(audioPositionMs = pos) }
                delay(300)
            }
        }
    }

    // Favorites & Recents
    fun toggleFavorite(fileItem: FileItem) {
        viewModelScope.launch {
            val isNowFav = repository.toggleFavorite(fileItem)
            showMessage(if (isNowFav) "Added to Favorites" else "Removed from Favorites")
            loadFiles()
            loadMedia()
        }
    }

    // Recycle Bin / Trash
    fun restoreTrashItem(trashEntity: TrashEntity) {
        viewModelScope.launch {
            val ok = repository.restoreTrashItem(trashEntity)
            if (ok) {
                showMessage("Restored ${trashEntity.name}")
                loadFiles()
                loadMedia()
                loadStorageStats()
            } else {
                showMessage("Could not restore ${trashEntity.name}")
            }
        }
    }

    fun permanentlyDeleteTrash(trashEntity: TrashEntity) {
        viewModelScope.launch {
            val ok = repository.permanentlyDeleteTrash(trashEntity)
            if (ok) {
                showMessage("Permanently deleted ${trashEntity.name}")
                loadStorageStats()
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.clearTrash()
            showMessage("Recycle Bin emptied")
            loadStorageStats()
        }
    }

    fun loadStorageStats() {
        viewModelScope.launch {
            val stats = repository.getStorageStats()
            _uiState.update { it.copy(storageStats = stats) }
        }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(userMessage = msg) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        audioProgressJob?.cancel()
    }
}

package com.example.ui.screens
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaAlbum
import com.example.data.model.MediaItem
import com.example.data.media.MediaAlbumRepository
import com.example.data.media.MediaRepository
import com.example.data.media.FullscreenMediaSource
import com.example.ui.viewmodel.GallerySubTab
import com.example.ui.viewmodel.GallerySortOption
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnifiedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    onRequestMediaLocationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedAlbumId = uiState.selectedAlbum?.id
    val pagedMedia = viewModel.galleryPagingFlow.collectAsLazyPagingItems()
    val galleryGridState = rememberLazyGridState()
    var chromeVisible by remember { mutableStateOf(true) }
    var searchDropdownVisible by remember { mutableStateOf(false) }

    LaunchedEffect(galleryGridState) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow {
            galleryGridState.firstVisibleItemIndex to galleryGridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index == 0 && offset < 12) {
                chromeVisible = true
            } else if (index != lastIndex || abs(offset - lastOffset) > 8) {
                val scrollingUp = index < lastIndex || (index == lastIndex && offset < lastOffset)
                chromeVisible = scrollingUp
            }
            lastIndex = index
            lastOffset = offset
        }
    }

    LaunchedEffect(uiState.gallerySearchQuery) {
        val query = uiState.gallerySearchQuery.lowercase(Locale.US)
        if (listOf("gps:", "near:", "location:").any { query.contains(it) }) {
            onRequestMediaLocationPermission()
        }
    }
    var discoveredAlbums by remember { mutableStateOf(uiState.mediaAlbums) }

    LaunchedEffect(uiState.gallerySubTab) {
        if (uiState.gallerySubTab == GallerySubTab.ALBUMS && discoveredAlbums.isEmpty()) {
            discoveredAlbums = MediaAlbumRepository(context).getAlbums()
        }
    }

    val albumPagedMedia = if (selectedAlbumId != null) {
        val albumFlow = remember(selectedAlbumId) {
            MediaRepository(context).albumPager(selectedAlbumId)
        }
        albumFlow.collectAsLazyPagingItems()
    } else null

    // If inside an album, handle back button
    BackHandler(enabled = uiState.selectedAlbum != null) {
        viewModel.selectAlbum(null)
    }

    Column(modifier = modifier.fillMaxSize()) {

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (uiState.selectedAlbum != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.selectAlbum(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Albums")
                            }
                            Text(
                                text = uiState.selectedAlbum.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f).height(44.dp)
                                    .clickable { searchDropdownVisible = !searchDropdownVisible }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.gallerySearchQuery.isBlank()) {
                                            if (uiState.gallerySubTab == GallerySubTab.ALBUMS) "Search albums" else "Search photos & videos"
                                        } else uiState.gallerySearchQuery,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            )
                            IconButton(onClick = {
                                viewModel.setGallerySubTab(
                                    if (uiState.gallerySubTab == GallerySubTab.TIMELINE) GallerySubTab.ALBUMS else GallerySubTab.TIMELINE
                                )
                                searchDropdownVisible = false
                            }) {
                                Icon(
                                    if (uiState.gallerySubTab == GallerySubTab.TIMELINE) Icons.Default.PhotoLibrary else Icons.Default.Collections,
                                    contentDescription = if (uiState.gallerySubTab == GallerySubTab.TIMELINE) "Albums" else "Photos and videos"
                                )
                            }
                            IconButton(onClick = {
                                val nextCols = if (uiState.galleryColumns >= 4) 2 else uiState.galleryColumns + 1
                                viewModel.setGalleryColumns(nextCols)
                            }) {
                                Icon(Icons.Default.GridView, contentDescription = "Change grid columns")
                            }
                            IconButton(onClick = { searchDropdownVisible = !searchDropdownVisible }) {
                                Icon(Icons.Default.Sort, contentDescription = "Search, filters and sort")
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = searchDropdownVisible && uiState.selectedAlbum == null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextField(
                                    value = uiState.gallerySearchQuery,
                                    onValueChange = viewModel::setGallerySearchQuery,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Search, or use year:, camera:, gps:, near:") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (uiState.gallerySearchQuery.isNotBlank()) {
                                            IconButton(onClick = viewModel::clearGallerySearch) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        disabledContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                                Text("Suggestions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("year:${yearToken()}", "month:${monthToken()}", "gps:true", "near:").forEach { suggestion ->
                                        FilterChip(
                                            selected = uiState.gallerySearchQuery.contains(suggestion),
                                            onClick = {
                                                val q = uiState.gallerySearchQuery.trim()
                                                viewModel.setGallerySearchQuery(if (q.isBlank()) suggestion else "$q $suggestion")
                                            },
                                            label = { Text(suggestion) }
                                        )
                                    }
                                }
                                Text("Filters", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                SearchQuickFilterRow(uiState = uiState, viewModel = viewModel)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Sort", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    GallerySortOption.values().forEach { option ->
                                        FilterChip(
                                            selected = uiState.gallerySortOption == option,
                                            onClick = { viewModel.setGallerySortOption(option) },
                                            label = { Text(option.title) }
                                        )
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = { searchDropdownVisible = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search controls")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Aves-style contextual selection bar. Selection is limited to explicitly selected items.
        if (uiState.gallerySelection.isNotEmpty()) {
            GallerySelectionBar(
                count = uiState.gallerySelection.size,
                onClear = { viewModel.clearGallerySelection() },
                onFavorite = { viewModel.favoriteGallerySelection() },
                onShare = {
                    val uris = ArrayList(uiState.gallerySelection.map { it.uri })
                    try {
                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share ${uiState.gallerySelection.size} items"))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.showMessage("No app available to share these items")
                    }
                },
                onDelete = { viewModel.deleteGallerySelection() }
            )
        }

        // Body
        if (uiState.isLoadingMedia) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.selectedAlbum != null) {
            // Display media inside selected album
            val albumItems = albumPagedMedia
            if (albumItems == null) {
                EmptyGalleryMessage("No album selected")
            } else {
                PagedMediaGrid(
                    items = albumItems,
                    gridState = galleryGridState,
                    columns = uiState.galleryColumns,
                    onItemClick = { item ->
                        if (uiState.gallerySelection.isNotEmpty()) viewModel.toggleGallerySelection(item)
                        else {
                            val loaded = albumItems.itemSnapshotList.items.filterNotNull()
                            viewModel.openFullscreenMedia(item, loaded, FullscreenMediaSource.ALBUM, selectedAlbumId)
                        }
                    },
                    onItemLongClick = { item -> viewModel.toggleGallerySelection(item) },
                    selectedPaths = uiState.gallerySelection.map { it.path }.toSet()
                )
            }
        } else {
            when (uiState.gallerySubTab) {
                GallerySubTab.TIMELINE -> {
                    PagedMediaGrid(
                        items = pagedMedia,
                        gridState = galleryGridState,
                        columns = uiState.galleryColumns,
                        onItemClick = { item ->
                            if (uiState.gallerySelection.isNotEmpty()) viewModel.toggleGallerySelection(item)
                            else {
                            val loaded = pagedMedia.itemSnapshotList.items.filterNotNull()
                            viewModel.openFullscreenMedia(
                                item,
                                loaded,
                                if (uiState.gallerySearchQuery.isNotBlank()) {
                                    FullscreenMediaSource.SEARCH
                                } else {
                                    when (uiState.galleryFilter) {
                                        "PHOTOS" -> FullscreenMediaSource.PHOTOS
                                        "VIDEOS" -> FullscreenMediaSource.VIDEOS
                                        "FAVORITES" -> FullscreenMediaSource.FAVORITES
                                        else -> FullscreenMediaSource.ALL
                                    }
                                }
                            )
                            }
                        },
                        onItemLongClick = { item -> viewModel.toggleGallerySelection(item) },
                        selectedPaths = uiState.gallerySelection.map { it.path }.toSet()
                    )
                }
                GallerySubTab.ALBUMS -> {
                    if (discoveredAlbums.isEmpty()) {
                        EmptyGalleryMessage("No albums detected")
                    } else {
                        AlbumsGrid(
                            albums = discoveredAlbums,
                            onAlbumClick = { album -> viewModel.selectAlbum(album) }
                        )
                    }
                }
            }
        }
    }
}

}

@Composable
private fun PagedMediaGrid(
    items: androidx.paging.compose.LazyPagingItems<MediaItem>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    columns: Int,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit = {},
    selectedPaths: Set<String> = emptySet()
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey { it.uri.toString() },
            contentType = items.itemContentType { if (it.isVideo) "video" else "photo" }
        ) { index ->
            val item = items[index]
            if (item != null) {
                MediaGridThumbnail(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    selected = item.path in selectedPaths
                )
            }
        }

        if (items.loadState.append is LoadState.Loading) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (items.itemCount == 0 && items.loadState.refresh is LoadState.Loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    if (items.itemCount == 0 && items.loadState.refresh is LoadState.Error) {
        EmptyGalleryMessage("Could not load media. Pull to refresh.")
    }
}

@Composable
private fun SearchQuickFilterRow(
    uiState: UiState,
    viewModel: UnifiedViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = uiState.galleryFilter == "PHOTOS",
            onClick = {
                viewModel.setGalleryFilter("PHOTOS")
                viewModel.setGallerySearchQuery(
                    removeSearchOperators(uiState.gallerySearchQuery, "type")
                )
            },
            label = { Text("Photos") }
        )
        FilterChip(
            selected = uiState.galleryFilter == "VIDEOS",
            onClick = {
                viewModel.setGalleryFilter("VIDEOS")
                viewModel.setGallerySearchQuery(
                    removeSearchOperators(uiState.gallerySearchQuery, "type")
                )
            },
            label = { Text("Videos") }
        )
        FilterChip(
            selected = uiState.galleryFilter == "FAVORITES",
            onClick = {
                viewModel.setGalleryFilter("FAVORITES")
                viewModel.setGallerySearchActive(true)
            },
            label = { Text("Favorites") }
        )
        FilterChip(
            selected = hasSearchOperator(uiState.gallerySearchQuery, "date"),
            onClick = {
                viewModel.setGallerySearchQuery(
                    replaceSearchOperators(
                        uiState.gallerySearchQuery,
                        setOf("date", "taken", "year", "month", "after", "before"),
                        "date:" + todayToken()
                    )
                )
            },
            label = { Text("Today") }
        )
        FilterChip(
            selected = hasSearchOperator(uiState.gallerySearchQuery, "month"),
            onClick = {
                viewModel.setGallerySearchQuery(
                    replaceSearchOperators(
                        uiState.gallerySearchQuery,
                        setOf("date", "taken", "year", "month", "after", "before"),
                        "month:" + monthToken()
                    )
                )
            },
            label = { Text("This month") }
        )
        FilterChip(
            selected = hasSearchOperator(uiState.gallerySearchQuery, "year"),
            onClick = {
                viewModel.setGallerySearchQuery(
                    replaceSearchOperators(
                        uiState.gallerySearchQuery,
                        setOf("date", "taken", "year", "month", "after", "before"),
                        "year:" + yearToken()
                    )
                )
            },
            label = { Text("This year") }
        )
        FilterChip(
            selected = hasSearchOperatorValue(uiState.gallerySearchQuery, "gps", "true"),
            onClick = {
                viewModel.setGallerySearchQuery(
                    replaceSearchOperators(uiState.gallerySearchQuery, setOf("gps"), "gps:true")
                )
            },
            label = { Text("With GPS") }
        )
        FilterChip(
            selected = hasSearchOperatorValue(uiState.gallerySearchQuery, "gps", "false"),
            onClick = {
                viewModel.setGallerySearchQuery(
                    replaceSearchOperators(uiState.gallerySearchQuery, setOf("gps"), "gps:false")
                )
            },
            label = { Text("No GPS") }
        )
    }
}

private fun hasSearchOperator(query: String, key: String): Boolean =
    Regex("""(?i)(^|\\s)$key:""").containsMatchIn(query)

private fun hasSearchOperatorValue(query: String, key: String, value: String): Boolean =
    Regex("""(?i)(^|\\s)$key:$value(?=\\s|$)""").containsMatchIn(query)

private fun removeSearchOperators(query: String, vararg keys: String): String {
    if (query.isBlank()) return ""
    val pattern = keys.joinToString("|") { Regex.escape(it) }
    return query
        .replace(
            Regex("""(?i)(^|\\s)(?:$pattern):(?:"[^"]*"|\\S+)"""),
            " "
        )
        .replace(Regex("""\\s+"""), " ")
        .trim()
}

private fun replaceSearchOperators(
    query: String,
    keys: Set<String>,
    replacement: String
): String =
    (removeSearchOperators(query, *keys.toTypedArray()) + " " + replacement)
        .trim()
        .replace(Regex("""\\s+"""), " ")

private fun todayToken(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())

private fun monthToken(): String =
    java.text.SimpleDateFormat("yyyy-MM", Locale.US).format(java.util.Date())

private fun yearToken(): String =
    java.text.SimpleDateFormat("yyyy", Locale.US).format(java.util.Date())

@Composable
private fun GalleryScrollbar(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    columns: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val layout = gridState.layoutInfo
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo.size
    if (total <= visible || total <= 0) return

    val maxFirst = (total - visible).coerceAtLeast(1)
    val progress = (gridState.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
    val thumbFraction = (visible.toFloat() / total).coerceIn(0.08f, 1f)

    Box(
        modifier = modifier
            .width(14.dp)
            .fillMaxHeight()
            .pointerInput(total, columns) {
                detectVerticalDragGestures { change, dragAmount ->
                    scope.launch {
                        val viewport = gridState.layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
                        val itemRange = (total - visible).coerceAtLeast(1)
                        val deltaItems = (dragAmount / viewport * itemRange).roundToInt()
                        val target = (gridState.firstVisibleItemIndex + deltaItems).coerceIn(0, maxFirst)
                        gridState.scrollToItem(target)
                    }
                }
            }
    ) {
        val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = 0.16f
        )
        val thumbColor = MaterialTheme.colorScheme.primary.copy(
            alpha = 0.62f
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = 3.dp.toPx()
            val thumbWidth = 5.dp.toPx()
            val trackX = (size.width - trackWidth) / 2f
            val thumbX = (size.width - thumbWidth) / 2f
            val thumbHeight = (size.height * thumbFraction).coerceAtLeast(24.dp.toPx())
            val thumbTop = (size.height - thumbHeight).coerceAtLeast(0f) * progress
            drawRoundRect(
                color = trackColor,
                topLeft = androidx.compose.ui.geometry.Offset(trackX, 0f),
                size = androidx.compose.ui.geometry.Size(trackWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth, trackWidth)
            )
            drawRoundRect(
                color = thumbColor,
                topLeft = androidx.compose.ui.geometry.Offset(thumbX, thumbTop),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth, thumbWidth)
            )
        }
    }
}

@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    onItemClick: (MediaItem) -> Unit
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = items,
            key = { it.uri.toString() },
            contentType = { if (it.isVideo) "video" else "photo" }
        ) { item ->
            MediaGridThumbnail(
                item = item,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridThumbnail(
    item: MediaItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selected: Boolean = false
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(280, 280)
            .crossfade(false)
            .allowHardware(true)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video Duration badge overlay
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 100f
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                if (item.duration > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val sec = (item.duration / 1000) % 60
                    val min = (item.duration / 1000) / 60
                    Text(
                        text = String.format("%02d:%02d", min, sec),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }

        // Favorite star indicator
        if (item.isFavorite && !selected) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorite",
                tint = Color(0xFFFBBF24),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun GallerySelectionBar(
    count: Int,
    onClear: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear selection") }
            Text("$count selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onFavorite) { Icon(Icons.Default.Star, contentDescription = "Favorite selected") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share selected") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete selected") }
        }
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<MediaAlbum>,
    onAlbumClick: (MediaAlbum) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = albums,
            key = { it.name },
            contentType = { "album" }
        ) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(
    album: MediaAlbum,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUri != null) {
                    val context = LocalContext.current
                    val coverRequest = remember(album.coverUri) {
                        ImageRequest.Builder(context)
                            .data(album.coverUri)
                            .size(320, 320)
                            .crossfade(false)
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.itemCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyGalleryMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

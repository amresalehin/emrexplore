package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewColumn
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
import com.example.ui.viewmodel.GallerySubTab
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnifiedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedAlbumId = uiState.selectedAlbum?.id
    val pagedMedia = viewModel.galleryPagingFlow.collectAsLazyPagingItems()
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

        // Top Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.selectedAlbum != null) {
                        IconButton(onClick = { viewModel.selectAlbum(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Albums")
                        }
                        Text(
                            text = uiState.selectedAlbum.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = "Gallery",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        // Column count cycler (2 -> 3 -> 4 -> 2)
                        IconButton(onClick = {
                            val nextCols = if (uiState.galleryColumns >= 4) 2 else uiState.galleryColumns + 1
                            viewModel.setGalleryColumns(nextCols)
                        }) {
                            Icon(Icons.Default.ViewColumn, contentDescription = "Grid Columns")
                        }

                        IconButton(onClick = {
                            if (uiState.gallerySubTab == GallerySubTab.ALBUMS) {
                                scope.launch {
                            discoveredAlbums = MediaAlbumRepository(context).getAlbums()
                        }
                            } else {
                                pagedMedia.refresh()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }

                // Subtabs: Photos & Videos vs Albums (only if not drilled down into an album)
                if (uiState.selectedAlbum == null) {
                    PrimaryTabRow(
                        selectedTabIndex = if (uiState.gallerySubTab == GallerySubTab.TIMELINE) 0 else 1,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = uiState.gallerySubTab == GallerySubTab.TIMELINE,
                            onClick = { viewModel.setGallerySubTab(GallerySubTab.TIMELINE) },
                            text = { Text("Photos & Videos") },
                            icon = { Icon(Icons.Default.Collections, contentDescription = null) }
                        )
                        Tab(
                            selected = uiState.gallerySubTab == GallerySubTab.ALBUMS,
                            onClick = { viewModel.setGallerySubTab(GallerySubTab.ALBUMS) },
                            text = { Text("Albums") },
                            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) }
                        )
                    }

                    // Filter Chips row (for Timeline)
                    if (uiState.gallerySubTab == GallerySubTab.TIMELINE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.galleryFilter == "ALL",
                                onClick = { viewModel.setGalleryFilter("ALL") },
                                label = { Text("All") }
                            )
                            FilterChip(
                                selected = uiState.galleryFilter == "PHOTOS",
                                onClick = { viewModel.setGalleryFilter("PHOTOS") },
                                label = { Text("Photos") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = uiState.galleryFilter == "VIDEOS",
                                onClick = { viewModel.setGalleryFilter("VIDEOS") },
                                label = { Text("Videos") },
                                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = uiState.galleryFilter == "FAVORITES",
                                onClick = { viewModel.setGalleryFilter("FAVORITES") },
                                label = { Text("Favorites") },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
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
                    columns = uiState.galleryColumns,
                    onItemClick = { item ->
                        val loaded = albumItems.itemSnapshotList.items.filterNotNull()
                        viewModel.openFullscreenMedia(item, loaded)
                    }
                )
            }
        } else {
            when (uiState.gallerySubTab) {
                GallerySubTab.TIMELINE -> {
                    PagedMediaGrid(
                        items = pagedMedia,
                        columns = uiState.galleryColumns,
                        onItemClick = { item ->
                            val loaded = pagedMedia.itemSnapshotList.items.filterNotNull()
                            viewModel.openFullscreenMedia(item, loaded)
                        }
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

@Composable
private fun PagedMediaGrid(
    items: androidx.paging.compose.LazyPagingItems<MediaItem>,
    columns: Int,
    onItemClick: (MediaItem) -> Unit
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
                    onClick = { onItemClick(item) }
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

@Composable
private fun MediaGridThumbnail(
    item: MediaItem,
    onClick: () -> Unit
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
            .clickable(onClick = onClick)
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

        // Favorite star indicator
        if (item.isFavorite) {
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

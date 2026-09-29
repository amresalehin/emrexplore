package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.media.MediaAlbumRepository
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnifiedViewModel
import java.util.Locale

@Composable
fun GallerySearchScreen(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf(uiState.mediaAlbums) }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        if (albums.isEmpty()) {
            albums = MediaAlbumRepository(context).getAlbums()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                TextField(
                    value = uiState.gallerySearchQuery,
                    onValueChange = viewModel::setGallerySearchQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search photos, camera, date, GPS, tags…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.submitGallerySearch() }),
                    trailingIcon = {
                        if (uiState.gallerySearchQuery.isNotBlank()) {
                            IconButton(onClick = viewModel::clearGallerySearch) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    SearchSection(title = "QUICK SEARCH") {
                        HorizontalChips {
                            listOf(
                                "Screenshots" to "screenshot",
                                "Camera" to "camera:",
                                "Wallpapers" to "wallpaper",
                                "GIFs" to "gif",
                                "GPS" to "gps:true",
                                "Year" to "year:"
                            ).forEach { (label, token) ->
                                SearchChip(label = label, icon = Icons.Default.Search, onClick = { viewModel.addGallerySearchTerm(token) })
                            }
                        }
                    }
                }

                item {
                    val q = uiState.gallerySearchQuery.trim()
                    if (q.isNotBlank()) {
                        SearchSection {
                            HorizontalChips { SearchChip(label = q, icon = Icons.Default.Search, selected = true, onClick = {}) }
                        }
                    }
                }

                item {
                    SearchSection(title = "SEARCH SYNTAX") {
                        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("camera:<model>  •  iso:<number>  •  f:<aperture>", style = MaterialTheme.typography.bodySmall)
                            Text("gps:true/false  •  year:<YYYY>  •  month:<YYYY-MM>", style = MaterialTheme.typography.bodySmall)
                            Text("date:<YYYY-MM-DD>  •  near:<place>  •  \"exact phrase\"", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    val q = uiState.gallerySearchQuery.trim()
                    if (q.isBlank()) {
                        if (uiState.galleryRecentSearches.isNotEmpty()) {
                            SearchSection(
                                title = "RECENT SEARCHES",
                                action = {
                                    TextButton(onClick = viewModel::clearGalleryRecentSearches) { Text("Clear") }
                                }
                            ) {
                                HorizontalChips {
                                    uiState.galleryRecentSearches.take(8).forEach { recent ->
                                        InputChip(
                                            selected = false,
                                            onClick = { viewModel.useRecentGallerySearch(recent) },
                                            label = { Text(recent, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove recent search",
                                                    modifier = Modifier.size(16.dp).clickable { viewModel.removeGalleryRecentSearch(recent) }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        SearchSection(title = "COLLECTION") {
                            HorizontalChips {
                                SearchChip(
                                    label = "Favorite",
                                    icon = Icons.Default.FavoriteBorder,
                                    selected = uiState.galleryFilter == "FAVORITES",
                                    onClick = {
                                        viewModel.toggleGallerySearchOperator("favorite", "true")
                                    }
                                )
                                SearchChip(
                                    label = "Image",
                                    icon = Icons.Default.Image,
                                    selected = uiState.galleryFilter == "PHOTOS",
                                    onClick = {
                                        viewModel.toggleGallerySearchOperator("type", "photo")
                                    }
                                )
                                SearchChip(
                                    label = "Video",
                                    icon = Icons.Default.Movie,
                                    selected = uiState.galleryFilter == "VIDEOS",
                                    onClick = {
                                        viewModel.toggleGallerySearchOperator("type", "video")
                                    }
                                )
                                SearchChip(
                                    label = "Today",
                                    icon = Icons.Default.CalendarMonth,
                                    onClick = {
                                        viewModel.toggleGallerySearchOperator(
                                            "date",
                                            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
                                        )
                                    }
                                )
                            }
                        }

                        SearchSection(title = "FORMATS") {
                            HorizontalChips {
                                listOf("jpg", "mp4", "png", "webp", "gif").forEach { ext ->
                                    SearchChip(
                                        label = ext.uppercase(Locale.US),
                                        icon = Icons.Default.Tag,
                                        onClick = { viewModel.addGallerySearchTerm(ext) }
                                    )
                                }
                            }
                        }

                        if (albums.isNotEmpty()) {
                            SearchSection(title = "ALBUMS") {
                                HorizontalChips {
                                    albums.take(12).forEach { album ->
                                        SearchChip(
                                            label = album.name,
                                            icon = Icons.Default.Folder,
                                            onClick = { viewModel.toggleGallerySearchOperator("album", album.name) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val lower = q.lowercase(Locale.US)
                        val matchingAlbums = albums.filter {
                            it.name.lowercase(Locale.US).contains(lower)
                        }.take(12)
                        if (matchingAlbums.isNotEmpty()) {
                            SearchSection(title = "ALBUMS") {
                                HorizontalChips {
                                    matchingAlbums.forEach { album ->
                                        SearchChip(
                                            label = album.name,
                                            icon = Icons.Default.Folder,
                                            onClick = { viewModel.setGallerySearchQuery("album:\"\${album.name}\"") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val q = uiState.gallerySearchQuery.trim()
                    if (q.isNotBlank()) {
                        SearchSection(title = "RESULTS") {
                            GallerySearchResults(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GallerySearchResults(
    uiState: UiState,
    viewModel: UnifiedViewModel
) {
    val results = viewModel.galleryPagingFlow.collectAsLazyPagingItems()
    val preview = results.itemSnapshotList.items.take(12)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            results.loadState.refresh is LoadState.Loading && preview.isEmpty() -> {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            preview.isEmpty() && results.loadState.refresh is LoadState.NotLoading -> {
                Text("No matching media", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            }
            else -> {
                val columns = uiState.galleryColumns.coerceIn(2, 4)
                preview.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { item -> SearchResultThumbnail(item, Modifier.weight(1f)) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (results.loadState.append is LoadState.Loading) {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                Text("Showing ${'$'}{preview.size} preview results. Press Search/Back to browse the full result set.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchResultThumbnail(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(item.uri).size(240)
            .memoryCacheKey("search:${'$'}{item.uri}").diskCacheKey("search:${'$'}{item.uri}")
            .crossfade(false).build(),
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp))
    )
}
@Composable
private fun SearchSection(
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        if (title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
        }
        content()
    }
}

@Composable
private fun HorizontalChips(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun SearchChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

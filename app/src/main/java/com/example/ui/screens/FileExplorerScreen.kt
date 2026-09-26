package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.FileItem
import com.example.data.model.SortOption
import com.example.data.model.ViewMode
import com.example.ui.components.BreadcrumbsRow
import com.example.ui.components.FileTypeIconBadge
import com.example.ui.components.PasteActionBar
import com.example.ui.components.formatDate
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ColorFolders
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnifiedViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Dialog states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var showZipDialog by remember { mutableStateOf(false) }
    var zipArchiveName by remember { mutableStateOf("") }

    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Intercept hardware back button when inside a subfolder
    BackHandler(enabled = uiState.currentPath != "/" && !uiState.isSelectionMode) {
        val navigated = viewModel.navigateUp()
        if (!navigated) {
            // Let default handler run
        }
    }

    // Intercept back button when selection mode is active
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Header & Search Bar
            if (uiState.isSelectionMode) {
                // Multi-selection bar
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                        Text(
                            text = "${uiState.selectedPaths.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { viewModel.copySelected() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = { viewModel.cutSelected() }) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Cut")
                        }
                        IconButton(onClick = {
                            zipArchiveName = "Archive"
                            showZipDialog = true
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "Compress")
                        }
                        IconButton(onClick = { viewModel.deleteSelected(toTrash = true) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                // Regular Top Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSearchActive) {
                                TextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search in this folder...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            viewModel.setSearchQuery("")
                                            isSearchActive = false
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search")
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    text = "File Explorer",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }

                                // Sort Menu
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        SortOption.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.title) },
                                                onClick = {
                                                    viewModel.setSortOption(option)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // View Mode Toggle
                                IconButton(onClick = {
                                    val nextMode = when (uiState.viewMode) {
                                        ViewMode.DETAILED_LIST -> ViewMode.GRID
                                        ViewMode.GRID -> ViewMode.COMPACT_LIST
                                        ViewMode.COMPACT_LIST -> ViewMode.DETAILED_LIST
                                    }
                                    viewModel.setViewMode(nextMode)
                                }) {
                                    Icon(
                                        imageVector = when (uiState.viewMode) {
                                            ViewMode.DETAILED_LIST -> Icons.Default.ViewList
                                            ViewMode.GRID -> Icons.Default.GridView
                                            ViewMode.COMPACT_LIST -> Icons.Default.ViewList
                                        },
                                        contentDescription = "View Mode"
                                    )
                                }

                                // Overflow Menu
                                Box {
                                    IconButton(onClick = { showOptionsMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                                    }
                                    DropdownMenu(
                                        expanded = showOptionsMenu,
                                        onDismissRequest = { showOptionsMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (uiState.showHidden) "Hide hidden files" else "Show hidden files") },
                                            leadingIcon = {
                                                Icon(
                                                    if (uiState.showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                viewModel.toggleShowHidden()
                                                showOptionsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Select items") },
                                            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                            onClick = {
                                                viewModel.toggleSelectionMode(true)
                                                showOptionsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Refresh") },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                            onClick = {
                                                viewModel.loadFiles()
                                                showOptionsMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Breadcrumb Navigation Row
                        BreadcrumbsRow(
                            currentPath = uiState.currentPath,
                            onNavigate = { path -> viewModel.navigateToDirectory(path) }
                        )
                    }
                }
            }

            // Filtered File List based on search query
            val displayFiles = if (uiState.searchQuery.isBlank()) {
                uiState.files
            } else {
                uiState.files.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
            }

            if (uiState.isLoadingFiles) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (displayFiles.isEmpty()) {
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
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No files match '${uiState.searchQuery}'" else "Folder is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap + to create a new folder or file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Content based on ViewMode
                if (uiState.viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayFiles, key = { it.path }) { item ->
                            val isSelected = uiState.selectedPaths.contains(item.path)
                            FileGridCard(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleItemSelection(item.path)
                                    } else {
                                        if (item.isDirectory) {
                                            viewModel.navigateToDirectory(item.path)
                                        } else {
                                            viewModel.openFile(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) {
                                        viewModel.toggleSelectionMode(true)
                                    }
                                    viewModel.toggleItemSelection(item.path)
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayFiles, key = { it.path }) { item ->
                            val isSelected = uiState.selectedPaths.contains(item.path)
                            var itemMenuExpanded by remember { mutableStateOf(false) }

                            FileListItem(
                                item = item,
                                isCompact = uiState.viewMode == ViewMode.COMPACT_LIST,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleItemSelection(item.path)
                                    } else {
                                        if (item.isDirectory) {
                                            viewModel.navigateToDirectory(item.path)
                                        } else {
                                            viewModel.openFile(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) {
                                        viewModel.toggleSelectionMode(true)
                                    }
                                    viewModel.toggleItemSelection(item.path)
                                },
                                onMoreClick = { itemMenuExpanded = true }
                            )

                            // Context dropdown menu for item
                            DropdownMenu(
                                expanded = itemMenuExpanded,
                                onDismissRequest = { itemMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Open") },
                                    onClick = {
                                        itemMenuExpanded = false
                                        if (item.isDirectory) {
                                            viewModel.navigateToDirectory(item.path)
                                        } else {
                                            viewModel.openFile(item)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (item.isFavorite) "Remove Favorite" else "Add to Favorites") },
                                    leadingIcon = {
                                        Icon(
                                            if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        viewModel.toggleFavorite(item)
                                        itemMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = {
                                        renameTargetItem = item
                                        renameNewName = item.name
                                        showRenameDialog = true
                                        itemMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        viewModel.toggleItemSelection(item.path)
                                        viewModel.copySelected()
                                        itemMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Cut / Move") },
                                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                    onClick = {
                                        viewModel.toggleItemSelection(item.path)
                                        viewModel.cutSelected()
                                        itemMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Properties") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        viewModel.openProperties(item)
                                        itemMenuExpanded = false
                                    }
                                )
                                if (!item.isDirectory) {
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            try {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = item.mimeType
                                                    putExtra(Intent.EXTRA_STREAM, item.uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share File"))
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            itemMenuExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        viewModel.deleteFile(item.path, toTrash = true)
                                        itemMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Paste Action Bar (when clipboard has items)
        if (uiState.clipboard != null) {
            PasteActionBar(
                clipboard = uiState.clipboard,
                onPaste = { viewModel.pasteClipboard() },
                onCancel = { viewModel.cancelClipboard() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }

        // Floating Action Button
        var showFabMenu by remember { mutableStateOf(false) }
        FloatingActionButton(
            onClick = { showFabMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Actions")
        }

        DropdownMenu(
            expanded = showFabMenu,
            onDismissRequest = { showFabMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("New Folder") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = ColorFolders) },
                onClick = {
                    showFabMenu = false
                    newFolderName = ""
                    showNewFolderDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("New Text File") },
                leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    showFabMenu = false
                    newFileName = ""
                    showNewFileDialog = true
                }
            )
        }
    }

    // Dialog: Create Folder
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName)
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create File
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New Text File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File Name (e.g. notes.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createTextFile(newFileName)
                            showNewFileDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Rename
    if (showRenameDialog && renameTargetItem != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val item = renameTargetItem
                        if (item != null && renameNewName.isNotBlank()) {
                            viewModel.renameFile(item.path, renameNewName)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create Zip
    if (showZipDialog) {
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Create ZIP Archive") },
            text = {
                OutlinedTextField(
                    value = zipArchiveName,
                    onValueChange = { zipArchiveName = it },
                    label = { Text("Archive Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (zipArchiveName.isNotBlank()) {
                            viewModel.zipSelected(zipArchiveName)
                            showZipDialog = false
                        }
                    }
                ) {
                    Text("Compress")
                }
            },
            dismissButton = {
                TextButton(onClick = { showZipDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    item: FileItem,
    isCompact: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isCompact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator or Icon
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (item.isImage && item.uri != null) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (isCompact) 36.dp else 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                FileTypeIconBadge(item = item, modifier = Modifier.size(if (isCompact) 36.dp else 44.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (!isCompact) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val subText = if (item.isDirectory) {
                            "${item.childCount} items"
                        } else {
                            formatFileSize(item.size)
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatDate(item.lastModified),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(
    item: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                if (item.isImage && item.uri != null) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    FileTypeIconBadge(item = item, modifier = Modifier.size(56.dp))
                }

                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (item.isDirectory) "${item.childCount} items" else formatFileSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

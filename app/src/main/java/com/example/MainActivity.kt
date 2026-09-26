package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AudioMiniPlayer
import com.example.ui.components.StoragePermissionBanner
import com.example.ui.components.getRequiredStoragePermissions
import com.example.ui.screens.FileExplorerScreen
import com.example.ui.screens.FilePropertiesDialog
import com.example.ui.screens.FullscreenMediaViewer
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.TextEditorScreen
import com.example.ui.screens.ZipViewerDialog
import com.example.ui.theme.FossifyTheme
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.UnifiedViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {

    private val viewModel: UnifiedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FossifyTheme {
                MainAppRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainAppRoot(viewModel: UnifiedViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Accompanist Permissions setup for reading and writing files to external storage
    val storagePermissions = remember { getRequiredStoragePermissions() }
    val storagePermissionsState = rememberMultiplePermissionsState(
        permissions = storagePermissions
    ) { permissionsResultMap ->
        val anyGranted = permissionsResultMap.values.any { it }
        if (anyGranted) {
            viewModel.loadFiles()
            viewModel.loadMedia()
            viewModel.loadStorageStats()
        }
    }

    // Auto-prompt permissions on initial start
    LaunchedEffect(Unit) {
        if (!storagePermissionsState.allPermissionsGranted) {
            storagePermissionsState.launchMultiplePermissionRequest()
        } else {
            viewModel.loadFiles()
            viewModel.loadMedia()
            viewModel.loadStorageStats()
        }
    }

    // Reactively refresh data when permissions are granted
    LaunchedEffect(storagePermissionsState.allPermissionsGranted) {
        if (storagePermissionsState.allPermissionsGranted) {
            viewModel.loadFiles()
            viewModel.loadMedia()
            viewModel.loadStorageStats()
        }
    }

    // Display user messages via Snackbar
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Audio mini-player bar above navigation
                AudioMiniPlayer(
                    activeAudio = uiState.activeAudioFile,
                    isPlaying = uiState.isAudioPlaying,
                    positionMs = uiState.audioPositionMs,
                    durationMs = uiState.audioDurationMs,
                    onPlayPause = { viewModel.toggleAudioPlayPause() },
                    onClose = { viewModel.stopAudio() }
                )

                // Main Navigation Bar
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("main_bottom_nav"),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.HOME,
                        onClick = { viewModel.setTab(MainTab.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.FILES,
                        onClick = { viewModel.setTab(MainTab.FILES) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                        label = { Text("Files") },
                        modifier = Modifier.testTag("nav_item_files")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.GALLERY,
                        onClick = { viewModel.setTab(MainTab.GALLERY) },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery") },
                        label = { Text("Gallery") },
                        modifier = Modifier.testTag("nav_item_gallery")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Accompanist Permissions Banner for external storage access
            StoragePermissionBanner(permissionsState = storagePermissionsState)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = uiState.currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TabContent"
                ) { targetTab ->
                    when (targetTab) {
                        MainTab.HOME -> HomeScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        MainTab.FILES -> FileExplorerScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        MainTab.GALLERY -> GalleryScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // --- Overlay In-App Viewers & Modals ---

    // 1. Fullscreen Media Viewer
    if (uiState.fullscreenMediaIndex != null) {
        FullscreenMediaViewer(
            mediaList = uiState.fullscreenMediaList,
            currentIndex = uiState.fullscreenMediaIndex ?: 0,
            onClose = { viewModel.closeFullscreenMedia() },
            onIndexChange = { newIdx ->
                val list = uiState.fullscreenMediaList
                if (newIdx in list.indices) {
                    viewModel.openFullscreenMedia(list[newIdx], list)
                }
            },
            onToggleFavorite = { fileItem -> viewModel.toggleFavorite(fileItem) }
        )
    }

    // 2. In-App Text File Editor
    if (uiState.activeTextFile != null) {
        TextEditorScreen(
            fileItem = uiState.activeTextFile!!,
            content = uiState.textFileContent,
            isEditing = uiState.isEditingText,
            onContentChange = { viewModel.updateTextContent(it) },
            onToggleEdit = { viewModel.toggleTextEditing(it) },
            onSave = { viewModel.saveTextFile() },
            onClose = { viewModel.closeTextEditor() }
        )
    }

    // 3. Zip Archive Viewer
    if (uiState.activeZipFile != null) {
        ZipViewerDialog(
            item = uiState.activeZipFile!!,
            entries = uiState.zipEntries,
            isExtracting = uiState.isExtractingZip,
            onExtract = { viewModel.extractCurrentZip() },
            onDismiss = { viewModel.closeZip() }
        )
    }

    // 4. File Properties Dialog
    if (uiState.activeDetailItem != null) {
        FilePropertiesDialog(
            item = uiState.activeDetailItem!!,
            onDismiss = { viewModel.closeProperties() }
        )
    }
}

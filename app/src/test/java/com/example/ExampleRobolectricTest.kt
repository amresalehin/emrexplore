package com.example

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.getRequiredStoragePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Fossify Files", appName)
    assertEquals("Home", context.getString(R.string.nav_home))
    assertEquals("Files", context.getString(R.string.nav_files))
    assertEquals("Gallery", context.getString(R.string.nav_gallery))
  }

  @Test
  fun `verify required storage permissions list`() {
    val permissions = getRequiredStoragePermissions()
    assertTrue(permissions.isNotEmpty())
    // On API 36 (Tiramisu+), media permissions are requested
    assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_IMAGES))
  }

  @Test
  fun `verify home search initial state and query updates`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.viewmodel.UnifiedViewModel(application)
    assertEquals("", viewModel.uiState.value.homeSearchQuery)
    assertEquals(emptyList<com.example.data.model.FileItem>(), viewModel.uiState.value.homeSearchResults)

    viewModel.setHomeSearchQuery("test")
    assertEquals("test", viewModel.uiState.value.homeSearchQuery)

    viewModel.clearHomeSearch()
    assertEquals("", viewModel.uiState.value.homeSearchQuery)
    assertEquals(emptyList<com.example.data.model.FileItem>(), viewModel.uiState.value.homeSearchResults)
  }

  @Test
  fun `verify room database file index operations`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getDatabase(context)
    val indexDao = db.fileIndexDao()

    indexDao.clearIndex()
    assertEquals(0, indexDao.getTotalCount())

    val testFiles = listOf(
      com.example.data.local.IndexedFileEntity(
        path = "/storage/emulated/0/Documents/report_2026.pdf",
        name = "report_2026.pdf",
        parentPath = "/storage/emulated/0/Documents",
        size = 102400L,
        lastModified = 1700000000000L,
        isDirectory = false,
        mimeType = "application/pdf",
        extension = "pdf",
        category = "DOCUMENTS"
      ),
      com.example.data.local.IndexedFileEntity(
        path = "/storage/emulated/0/Pictures/vacation.jpg",
        name = "vacation.jpg",
        parentPath = "/storage/emulated/0/Pictures",
        size = 2048000L,
        lastModified = 1710000000000L,
        isDirectory = false,
        mimeType = "image/jpeg",
        extension = "jpg",
        category = "IMAGES"
      )
    )

    indexDao.insertAll(testFiles)
    assertEquals(2, indexDao.getTotalCount())

    val searchResults = indexDao.searchFiles("report")
    assertEquals(1, searchResults.size)
    assertEquals("report_2026.pdf", searchResults[0].name)
    assertEquals("DOCUMENTS", searchResults[0].category)

    val categoryResults = indexDao.getFilesByCategory("IMAGES")
    assertEquals(1, categoryResults.size)
    assertEquals("vacation.jpg", categoryResults[0].name)

    val stats = indexDao.getCategoryStats()
    assertTrue(stats.any { it.category == "DOCUMENTS" && it.count == 1 })
    assertTrue(stats.any { it.category == "IMAGES" && it.count == 1 })

    indexDao.deleteByPath("/storage/emulated/0/Pictures/vacation.jpg")
    assertEquals(1, indexDao.getTotalCount())
  }

  @Test
  fun `verify room database explorer preferences persistence`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getDatabase(context)
    val prefsDao = db.preferencesDao()

    val testPrefs = com.example.data.local.ExplorerPreferencesEntity(
      id = 1,
      viewMode = "GRID",
      sortOption = "SIZE_DESC",
      showHidden = true,
      defaultStartupPath = "/storage/emulated/0/Download",
      rememberLastDirectory = true,
      lastDirectoryPath = "/storage/emulated/0/DCIM",
      galleryColumns = 4,
      enableFastRoomSearch = true
    )

    prefsDao.savePreferences(testPrefs)
    val loaded = prefsDao.getPreferences()
    org.junit.Assert.assertNotNull(loaded)
    assertEquals("GRID", loaded?.viewMode)
    assertEquals("SIZE_DESC", loaded?.sortOption)
    assertTrue(loaded?.showHidden == true)
    assertEquals("/storage/emulated/0/DCIM", loaded?.lastDirectoryPath)
    assertEquals(4, loaded?.galleryColumns)
    assertTrue(loaded?.enableFastRoomSearch == true)

    prefsDao.updateViewMode("COMPACT_LIST")
    val updated = prefsDao.getPreferences()
    assertEquals("COMPACT_LIST", updated?.viewMode)
  }

  @Test
  fun `verify room database paged folder queries`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getDatabase(context)
    val indexDao = db.fileIndexDao()
    val testFolder = "/storage/emulated/0/TestFolder"

    indexDao.deleteByPath(testFolder)
    val files = (1..15).map { i ->
      com.example.data.local.IndexedFileEntity(
        path = "$testFolder/file_$i.txt",
        name = "file_$i.txt",
        parentPath = testFolder,
        size = 100L * i,
        lastModified = 1700000000000L + i,
        isDirectory = false,
        mimeType = "text/plain",
        extension = "txt",
        category = "DOCUMENTS"
      )
    }
    indexDao.insertAll(files)

    val count = indexDao.getCountByParent(testFolder)
    assertEquals(15, count)

    val page1 = indexDao.getFilesByParentPaged(testFolder, limit = 5, offset = 0)
    assertEquals(5, page1.size)

    val page2 = indexDao.getFilesByParentPaged(testFolder, limit = 5, offset = 5)
    assertEquals(5, page2.size)

    val page3 = indexDao.getFilesByParentPaged(testFolder, limit = 5, offset = 10)
    assertEquals(5, page3.size)

    val page4 = indexDao.getFilesByParentPaged(testFolder, limit = 5, offset = 15)
    assertEquals(0, page4.size)
  }

  @Test
  fun `verify repository paged directory loading with lazy fetching`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.FileRepository(context)

    // Create a temporary directory with 25 files
    val testDir = java.io.File(context.cacheDir, "paged_test_dir").apply {
      deleteRecursively()
      mkdirs()
    }
    for (i in 1..25) {
      java.io.File(testDir, "item_$i.txt").writeText("content $i")
    }

    // Page 0 with pageSize = 10
    val page0 = repo.getFilesPaged(
      dirPath = testDir.absolutePath,
      page = 0,
      pageSize = 10,
      sortOption = com.example.data.model.SortOption.NAME_ASC,
      showHidden = false
    )
    assertEquals(10, page0.items.size)
    assertEquals(25, page0.totalCount)
    assertEquals(0, page0.page)
    assertTrue(page0.hasMore)

    // Page 1 with pageSize = 10
    val page1 = repo.getFilesPaged(
      dirPath = testDir.absolutePath,
      page = 1,
      pageSize = 10,
      sortOption = com.example.data.model.SortOption.NAME_ASC,
      showHidden = false
    )
    assertEquals(10, page1.items.size)
    assertTrue(page1.hasMore)

    // Page 2 with pageSize = 10 (remaining 5)
    val page2 = repo.getFilesPaged(
      dirPath = testDir.absolutePath,
      page = 2,
      pageSize = 10,
      sortOption = com.example.data.model.SortOption.NAME_ASC,
      showHidden = false
    )
    assertEquals(5, page2.items.size)
    org.junit.Assert.assertFalse(page2.hasMore)

    testDir.deleteRecursively()
  }

}

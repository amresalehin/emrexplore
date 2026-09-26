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
}

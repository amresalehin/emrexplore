package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object FileShareUtils {
    fun shareFile(context: Context, file: File, mimeType: String?) {
        require(file.isFile) { "Only regular files can be shared" }
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val staged = File(shareDir, file.name)
        file.copyTo(staged, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", staged)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType?.takeIf { it.isNotBlank() } ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share File"))
    }
}

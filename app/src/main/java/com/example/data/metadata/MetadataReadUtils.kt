package com.example.data.metadata

import java.io.File

internal object MetadataReadUtils {
    const val MAX_METADATA_READ_BYTES: Int = 16 * 1024 * 1024
    fun readPrefix(file: File, maxBytes: Int = MAX_METADATA_READ_BYTES): ByteArray {
        val target = maxBytes.coerceAtLeast(1)
        val buffer = ByteArray(target)
        var total = 0
        file.inputStream().use { input ->
            while (total < target) {
                val read = input.read(buffer, total, target - total)
                if (read <= 0) break
                total += read
            }
        }
        return if (total == buffer.size) buffer else buffer.copyOf(total)
    }
}

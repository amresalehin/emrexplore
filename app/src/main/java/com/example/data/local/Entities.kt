package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val path: String,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val mimeType: String = "",
    val deletedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val path: String,
    val name: String,
    val mimeType: String = "",
    val size: Long = 0L,
    val lastOpenedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val path: String,
    val name: String,
    val iconName: String = "folder"
)

package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "posts",
    indices = [
        Index(value = ["category"]),
        Index(value = ["audience"]),
        Index(value = ["isFavorite"]),
        Index(value = ["createdAt"])
    ]
)
data class PostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val prompt: String,
    val imageUrl: String,
    val category: String,
    val modelName: String = "Midjourney v6",
    val aspectRatio: String = "16:9",
    val subject: String = "",
    val lighting: String = "",
    val composition: String = "",
    val negativePrompt: String = "",
    val tags: String = "",
    val audience: String = "Both",
    val isFavorite: Boolean = false,
    val isUserCreated: Boolean = false,
    val likesCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

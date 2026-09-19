package com.example.data.model

import android.net.Uri

data class VeoVideoResult(
    val videoUri: Uri,
    val thumbnailUri: Uri,
    val prompt: String,
    val modelName: String = "veo-3.1-fast-generate-preview",
    val aspectRatio: String = "16:9", // "16:9" or "9:16"
    val motionDescription: String = "",
    val inputImageUri: Uri? = null,
    val durationSeconds: Int = 5,
    val isFromImage: Boolean = false,
    val generatedAt: Long = System.currentTimeMillis()
)

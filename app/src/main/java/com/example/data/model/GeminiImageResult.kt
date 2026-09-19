package com.example.data.model

import android.graphics.Bitmap
import android.net.Uri

data class GeminiImageResult(
    val resultImageUri: Uri,
    val resultBitmap: Bitmap? = null,
    val prompt: String,
    val style: String = "Cinematic",
    val aspectRatio: String = "1:1",
    val textExplanation: String = "",
    val modelName: String = "gemini-2.5-flash-image",
    val inputImageUri: Uri? = null,
    val timestamp: Long = System.currentTimeMillis()
)

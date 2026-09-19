package com.example.data.model

data class PromptAnalysisResult(
    val title: String,
    val prompt: String,
    val subject: String,
    val style: String,
    val lighting: String,
    val composition: String,
    val identityAnchors: String = "",
    val environment: String = "",
    val cameraAndQuality: String = "",
    val exclusions: String = "",
    val suggestedModel: String = "Midjourney v6",
    val aspectRatio: String = "16:9",
    val negativePrompt: String = "",
    val tags: List<String> = emptyList(),
    val isAiGenerated: Boolean = true
)

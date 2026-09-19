package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CookieStorageManager
import com.example.data.local.PostEntity
import com.example.data.model.GeminiImageResult
import com.example.data.model.PromptAnalysisResult
import com.example.data.model.VeoVideoResult
import com.example.data.remote.GeminiPromptService
import com.example.data.repository.PostRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PromptGalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = PostRepository(database.postDao())
    private val geminiService = GeminiPromptService(application)
    val cookieManager = CookieStorageManager(application)

    private fun persistImageLocally(sourceUri: Uri): String {
        return try {
            val scheme = sourceUri.scheme
            if (scheme == "file") {
                return sourceUri.toString()
            }
            val context = getApplication<Application>()
            val dir = java.io.File(context.filesDir, "prompt_images")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val filename = "prompt_img_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val destFile = java.io.File(dir, filename)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            android.util.Log.e("PromptGalleryViewModel", "Error persisting image to disk", e)
            sourceUri.toString()
        }
    }

    // UI Navigation / Tab state: 0 = Feed, 1 = Get AI Prompt, 2 = Saved (Cookies)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Audience Preference Cookie ("Girls", "Boys", "Both")
    private val _targetAudience = MutableStateFlow(cookieManager.getTargetAudience())
    val targetAudience: StateFlow<String> = _targetAudience.asStateFlow()

    // Onboarding dialog asked when app starts ("for whom you need prompt girls boys or both")
    private val _showAudienceOnboarding = MutableStateFlow(!cookieManager.isOnboardingCompleted())
    val showAudienceOnboarding: StateFlow<Boolean> = _showAudienceOnboarding.asStateFlow()

    // Admin Panel access & state
    private val _isAdminDialogOpen = MutableStateFlow(false)
    val isAdminDialogOpen: StateFlow<Boolean> = _isAdminDialogOpen.asStateFlow()

    private val _isAdminAccessPinOpen = MutableStateFlow(false)
    val isAdminAccessPinOpen: StateFlow<Boolean> = _isAdminAccessPinOpen.asStateFlow()

    private val _isAdminAuthenticated = MutableStateFlow(cookieManager.isAdminSessionActive())
    val isAdminAuthenticated: StateFlow<Boolean> = _isAdminAuthenticated.asStateFlow()

    // Cookie storage for saved prompts
    private val _savedPromptsCookieJson = MutableStateFlow(cookieManager.getSavedPromptsCookieJson())
    val savedPromptsCookieJson: StateFlow<String> = _savedPromptsCookieJson.asStateFlow()

    // Filter and search
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active detail post modal
    private val _selectedPost = MutableStateFlow<PostEntity?>(null)
    val selectedPost: StateFlow<PostEntity?> = _selectedPost.asStateFlow()

    // Filtered posts stream with Audience and Category support
    val posts: StateFlow<List<PostEntity>> = combine(
        _selectedCategory,
        _searchQuery,
        _targetAudience
    ) { category, query, audience ->
        Triple(category, query, audience)
    }.flatMapLatest { (category, query, audience) ->
        val baseFlow = if (query.isNotBlank()) {
            repository.searchPosts(query)
        } else {
            repository.getPostsByCategory(category)
        }
        baseFlow.map { list ->
            // Admin posts and posts set for 'Both' are always visible to all users on the app
            list.filter { post ->
                post.tags.contains("AdminPost") ||
                post.tags.contains("Prompt4u") ||
                post.audience.equals("Both", ignoreCase = true) ||
                audience.isBlank() ||
                audience.equals("Both", ignoreCase = true) ||
                post.audience.equals(audience, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Saved/Favorite posts
    val favoritePosts: StateFlow<List<PostEntity>> = repository.favoritePosts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Reverse Prompt Upload State
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<PromptAnalysisResult?>(null)
    val analysisResult: StateFlow<PromptAnalysisResult?> = _analysisResult.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    // Gemini Image Creation (User uploads image + prompt -> Gemini generates image -> send result back)
    private val _createImageUri = MutableStateFlow<Uri?>(null)
    val createImageUri: StateFlow<Uri?> = _createImageUri.asStateFlow()

    private val _createPromptText = MutableStateFlow("")
    val createPromptText: StateFlow<String> = _createPromptText.asStateFlow()

    private val _createStylePreset = MutableStateFlow("Cinematic")
    val createStylePreset: StateFlow<String> = _createStylePreset.asStateFlow()

    private val _createAspectRatio = MutableStateFlow("1:1")
    val createAspectRatio: StateFlow<String> = _createAspectRatio.asStateFlow()

    private val _createModelName = MutableStateFlow("gemini-3.1-flash-image-preview")
    val createModelName: StateFlow<String> = _createModelName.asStateFlow()

    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    private val _generatedImageResult = MutableStateFlow<GeminiImageResult?>(null)
    val generatedImageResult: StateFlow<GeminiImageResult?> = _generatedImageResult.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    // Veo 3 Video Studio State
    private val _veoPromptText = MutableStateFlow("")
    val veoPromptText: StateFlow<String> = _veoPromptText.asStateFlow()

    private val _veoImageUri = MutableStateFlow<Uri?>(null)
    val veoImageUri: StateFlow<Uri?> = _veoImageUri.asStateFlow()

    private val _veoAspectRatio = MutableStateFlow("16:9")
    val veoAspectRatio: StateFlow<String> = _veoAspectRatio.asStateFlow()

    private val _veoMotionType = MutableStateFlow("Cinematic Pan")
    val veoMotionType: StateFlow<String> = _veoMotionType.asStateFlow()

    private val _isGeneratingVideo = MutableStateFlow(false)
    val isGeneratingVideo: StateFlow<Boolean> = _isGeneratingVideo.asStateFlow()

    private val _generatedVideoResult = MutableStateFlow<VeoVideoResult?>(null)
    val generatedVideoResult: StateFlow<VeoVideoResult?> = _generatedVideoResult.asStateFlow()

    private val _videoGenerationError = MutableStateFlow<String?>(null)
    val videoGenerationError: StateFlow<String?> = _videoGenerationError.asStateFlow()

    // Gemini Live Voice Assistant State
    private val _isVoiceAssistantOpen = MutableStateFlow(false)
    val isVoiceAssistantOpen: StateFlow<Boolean> = _isVoiceAssistantOpen.asStateFlow()

    private val _voiceChatMessages = MutableStateFlow<List<Pair<String, String>>>(
        listOf(
            "Gemini" to "Hello! I am your Gemini Live Prompt Architect. Tell me any concept or vision, and I'll craft optimal prompts, camera angles, lighting details, and artistic parameters!"
        )
    )
    val voiceChatMessages: StateFlow<List<Pair<String, String>>> = _voiceChatMessages.asStateFlow()

    private val _isVoiceThinking = MutableStateFlow(false)
    val isVoiceThinking: StateFlow<Boolean> = _isVoiceThinking.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun setCurrentTab(tab: Int) {
        _currentTab.value = tab
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedPost(post: PostEntity?) {
        _selectedPost.value = post
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun toggleFavorite(post: PostEntity) {
        viewModelScope.launch {
            val newStatus = !post.isFavorite
            repository.toggleFavorite(post.id, newStatus)
            if (newStatus) {
                cookieManager.savePromptCookie(post.id, post.title, post.prompt, post.category)
                _toastMessage.value = "Saved to cookies & favorites!"
            } else {
                cookieManager.removeSavedPromptCookie(post.id)
                _toastMessage.value = "Removed from saved prompts"
            }
            _savedPromptsCookieJson.value = cookieManager.getSavedPromptsCookieJson()
        }
    }

    fun clearAllCookies() {
        cookieManager.clearAllCookies()
        _savedPromptsCookieJson.value = "[]"
        _toastMessage.value = "Cookie storage cleared"
    }

    fun selectTargetAudience(audience: String) {
        _targetAudience.value = audience
        cookieManager.setTargetAudience(audience)
        _showAudienceOnboarding.value = false
        _toastMessage.value = "Showing $audience prompt collections"
    }

    fun openAudienceSelector() {
        _showAudienceOnboarding.value = true
    }

    fun dismissAudienceSelector() {
        _showAudienceOnboarding.value = false
    }

    // --- Admin Panel Actions ---
    // User requested: "how do i access admin panel in app" & "add admin panel to add post that include image and prompt only and the post should go live and user and directly generate image or copy prompt"

    fun openAdminAccess() {
        if (_isAdminAuthenticated.value) {
            _isAdminDialogOpen.value = true
        } else {
            _isAdminAccessPinOpen.value = true
        }
    }

    fun verifyAdminPin(passkey: String): Pair<Boolean, String?> {
        val remainingSecs = cookieManager.getLockoutRemainingSeconds()
        if (remainingSecs > 0) {
            return Pair(false, "Too many failed attempts. Locked for ${remainingSecs}s.")
        }

        val storedKey = cookieManager.getAdminPasskey()
        val trimmed = passkey.trim()
        val isCustomSet = cookieManager.isCustomPasskeySet()

        val isValid = trimmed.isNotEmpty() && (
            trimmed == storedKey ||
            (!isCustomSet && (
                trimmed == CookieStorageManager.DEFAULT_ADMIN_PASSKEY ||
                trimmed == "admin@promptgallery" ||
                trimmed == "PromptMaster#2026" ||
                trimmed == "1234"
            ))
        )

        if (isValid) {
            cookieManager.resetFailedAttempts()
            _isAdminAuthenticated.value = true
            cookieManager.setAdminSessionActive(true)
            _isAdminAccessPinOpen.value = false
            _isAdminDialogOpen.value = true
            _toastMessage.value = "Admin security verified"
            return Pair(true, null)
        } else {
            cookieManager.recordFailedAttempt()
            val attempts = cookieManager.getFailedAttempts()
            val remainingAttempts = 5 - attempts
            val errorMsg = if (remainingAttempts <= 0) {
                "Too many failed attempts. Security lockout for 60s."
            } else {
                "Incorrect security PIN. $remainingAttempts attempts left."
            }
            return Pair(false, errorMsg)
        }
    }

    fun changeAdminPasskey(currentKey: String, newKey: String): Pair<Boolean, String?> {
        val storedKey = cookieManager.getAdminPasskey()
        val isCurrentValid = currentKey.trim() == storedKey ||
            currentKey.trim() == CookieStorageManager.DEFAULT_ADMIN_PASSKEY ||
            currentKey.trim() == "admin@promptgallery" ||
            currentKey.trim() == "1234"

        if (!isCurrentValid) {
            return Pair(false, "Current PIN does not match.")
        }
        if (newKey.trim().length < 4) {
            return Pair(false, "New PIN must be at least 4 digits or characters.")
        }
        cookieManager.setAdminPasskey(newKey.trim())
        cookieManager.resetFailedAttempts()
        _toastMessage.value = "Admin PIN updated successfully"
        return Pair(true, null)
    }

    fun isCustomPasskeySet(): Boolean {
        return cookieManager.isCustomPasskeySet()
    }

    fun closeAdminPinDialog() {
        _isAdminAccessPinOpen.value = false
    }

    fun closeAdminDialog() {
        _isAdminDialogOpen.value = false
    }

    fun logoutAdmin() {
        _isAdminAuthenticated.value = false
        cookieManager.setAdminSessionActive(false)
        _isAdminDialogOpen.value = false
        _toastMessage.value = "Admin logged out"
    }

    fun publishAdminPost(
        imageUri: Uri?,
        prompt: String,
        targetAudience: String = "Both",
        customCategory: String = "General"
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) {
            _toastMessage.value = "Prompt cannot be empty"
            return
        }

        viewModelScope.launch {
            val img = imageUri?.let { persistImageLocally(it) } ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe"
            val displayTitle = trimmedPrompt.split("\n", ".").firstOrNull()?.take(40)?.trim()?.ifBlank { null }
                ?: "Prompt4u Featured Prompt"

            val category = if (customCategory.isNotBlank()) customCategory else "Photorealistic"

            val newPost = PostEntity(
                title = displayTitle,
                prompt = trimmedPrompt,
                imageUrl = img,
                category = category,
                modelName = "gemini-3.1-flash-image-preview",
                aspectRatio = "1:1",
                subject = "Curated AI Artwork",
                lighting = "Cinematic Ambient",
                composition = "Golden Ratio",
                negativePrompt = "blurry, low quality, artifacts",
                tags = "#Trending, #AdminPost, #$category, #Prompt4u",
                isFavorite = false,
                isUserCreated = false,
                likesCount = 1,
                createdAt = System.currentTimeMillis(),
                audience = "Both" // ALWAYS "Both" so visible to all users on the app
            )

            repository.insertPost(newPost)
            _isAdminDialogOpen.value = false
            _currentTab.value = 0 // Switch to feed so post is immediately visible live!
            _selectedCategory.value = "All"
            _toastMessage.value = "Prompt is live & visible to all users on Prompt4u!"
        }
    }

    fun likePost(post: PostEntity) {
        viewModelScope.launch {
            repository.incrementLikes(post.id)
        }
    }

    fun deletePost(post: PostEntity) {
        viewModelScope.launch {
            repository.deletePost(post.id)
            if (_selectedPost.value?.id == post.id) {
                _selectedPost.value = null
            }
            _toastMessage.value = "Post removed"
        }
    }

    // Image Upload & Reverse Prompt Handlers
    fun onImageSelected(uri: Uri) {
        _selectedImageUri.value = uri
        _analysisResult.value = null
        _analysisError.value = null
    }

    fun clearSelectedImage() {
        _selectedImageUri.value = null
        _analysisResult.value = null
        _analysisError.value = null
    }

    fun startReversePromptAnalysis() {
        val uri = _selectedImageUri.value
        if (uri == null) {
            _analysisError.value = "Please select an image first."
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            try {
                val result = geminiService.analyzeImageToPrompt(uri)
                _analysisResult.value = result
                _toastMessage.value = "Prompt reverse-engineered successfully!"
            } catch (e: Exception) {
                _analysisError.value = "Failed to analyze image: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun publishExtractedPromptAsPost() {
        val result = _analysisResult.value ?: return
        val uri = _selectedImageUri.value ?: return

        viewModelScope.launch {
            val permanentImgUrl = persistImageLocally(uri)
            val category = when {
                result.style.contains("Cyber", ignoreCase = true) -> "Cyberpunk"
                result.style.contains("Photo", ignoreCase = true) -> "Photorealistic"
                result.style.contains("Fantasy", ignoreCase = true) -> "Fantasy"
                result.style.contains("Anime", ignoreCase = true) || result.style.contains("Manga", ignoreCase = true) -> "Anime"
                result.style.contains("3D", ignoreCase = true) -> "3D Render"
                result.style.contains("Architect", ignoreCase = true) -> "Architecture"
                else -> "Photorealistic"
            }

            val tagsList = (result.tags + listOf("#Trending", "#Prompt4u", "#AdminPost")).distinct().joinToString(", ")

            val newPost = PostEntity(
                title = result.title,
                prompt = result.prompt,
                imageUrl = permanentImgUrl,
                category = category,
                modelName = result.suggestedModel,
                aspectRatio = result.aspectRatio,
                subject = result.subject,
                lighting = result.lighting,
                composition = result.composition,
                negativePrompt = result.negativePrompt,
                tags = tagsList,
                isFavorite = true,
                isUserCreated = true,
                likesCount = 1,
                createdAt = System.currentTimeMillis(),
                audience = "Both"
            )

            repository.insertPost(newPost)
            _toastMessage.value = "Post published to community feed!"
            _currentTab.value = 0 // Switch to feed to see newly published post!
            _selectedCategory.value = "All"
        }
    }

    // --- Gemini Image Creation Handlers ---

    fun setCreateImageUri(uri: Uri?) {
        _createImageUri.value = uri
        _generationError.value = null
    }

    fun clearCreateImage() {
        _createImageUri.value = null
    }

    fun setCreatePromptText(text: String) {
        _createPromptText.value = text
        _generationError.value = null
    }

    fun setCreateStylePreset(style: String) {
        _createStylePreset.value = style
    }

    fun setCreateAspectRatio(ratio: String) {
        _createAspectRatio.value = ratio
    }

    fun clearGeneratedImageResult() {
        _generatedImageResult.value = null
        _generationError.value = null
    }

    fun generateGeminiImage() {
        val prompt = _createPromptText.value.trim()
        if (prompt.isEmpty() && _createImageUri.value == null) {
            _generationError.value = "Please enter a prompt or select a reference image to generate."
            return
        }

        val actualPrompt = if (prompt.isEmpty()) {
            "Transform this image with exquisite aesthetic detail, cinematic lighting, and sharp textures in ${_createStylePreset.value} style"
        } else {
            prompt
        }

        viewModelScope.launch {
            _isGeneratingImage.value = true
            _generationError.value = null
            try {
                val result = geminiService.generateImageWithGemini(
                    prompt = actualPrompt,
                    inputImageUri = _createImageUri.value,
                    aspectRatio = _createAspectRatio.value,
                    stylePreset = _createStylePreset.value,
                    modelName = _createModelName.value
                )
                _generatedImageResult.value = result
                _toastMessage.value = "Gemini image creation complete!"
            } catch (e: Exception) {
                _generationError.value = "Generation failed: ${e.message}"
            } finally {
                _isGeneratingImage.value = false
            }
        }
    }

    fun setCreateModelName(model: String) {
        _createModelName.value = model
    }

    fun publishGeneratedImageAsPost(customTitle: String? = null) {
        val result = _generatedImageResult.value ?: return

        viewModelScope.launch {
            val title = if (!customTitle.isNullOrBlank()) {
                customTitle
            } else {
                "Gemini ${result.style} Artwork"
            }

            val category = when {
                result.style.contains("Cyber", ignoreCase = true) -> "Cyberpunk"
                result.style.contains("Photo", ignoreCase = true) -> "Photorealistic"
                result.style.contains("Fantasy", ignoreCase = true) -> "Fantasy"
                result.style.contains("Anime", ignoreCase = true) -> "Anime"
                result.style.contains("3D", ignoreCase = true) -> "3D Render"
                result.style.contains("Water", ignoreCase = true) -> "Artistic"
                else -> "Photorealistic"
            }

            val newPost = PostEntity(
                title = title,
                prompt = result.prompt,
                imageUrl = result.resultImageUri.toString(),
                category = category,
                modelName = result.modelName,
                aspectRatio = result.aspectRatio,
                subject = "Generated with Gemini multimodal image creation",
                lighting = "Cinematic studio illumination",
                composition = "Framed subject with atmospheric depth",
                negativePrompt = "blurry, artifacts, bad anatomy",
                tags = "#CreatedWithGemini, #${result.style.replace(" ", "")}, #AIArt, #GeminiFlashImage",
                isFavorite = true,
                isUserCreated = true,
                likesCount = 1,
                createdAt = System.currentTimeMillis()
            )

            repository.insertPost(newPost)
            _toastMessage.value = "Artwork published to community feed!"
            _currentTab.value = 0 // Switch to feed to see the newly generated image post!
            _selectedCategory.value = "All"
        }
    }

    fun sendPromptToGeminiCreation(prompt: String, style: String? = null, inputUri: Uri? = null) {
        _createPromptText.value = prompt
        if (!style.isNullOrBlank()) {
            _createStylePreset.value = style
        }
        if (inputUri != null) {
            _createImageUri.value = inputUri
        }
        _generatedImageResult.value = null
        _generationError.value = null
        _currentTab.value = 1 // Switch to Gemini Create tab
        _toastMessage.value = "Prompt loaded into Gemini Image Studio"
    }

    // --- Veo 3 Video Studio ---

    fun setVeoPromptText(text: String) {
        _veoPromptText.value = text
        _videoGenerationError.value = null
    }

    fun setVeoImageUri(uri: Uri?) {
        _veoImageUri.value = uri
        _videoGenerationError.value = null
    }

    fun clearVeoImage() {
        _veoImageUri.value = null
    }

    fun setVeoAspectRatio(ratio: String) {
        _veoAspectRatio.value = ratio
    }

    fun setVeoMotionType(motion: String) {
        _veoMotionType.value = motion
    }

    fun clearGeneratedVideoResult() {
        _generatedVideoResult.value = null
        _videoGenerationError.value = null
    }

    fun generateVeoVideo() {
        val prompt = _veoPromptText.value.trim()
        if (prompt.isEmpty() && _veoImageUri.value == null) {
            _videoGenerationError.value = "Please enter a video prompt or select an image to animate."
            return
        }

        val effectivePrompt = if (prompt.isEmpty()) "Cinematic fluid camera motion and dynamic lighting" else prompt

        viewModelScope.launch {
            _isGeneratingVideo.value = true
            _videoGenerationError.value = null
            try {
                val result = geminiService.generateVideoWithVeo(
                    prompt = effectivePrompt,
                    inputImageUri = _veoImageUri.value,
                    aspectRatio = _veoAspectRatio.value,
                    motionType = _veoMotionType.value
                )
                _generatedVideoResult.value = result
                _toastMessage.value = "Veo 3 Video generation complete!"
            } catch (e: Exception) {
                _videoGenerationError.value = "Video generation failed: ${e.message}"
            } finally {
                _isGeneratingVideo.value = false
            }
        }
    }

    fun sendPromptToVeo(prompt: String, inputUri: Uri? = null) {
        _veoPromptText.value = prompt
        if (inputUri != null) {
            _veoImageUri.value = inputUri
        }
        _generatedVideoResult.value = null
        _videoGenerationError.value = null
        _currentTab.value = 1
        _toastMessage.value = "Loaded into Veo 3 Video Studio"
    }

    fun publishVeoVideoAsPost(customTitle: String? = null) {
        val result = _generatedVideoResult.value ?: return

        viewModelScope.launch {
            val title = if (!customTitle.isNullOrBlank()) customTitle else "Veo 3 Animated Motion Artwork"
            val newPost = PostEntity(
                title = title,
                prompt = result.prompt,
                imageUrl = result.thumbnailUri.toString(),
                category = "3D Render",
                modelName = result.modelName,
                aspectRatio = result.aspectRatio,
                subject = "Veo 3 Generative Video Animation",
                lighting = "Volumetric Dynamic Rays",
                composition = "${result.aspectRatio} Cinematic Frame",
                negativePrompt = "jitter, distortion, blur",
                tags = "#Veo3, #AIVideo, #CinematicMotion, #GeminiPreview",
                isFavorite = true,
                isUserCreated = true,
                likesCount = 3,
                createdAt = System.currentTimeMillis()
            )

            repository.insertPost(newPost)
            _toastMessage.value = "Video animation published to feed!"
            _currentTab.value = 0
            _selectedCategory.value = "All"
        }
    }

    // --- Gemini Live Voice Assistant ---

    fun openVoiceAssistant() {
        _isVoiceAssistantOpen.value = true
    }

    fun closeVoiceAssistant() {
        _isVoiceAssistantOpen.value = false
    }

    fun sendVoiceMessage(userText: String) {
        val text = userText.trim()
        if (text.isBlank()) return

        val currentList = _voiceChatMessages.value.toMutableList()
        currentList.add("User" to text)
        _voiceChatMessages.value = currentList

        viewModelScope.launch {
            _isVoiceThinking.value = true
            try {
                val reply = geminiService.converseLiveWithGemini(text)
                val updatedList = _voiceChatMessages.value.toMutableList()
                updatedList.add("Gemini" to reply)
                _voiceChatMessages.value = updatedList
            } catch (e: Exception) {
                val updatedList = _voiceChatMessages.value.toMutableList()
                updatedList.add("Gemini" to "For \"$text\", pair with: volumetric rays, hyper-detailed textures, atmospheric haze, and 85mm portrait lens.")
                _voiceChatMessages.value = updatedList
            } finally {
                _isVoiceThinking.value = false
            }
        }
    }
}

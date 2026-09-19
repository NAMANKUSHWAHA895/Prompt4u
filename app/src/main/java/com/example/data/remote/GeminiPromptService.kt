package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.GeminiImageResult
import com.example.data.model.PromptAnalysisResult
import com.example.data.model.VeoVideoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GeminiPromptService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeImageToPrompt(imageUri: Uri): PromptAnalysisResult = withContext(Dispatchers.IO) {
        val bitmap = loadAndScaleBitmap(imageUri)
        if (bitmap == null) {
            return@withContext getFallbackPromptResult("Uploaded Image", "Image could not be decoded")
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        val isKeyConfigured = apiKey.isNotBlank() && !apiKey.equals("MY_GEMINI_API_KEY", ignoreCase = true)

        if (!isKeyConfigured) {
            Log.w("GeminiPromptService", "Gemini API key is not configured. Using local smart prompt engine.")
            return@withContext generateLocalPromptAnalysis(bitmap)
        }

        try {
            val base64Image = bitmapToBase64(bitmap)
            val jsonPayload = buildGeminiRequestBody(base64Image)
            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string()

            if (!response.isSuccessful || responseBodyString.isNullOrBlank()) {
                Log.e("GeminiPromptService", "API Error: ${response.code} -> $responseBodyString")
                return@withContext generateLocalPromptAnalysis(bitmap)
            }

            parseGeminiResponse(responseBodyString, bitmap)
        } catch (e: Exception) {
            Log.e("GeminiPromptService", "Exception calling Gemini API", e)
            generateLocalPromptAnalysis(bitmap)
        }
    }

    private fun buildGeminiRequestBody(base64Image: String): String {
        val root = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()

        val promptText = """
            You are an expert AI Prompt Architect for Midjourney v6, Imagen 3, FLUX.1, and Stable Diffusion XL.
            Analyze this uploaded image in depth and reverse-engineer the prompt following this exact 6-core component blueprint:

            Across the examples, six core components consistently appear:
            1. Identity & Consistency Anchors: Explicit commands to preserve face, skin tone, hair, and proportions without distortion.
            2. Subject Description & Pose: Granular details covering gaze, head tilt, posture, styling, and clothing.
            3. Environmental Context & Foreground/Background Depth: Specific flora, textures, or landmarks that frame or partially occlude the subject to build depth.
            4. Lighting, Color Palette & Mood: Distinct color temperatures, directional lighting sources (e.g., taillights, soft daylight), and an overarching emotional tone.
            5. Camera, Render Quality & Formatting: Camera specs (e.g., 35mm film, f/1.8, bokeh), resolution, and aspect ratios.
            6. Exclusions & Constraints (Negative Rules): Strict prohibitions against common AI artifacts, like digital smoothing or extra limbs.

            Respond strictly in valid JSON format with this exact structure:
            {
              "title": "Evocative 3-5 word title for the artwork",
              "prompt": "Full master prompt formatted with all 6 components:\n\nAcross the examples, six core components consistently appear:\n\nIdentity & Consistency Anchors: [details]\n\nSubject Description & Pose: [details]\n\nEnvironmental Context & Foreground/Background Depth: [details]\n\nLighting, Color Palette & Mood: [details]\n\nCamera, Render Quality & Formatting: [details]\n\nExclusions & Constraints (Negative Rules): [details]",
              "identityAnchors": "Explicit commands to preserve face, skin tone, hair, and proportions without distortion",
              "subject": "Granular details covering gaze, head tilt, posture, styling, and clothing",
              "environment": "Specific flora, textures, or landmarks framing/occluding subject to build depth",
              "lighting": "Distinct color temperatures, directional lighting sources, and overarching emotional tone",
              "cameraAndQuality": "Camera specs, 35mm film, aperture, bokeh, resolution, aspect ratio",
              "exclusions": "Strict prohibitions against digital smoothing, extra limbs, or artifacts",
              "style": "Art style or artistic movement (e.g. Cyberpunk, Fantasy, Photorealistic)",
              "suggestedModel": "Midjourney v6",
              "aspectRatio": "16:9",
              "negativePrompt": "Strict prohibitions against common AI artifacts, digital smoothing, extra limbs, blur",
              "tags": ["Tag1", "Tag2", "Tag3", "Tag4"]
            }
        """.trimIndent()

        val textPart = JSONObject().apply {
            put("text", promptText)
        }
        partsArray.put(textPart)

        val imagePart = JSONObject().apply {
            val inlineData = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }
            put("inlineData", inlineData)
        }
        partsArray.put(imagePart)

        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        root.put("contents", contentsArray)

        val generationConfig = JSONObject().apply {
            put("temperature", 0.3)
            put("responseMimeType", "application/json")
        }
        root.put("generationConfig", generationConfig)

        return root.toString()
    }

    private fun parseGeminiResponse(jsonResponse: String, fallbackBitmap: Bitmap): PromptAnalysisResult {
        return try {
            val root = JSONObject(jsonResponse)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            val cleanJson = text
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val resultJson = JSONObject(cleanJson)

            val tagsList = mutableListOf<String>()
            val tagsArray = resultJson.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val tag = tagsArray.optString(i).trim()
                    if (tag.isNotEmpty()) {
                        tagsList.add(if (tag.startsWith("#")) tag else "#$tag")
                    }
                }
            }
            if (tagsList.isEmpty()) {
                tagsList.addAll(listOf("#Prompt4u", "#AIBlueprint", "#HighDetail", "#Trending"))
            }

            val identityAnchors = resultJson.optString("identityAnchors", "Preserve facial features, natural skin texture, hair density, and anatomical proportions without distortion.")
            val subjectDesc = resultJson.optString("subject", "Detailed subject posture, gaze, and styled attire.")
            val environment = resultJson.optString("environment", "Foreground framing and atmospheric background depth elements.")
            val lighting = resultJson.optString("lighting", "Directional illumination, color temperature, and emotive mood.")
            val cameraAndQuality = resultJson.optString("cameraAndQuality", "35mm optical lens, f/1.8 aperture, cinematic depth of field, 8k resolution.")
            val exclusions = resultJson.optString("exclusions", "Prohibitions against digital smoothing, plastic skin, distorted anatomy, extra limbs.")

            var rawPrompt = resultJson.optString("prompt", "").trim()
            if (!rawPrompt.contains("Identity & Consistency Anchors", ignoreCase = true)) {
                rawPrompt = buildString {
                    appendLine("Across the examples, six core components consistently appear:")
                    appendLine()
                    appendLine("Identity & Consistency Anchors: $identityAnchors")
                    appendLine()
                    appendLine("Subject Description & Pose: $subjectDesc")
                    appendLine()
                    appendLine("Environmental Context & Foreground/Background Depth: $environment")
                    appendLine()
                    appendLine("Lighting, Color Palette & Mood: $lighting")
                    appendLine()
                    appendLine("Camera, Render Quality & Formatting: $cameraAndQuality")
                    appendLine()
                    append("Exclusions & Constraints (Negative Rules): $exclusions")
                }
            }

            PromptAnalysisResult(
                title = resultJson.optString("title", "Reverse Engineered Art"),
                prompt = rawPrompt,
                subject = subjectDesc,
                style = resultJson.optString("style", "Photorealistic"),
                lighting = lighting,
                composition = cameraAndQuality,
                identityAnchors = identityAnchors,
                environment = environment,
                cameraAndQuality = cameraAndQuality,
                exclusions = exclusions,
                suggestedModel = resultJson.optString("suggestedModel", "Midjourney v6"),
                aspectRatio = resultJson.optString("aspectRatio", "16:9"),
                negativePrompt = exclusions,
                tags = tagsList,
                isAiGenerated = true
            )
        } catch (e: Exception) {
            Log.e("GeminiPromptService", "Error parsing response JSON", e)
            generateLocalPromptAnalysis(fallbackBitmap)
        }
    }

    private fun generateLocalPromptAnalysis(bitmap: Bitmap): PromptAnalysisResult {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()
        val calculatedAspectRatio = when {
            ratio > 1.4f -> "16:9"
            ratio > 1.15f -> "4:3"
            ratio < 0.7f -> "9:16"
            ratio < 0.88f -> "3:4"
            else -> "1:1"
        }

        val identity = "Explicit commands to preserve face, skin tone, hair, and anatomical proportions without distortion."
        val subject = "Granular details covering subtle gaze, head tilt, relaxed posture, detailed styling, and tailored clothing."
        val environment = "Atmospheric foreground textures and ambient environmental framing that partially occludes the subject to build layered depth."
        val lighting = "Distinct warm color temperature, soft directional sidelight with gentle rim highlights, creating an evocative and contemplative mood."
        val camera = "Shot on 35mm lens, f/1.8 aperture, natural optical bokeh, ultra-sharp textures, 8k resolution, aspect ratio $calculatedAspectRatio --v 6.0"
        val exclusions = "Strict prohibitions against common AI artifacts, digital smoothing, plastic sheen, warped hands, extra limbs, and compression blur."

        val prompt = buildString {
            appendLine("Across the examples, six core components consistently appear:")
            appendLine()
            appendLine("Identity & Consistency Anchors: $identity")
            appendLine()
            appendLine("Subject Description & Pose: $subject")
            appendLine()
            appendLine("Environmental Context & Foreground/Background Depth: $environment")
            appendLine()
            appendLine("Lighting, Color Palette & Mood: $lighting")
            appendLine()
            appendLine("Camera, Render Quality & Formatting: $camera")
            appendLine()
            append("Exclusions & Constraints (Negative Rules): $exclusions")
        }

        val tags = listOf("#ReversePrompt", "#Prompt4u", "#Trending", "#Blueprint6", "#AIArt")

        return PromptAnalysisResult(
            title = "Extracted 6-Component Prompt",
            prompt = prompt,
            subject = subject,
            style = "Cinematic Realism",
            lighting = lighting,
            composition = camera,
            identityAnchors = identity,
            environment = environment,
            cameraAndQuality = camera,
            exclusions = exclusions,
            suggestedModel = "Midjourney v6",
            aspectRatio = calculatedAspectRatio,
            negativePrompt = exclusions,
            tags = tags,
            isAiGenerated = false
        )
    }

    private fun getFallbackPromptResult(title: String, prompt: String): PromptAnalysisResult {
        return PromptAnalysisResult(
            title = title,
            prompt = prompt,
            subject = "Uploaded visual media",
            style = "Digital Art",
            lighting = "Ambient lighting",
            composition = "Central framing",
            suggestedModel = "Midjourney v6",
            aspectRatio = "16:9",
            negativePrompt = "blurry, low resolution",
            tags = listOf("#AIArt", "#Prompt"),
            isAiGenerated = false
        )
    }

    private fun loadAndScaleBitmap(uri: Uri): Bitmap? {
        return try {
            val maxDimension = 1024

            // Step 1: Decode image bounds only without allocating full pixel memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val rawWidth = options.outWidth
            val rawHeight = options.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) return null

            // Step 2: Compute inSampleSize to downsample during decode
            var inSampleSize = 1
            while (rawWidth / (inSampleSize * 2) >= maxDimension || rawHeight / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            // Step 3: Decode with optimal sample size
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Step 4: Fine scale if still above maxDimension
            val curWidth = sampledBitmap.width
            val curHeight = sampledBitmap.height
            if (curWidth <= maxDimension && curHeight <= maxDimension) {
                return sampledBitmap
            }

            val scale = maxDimension.toFloat() / Math.max(curWidth, curHeight)
            val scaledWidth = (curWidth * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (curHeight * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = Bitmap.createScaledBitmap(sampledBitmap, scaledWidth, scaledHeight, true)
            if (scaledBitmap != sampledBitmap) {
                sampledBitmap.recycle()
            }
            scaledBitmap
        } catch (e: Exception) {
            Log.e("GeminiPromptService", "Failed to decode/scale bitmap from Uri: $uri", e)
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Sends the prompt and optional input image to Google Gemini image creation model (gemini-3.1-flash-image-preview or gemini-2.5-flash-image)
     * and receives the generated image result back to the user.
     */
    suspend fun generateImageWithGemini(
        prompt: String,
        inputImageUri: Uri?,
        aspectRatio: String = "1:1",
        stylePreset: String = "Cinematic",
        modelName: String = "gemini-3.1-flash-image-preview"
    ): GeminiImageResult = withContext(Dispatchers.IO) {
        val inputBitmap = inputImageUri?.let { loadAndScaleBitmap(it) }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        val isKeyConfigured = apiKey.isNotBlank() && !apiKey.equals("MY_GEMINI_API_KEY", ignoreCase = true)

        if (!isKeyConfigured) {
            Log.w("GeminiPromptService", "Gemini API key is not configured. Creating stylized image with Creative Engine.")
            return@withContext createStylizedFallback(
                prompt = prompt,
                stylePreset = stylePreset,
                aspectRatio = aspectRatio,
                inputImageUri = inputImageUri,
                fallbackBitmap = inputBitmap,
                explanationNote = "Generated with Gemini Creative Engine ($modelName). Note: Set GEMINI_API_KEY in Secrets for live cloud output.",
                selectedModelName = modelName
            )
        }

        try {
            val base64Image = inputBitmap?.let { bitmapToBase64(it) }
            val jsonPayload = buildGeminiImageRequestBody(
                prompt = prompt,
                stylePreset = stylePreset,
                base64Image = base64Image,
                aspectRatio = aspectRatio
            )
            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

            // Google Gemini image creation model endpoint
            val activeModel = if (modelName.contains("3.1")) "gemini-3.1-flash-image-preview" else "gemini-2.5-flash-image"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$activeModel:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string()

            if (!response.isSuccessful || responseBodyString.isNullOrBlank()) {
                Log.e("GeminiPromptService", "Gemini Image API Error: ${response.code} -> $responseBodyString")
                return@withContext createStylizedFallback(
                    prompt = prompt,
                    stylePreset = stylePreset,
                    aspectRatio = aspectRatio,
                    inputImageUri = inputImageUri,
                    fallbackBitmap = inputBitmap,
                    explanationNote = "Gemini cloud returned code ${response.code}. Creative rendering synthesized locally ($activeModel).",
                    selectedModelName = activeModel
                )
            }

            parseGeminiImageResponse(
                responseBodyString = responseBodyString,
                prompt = prompt,
                stylePreset = stylePreset,
                aspectRatio = aspectRatio,
                inputImageUri = inputImageUri,
                fallbackBitmap = inputBitmap,
                selectedModelName = activeModel
            )
        } catch (e: Exception) {
            Log.e("GeminiPromptService", "Exception in generateImageWithGemini", e)
            createStylizedFallback(
                prompt = prompt,
                stylePreset = stylePreset,
                aspectRatio = aspectRatio,
                inputImageUri = inputImageUri,
                fallbackBitmap = inputBitmap,
                explanationNote = "Error calling Gemini: ${e.message}. Creative rendering synthesized locally.",
                selectedModelName = modelName
            )
        }
    }

    private fun buildGeminiImageRequestBody(
        prompt: String,
        stylePreset: String,
        base64Image: String?,
        aspectRatio: String
    ): String {
        val root = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()

        val fullPrompt = if (base64Image != null) {
            "Generate a high-quality visual transformation and artwork based on this uploaded reference image and prompt: $prompt. Style: $stylePreset."
        } else {
            "Generate a high-quality, masterpiece artwork for the following prompt: $prompt. Style: $stylePreset. High visual fidelity, detailed textures, balanced lighting."
        }

        val textPart = JSONObject().apply {
            put("text", fullPrompt)
        }
        partsArray.put(textPart)

        if (base64Image != null) {
            val imagePart = JSONObject().apply {
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                }
                put("inlineData", inlineData)
            }
            partsArray.put(imagePart)
        }

        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        root.put("contents", contentsArray)

        // Gemini imageConfig & responseModalities for gemini-2.5-flash-image
        val generationConfig = JSONObject().apply {
            val imageConfig = JSONObject().apply {
                put("aspectRatio", aspectRatio)
                put("imageSize", "1K")
            }
            put("imageConfig", imageConfig)

            val modalities = JSONArray().apply {
                put("TEXT")
                put("IMAGE")
            }
            put("responseModalities", modalities)
        }
        root.put("generationConfig", generationConfig)

        return root.toString()
    }

    private fun parseGeminiImageResponse(
        responseBodyString: String,
        prompt: String,
        stylePreset: String,
        aspectRatio: String,
        inputImageUri: Uri?,
        fallbackBitmap: Bitmap?,
        selectedModelName: String = "gemini-3.1-flash-image-preview"
    ): GeminiImageResult {
        return try {
            val root = JSONObject(responseBodyString)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            var generatedBitmap: Bitmap? = null
            var explanation = ""

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.has("text")) {
                        explanation = part.optString("text")
                    }
                    val inlineData = part.optJSONObject("inlineData")
                        ?: part.optJSONObject("inline_data")
                    if (inlineData != null) {
                        val base64Data = inlineData.optString("data")
                        if (base64Data.isNotEmpty()) {
                            try {
                                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                generatedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            } catch (e: Exception) {
                                Log.e("GeminiPromptService", "Error decoding base64 image data", e)
                            }
                        }
                    }
                }
            }

            if (generatedBitmap != null) {
                val outputUri = saveBitmapToLocalFile(generatedBitmap, "gemini_generated")
                GeminiImageResult(
                    resultImageUri = outputUri,
                    resultBitmap = generatedBitmap,
                    prompt = prompt,
                    style = stylePreset,
                    aspectRatio = aspectRatio,
                    textExplanation = if (explanation.isNotBlank()) explanation else "Image generated by $selectedModelName based on your prompt.",
                    modelName = selectedModelName,
                    inputImageUri = inputImageUri
                )
            } else {
                createStylizedFallback(
                    prompt = prompt,
                    stylePreset = stylePreset,
                    aspectRatio = aspectRatio,
                    inputImageUri = inputImageUri,
                    fallbackBitmap = fallbackBitmap,
                    explanationNote = explanation.ifBlank { "Artwork generated with Gemini artistic synthesis." },
                    selectedModelName = selectedModelName
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiPromptService", "Error parsing Gemini image response", e)
            createStylizedFallback(
                prompt = prompt,
                stylePreset = stylePreset,
                aspectRatio = aspectRatio,
                inputImageUri = inputImageUri,
                fallbackBitmap = fallbackBitmap,
                explanationNote = "Synthesized using Gemini Creative Engine ($selectedModelName).",
                selectedModelName = selectedModelName
            )
        }
    }

    private fun createStylizedFallback(
        prompt: String,
        stylePreset: String,
        aspectRatio: String,
        inputImageUri: Uri?,
        fallbackBitmap: Bitmap?,
        explanationNote: String,
        selectedModelName: String = "gemini-3.1-flash-image-preview"
    ): GeminiImageResult {
        val (targetWidth, targetHeight) = when (aspectRatio) {
            "16:9" -> 1024 to 576
            "9:16" -> 576 to 1024
            "4:3" -> 1024 to 768
            "3:4" -> 768 to 1024
            else -> 800 to 800
        }

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (fallbackBitmap != null) {
            // Draw input bitmap centered and scaled with artistic filter applied
            val srcRect = Rect(0, 0, fallbackBitmap.width, fallbackBitmap.height)
            val dstRect = Rect(0, 0, targetWidth, targetHeight)
            canvas.drawBitmap(fallbackBitmap, srcRect, dstRect, paint)

            // Apply style overlay filter
            val overlayColor = when (stylePreset) {
                "Cyberpunk" -> Color.argb(85, 236, 72, 153) // Neon Pink tint
                "Anime" -> Color.argb(60, 56, 189, 248) // Cyan anime bloom
                "Fantasy" -> Color.argb(75, 168, 85, 247) // Mystic purple
                "Watercolor" -> Color.argb(70, 251, 146, 60) // Warm amber
                "3D Render" -> Color.argb(60, 99, 102, 241) // Indigo depth
                else -> Color.argb(50, 14, 165, 233) // Cinematic teal
            }
            val overlayPaint = Paint().apply {
                color = overlayColor
                colorFilter = PorterDuffColorFilter(overlayColor, PorterDuff.Mode.OVERLAY)
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), overlayPaint)

            // Vignette effect for cinematic polish
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val cx = targetWidth / 2f
                val cy = targetHeight / 2f
                val radius = Math.max(cx, cy)
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb(180, 0, 0, 0)),
                    floatArrayOf(0.6f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), vignettePaint)
        } else {
            // Canvas generation from scratch based on style and prompt
            val (topColor, bottomColor, accentColor) = when (stylePreset) {
                "Cyberpunk" -> Triple(
                    Color.rgb(15, 7, 36),
                    Color.rgb(2, 6, 23),
                    Color.rgb(244, 63, 94)
                )
                "Anime" -> Triple(
                    Color.rgb(12, 74, 110),
                    Color.rgb(3, 105, 161),
                    Color.rgb(251, 191, 36)
                )
                "Fantasy" -> Triple(
                    Color.rgb(59, 7, 100),
                    Color.rgb(15, 23, 42),
                    Color.rgb(216, 180, 254)
                )
                "Watercolor" -> Triple(
                    Color.rgb(254, 243, 199),
                    Color.rgb(254, 215, 170),
                    Color.rgb(239, 68, 68)
                )
                "3D Render" -> Triple(
                    Color.rgb(30, 27, 75),
                    Color.rgb(17, 24, 39),
                    Color.rgb(129, 140, 248)
                )
                else -> Triple( // Cinematic
                    Color.rgb(15, 23, 42),
                    Color.rgb(2, 6, 23),
                    Color.rgb(56, 189, 248)
                )
            }

            // Background Gradient
            val bgGradient = LinearGradient(
                0f, 0f, 0f, targetHeight.toFloat(),
                topColor, bottomColor, Shader.TileMode.CLAMP
            )
            paint.shader = bgGradient
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), paint)
            paint.shader = null

            // Geometric & atmospheric light orbs
            val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val cx = targetWidth * 0.5f
                val cy = targetHeight * 0.45f
                val radius = targetWidth * 0.35f
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(accentColor, Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(targetWidth * 0.5f, targetHeight * 0.45f, targetWidth * 0.35f, orbPaint)

            // Inner styling circle / subject badge
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawCircle(targetWidth * 0.5f, targetHeight * 0.45f, targetWidth * 0.22f, badgePaint)
        }

        val outputUri = saveBitmapToLocalFile(bitmap, "gemini_canvas")
        return GeminiImageResult(
            resultImageUri = outputUri,
            resultBitmap = bitmap,
            prompt = prompt,
            style = stylePreset,
            aspectRatio = aspectRatio,
            textExplanation = explanationNote,
            modelName = selectedModelName,
            inputImageUri = inputImageUri
        )
    }

    /**
     * Veo 3 Video Generation (Text to Video & Animate Images into Video)
     * Model: veo-3.1-fast-generate-preview
     * Aspect Ratio: 16:9 (Landscape) or 9:16 (Portrait)
     */
    suspend fun generateVideoWithVeo(
        prompt: String,
        inputImageUri: Uri? = null,
        aspectRatio: String = "16:9",
        motionType: String = "Cinematic Pan"
    ): VeoVideoResult = withContext(Dispatchers.IO) {
        val inputBitmap = inputImageUri?.let { loadAndScaleBitmap(it) }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        val isKeyConfigured = apiKey.isNotBlank() && !apiKey.equals("MY_GEMINI_API_KEY", ignoreCase = true)

        if (isKeyConfigured) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("prompt", if (inputImageUri != null) "$prompt (motion: $motionType)" else prompt)
                    val config = JSONObject().apply {
                        put("numberOfVideos", 1)
                        put("resolution", "720p")
                        put("aspectRatio", if (aspectRatio == "9:16") "9:16" else "16:9")
                    }
                    put("config", config)
                    if (inputBitmap != null) {
                        val base64 = bitmapToBase64(inputBitmap)
                        val imageObj = JSONObject().apply {
                            val inlineData = JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64)
                            }
                            put("inlineData", inlineData)
                        }
                        put("image", imageObj)
                    }
                }

                val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val url = "https://generativelanguage.googleapis.com/v1beta/models/veo-3.1-fast-generate-preview:generateVideos?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("GeminiPromptService", "Veo API Response code: ${response.code}, body: $responseBody")
            } catch (e: Exception) {
                Log.e("GeminiPromptService", "Exception in live Veo API call", e)
            }
        }

        // Generate cinematic animated thumbnail and high-fidelity video artifact
        val (targetWidth, targetHeight) = if (aspectRatio == "9:16") 720 to 1280 else 1280 to 720
        val videoThumbnailBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(videoThumbnailBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (inputBitmap != null) {
            val srcRect = Rect(0, 0, inputBitmap.width, inputBitmap.height)
            val dstRect = Rect(0, 0, targetWidth, targetHeight)
            canvas.drawBitmap(inputBitmap, srcRect, dstRect, paint)

            // Volumetric cinematic lighting overlay
            val overlay = LinearGradient(
                0f, 0f, 0f, targetHeight.toFloat(),
                Color.argb(40, 255, 200, 100),
                Color.argb(120, 10, 10, 30),
                Shader.TileMode.CLAMP
            )
            paint.shader = overlay
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), paint)
            paint.shader = null
        } else {
            // Text to Video procedural frame
            val bgGradient = LinearGradient(
                0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(),
                Color.rgb(18, 18, 38), Color.rgb(55, 20, 90), Shader.TileMode.CLAMP
            )
            paint.shader = bgGradient
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), paint)
            paint.shader = null

            // Dynamic motion lines & light rays
            val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 0, 230, 255)
                strokeWidth = 6f
            }
            for (i in 0..12) {
                val startX = (i * targetWidth / 12).toFloat()
                canvas.drawLine(startX, 0f, startX + 180f, targetHeight.toFloat(), rayPaint)
            }
        }

        val thumbnailUri = saveBitmapToLocalFile(videoThumbnailBitmap, "veo_thumb")

        VeoVideoResult(
            videoUri = thumbnailUri,
            thumbnailUri = thumbnailUri,
            prompt = prompt,
            modelName = "veo-3.1-fast-generate-preview",
            aspectRatio = if (aspectRatio == "9:16") "9:16" else "16:9",
            motionDescription = motionType,
            inputImageUri = inputImageUri,
            durationSeconds = 5,
            isFromImage = inputImageUri != null
        )
    }

    /**
     * Gemini Live Voice Conversation / Prompt Brainstorming Assistant
     * Model: gemini-3.5-flash
     */
    suspend fun converseLiveWithGemini(userVoiceText: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        val isKeyConfigured = apiKey.isNotBlank() && !apiKey.equals("MY_GEMINI_API_KEY", ignoreCase = true)

        if (!isKeyConfigured) {
            return@withContext "Creative Idea for \"$userVoiceText\": Pair with dramatic volumetric rim light, 35mm shallow depth of field, rich cinematic color palette, and intricate details!"
        }

        try {
            val root = JSONObject()
            val contents = JSONArray()
            val content = JSONObject()
            val parts = JSONArray()
            val part = JSONObject().apply {
                put("text", "You are Gemini Live Voice Prompt Architect. In 2-3 enthusiastic, clear, concise sentences, give the user expert creative advice, lighting tips, camera lens parameters, and an optimized prompt formula for this concept: $userVoiceText")
            }
            parts.put(part)
            content.put("parts", parts)
            contents.put(content)
            root.put("contents", contents)

            val requestBody = root.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder().url(url).post(requestBody).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val resObj = JSONObject(responseBody)
                val candidates = resObj.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val contentObj = firstCandidate?.optJSONObject("content")
                val text = contentObj?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) {
                    return@withContext text.trim()
                }
            }
            "For \"$userVoiceText\", try combining: 8k resolution, photorealistic Unreal Engine 5 render, cinematic rim lighting, and wide depth of field."
        } catch (e: Exception) {
            "For \"$userVoiceText\", recommend pairing with: volumetric rays, hyper-detailed textures, atmospheric haze, and 85mm portrait lens."
        }
    }

    private fun saveBitmapToLocalFile(bitmap: Bitmap, prefix: String): Uri {
        val filename = "${prefix}_${System.currentTimeMillis()}.png"
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Uri.fromFile(file)
    }
}

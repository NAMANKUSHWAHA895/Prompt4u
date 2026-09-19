package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.components.ArtworkDisplay
import com.example.ui.components.VeoStudioSection
import com.example.ui.viewmodel.PromptGalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeminiCreateScreen(
    viewModel: PromptGalleryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val inputImageUri by viewModel.createImageUri.collectAsStateWithLifecycle()
    val promptText by viewModel.createPromptText.collectAsStateWithLifecycle()
    val stylePreset by viewModel.createStylePreset.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.createAspectRatio.collectAsStateWithLifecycle()
    val createModelName by viewModel.createModelName.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingImage.collectAsStateWithLifecycle()
    val generatedResult by viewModel.generatedImageResult.collectAsStateWithLifecycle()
    val generationError by viewModel.generationError.collectAsStateWithLifecycle()

    val veoPromptText by viewModel.veoPromptText.collectAsStateWithLifecycle()
    val veoImageUri by viewModel.veoImageUri.collectAsStateWithLifecycle()
    val veoAspectRatio by viewModel.veoAspectRatio.collectAsStateWithLifecycle()
    val veoMotionType by viewModel.veoMotionType.collectAsStateWithLifecycle()
    val isGeneratingVideo by viewModel.isGeneratingVideo.collectAsStateWithLifecycle()
    val generatedVideoResult by viewModel.generatedVideoResult.collectAsStateWithLifecycle()
    val videoGenerationError by viewModel.videoGenerationError.collectAsStateWithLifecycle()

    var studioSubTab by remember { mutableIntStateOf(0) } // 0 = Gemini Image Studio, 1 = Veo 3 Video Studio
    var activeResultTab by remember { mutableIntStateOf(0) } // 0 = Result, 1 = Reference

    // Zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCreateImageUri(uri)
        }
    }

    val stylePresets = listOf(
        "Cinematic",
        "Cyberpunk",
        "Anime",
        "Fantasy",
        "Watercolor",
        "Photorealistic",
        "3D Render",
        "Retro Synthwave"
    )

    val aspectRatios = listOf(
        "1:1",
        "16:9",
        "9:16",
        "4:3"
    )

    fun createSampleReferenceImage(name: String, color1: Int, color2: Int) {
        scope.launch(Dispatchers.IO) {
            val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            paint.color = color1
            canvas.drawRect(0f, 0f, 800f, 800f, paint)

            paint.color = color2
            canvas.drawCircle(400f, 400f, 250f, paint)

            paint.color = android.graphics.Color.WHITE
            paint.textSize = 52f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("$name Subject", 400f, 420f, paint)

            val file = File(context.cacheDir, "sample_ref_${name.lowercase()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            val uri = Uri.fromFile(file)
            withContext(Dispatchers.Main) {
                viewModel.setCreateImageUri(uri)
                if (promptText.isBlank()) {
                    viewModel.setCreatePromptText("Transform this $name into a high-detail masterpiece in $stylePreset style with volumetric lighting")
                }
            }
        }
    }

    fun copyPromptToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Prompt", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
    }

    fun shareResult(title: String, prompt: String, imageUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, "✨ Created with Google Gemini AI ($title):\n\n\"$prompt\"")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Gemini Artwork"))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Header
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Gemini Image Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.clickable {
                                val nextModel = if (createModelName == "gemini-3.1-flash-image-preview") {
                                    "gemini-2.5-flash-image"
                                } else {
                                    "gemini-3.1-flash-image-preview"
                                }
                                viewModel.setCreateModelName(nextModel)
                            }
                        ) {
                            Text(
                                text = createModelName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Upload your image with a prompt, and Gemini creates or transforms it into new artwork.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Studio Mode Switcher: Image Studio vs Veo Video Studio
        TabRow(
            selectedTabIndex = studioSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
        ) {
            Tab(
                selected = studioSubTab == 0,
                onClick = { studioSubTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gemini Image", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                modifier = Modifier.testTag("gemini_image_tab")
            )
            Tab(
                selected = studioSubTab == 1,
                onClick = { studioSubTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Veo 3 Video", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                modifier = Modifier.testTag("veo_video_tab")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (studioSubTab == 1) {
            VeoStudioSection(
                promptText = veoPromptText,
                onPromptChange = { viewModel.setVeoPromptText(it) },
                imageUri = veoImageUri,
                onImageSelected = { viewModel.setVeoImageUri(it) },
                selectedAspectRatio = veoAspectRatio,
                onAspectRatioChange = { viewModel.setVeoAspectRatio(it) },
                selectedMotion = veoMotionType,
                onMotionChange = { viewModel.setVeoMotionType(it) },
                isGenerating = isGeneratingVideo,
                generatedResult = generatedVideoResult,
                error = videoGenerationError,
                onGenerateVideo = { viewModel.generateVeoVideo() },
                onPublishPost = { viewModel.publishVeoVideoAsPost() }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

        // Generated Result Showcase (if exists)
        generatedResult?.let { result ->
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_result_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generated Result",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = result.modelName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Before/After tab selector if reference image was provided
                    if (result.inputImageUri != null) {
                        TabRow(
                            selectedTabIndex = activeResultTab,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Tab(
                                selected = activeResultTab == 0,
                                onClick = { activeResultTab = 0 },
                                text = { Text("Gemini Artwork", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = activeResultTab == 1,
                                onClick = { activeResultTab = 1 },
                                text = { Text("Uploaded Reference", fontWeight = FontWeight.SemiBold) }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Display active image (Generated result vs Uploaded reference)
                    val displayUri = if (activeResultTab == 0 || result.inputImageUri == null) {
                        result.resultImageUri
                    } else {
                        result.inputImageUri
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = displayUri,
                            contentDescription = "Gemini artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    when (result.aspectRatio) {
                                        "16:9" -> 16f / 9f
                                        "9:16" -> 9f / 16f
                                        "4:3" -> 4f / 3f
                                        else -> 1f
                                    }
                                )
                                .testTag("generated_artwork_image")
                        )

                        // Floating style pill on artwork
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (activeResultTab == 0) "✨ ${result.style}" else "📷 Reference",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Prompt used display
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Prompt Used",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { copyPromptToClipboard(result.prompt) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Prompt",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = result.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (result.textExplanation.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = result.textExplanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Result Actions: Save to Feed, Share, New
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.publishGeneratedImageAsPost() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_to_feed_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Publish,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Publish to Feed", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { shareResult("Gemini Artwork", result.prompt, result.resultImageUri) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = { viewModel.clearGeneratedImageResult() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "New",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Section 1: Upload User Image (Reference / Transformation Target)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reference Image",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(Optional)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (inputImageUri != null) {
                        IconButton(
                            onClick = { viewModel.clearCreateImage() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Image",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (inputImageUri == null) {
                    // Upload Picker Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("create_image_picker"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap to upload your image",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Gemini will transform it based on your prompt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Sample Buttons (Useful for emulator testing)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Or try sample:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                createSampleReferenceImage(
                                    name = "Cyber Hero",
                                    color1 = android.graphics.Color.rgb(15, 23, 42),
                                    color2 = android.graphics.Color.rgb(244, 63, 94)
                                )
                            },
                            label = { Text("Cyberpunk Ref", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                createSampleReferenceImage(
                                    name = "Nature Landscape",
                                    color1 = android.graphics.Color.rgb(6, 78, 59),
                                    color2 = android.graphics.Color.rgb(251, 191, 36)
                                )
                            },
                            label = { Text("Landscape Ref", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                createSampleReferenceImage(
                                    name = "Character Portrait",
                                    color1 = android.graphics.Color.rgb(88, 28, 135),
                                    color2 = android.graphics.Color.rgb(147, 197, 253)
                                )
                            },
                            label = { Text("Portrait Ref", fontSize = 12.sp) }
                        )
                    }
                } else {
                    // Uploaded preview thumbnail
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = inputImageUri,
                            contentDescription = "Uploaded preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "✓ Image Attached",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ready to send alongside your prompt to Gemini",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Change Image",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2: Prompt Input & Controls
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Creation Prompt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Quick suggestion button
                    Text(
                        text = "Need ideas?",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val suggestions = listOf(
                                    "Transform this into an intricate cyberpunk city street at night, neon reflections in rain puddles, holographic billboards, volumetric purple atmospheric haze",
                                    "Render this subject as a breathtaking anime masterwork by Studio Ghibli, vibrant lush meadow, fluffy clouds, golden sunbeams, emotive cinematic lighting",
                                    "Reimagine as an ultra-detailed 8k photorealistic oil painting, thick impasto strokes, rich dramatic chiaroscuro Rembrandt lighting, timeless museum masterpiece",
                                    "Futuristic mechanical cyborg armor with translucent glass panels, glowing internal circuits, high precision 3D Octane render, clean minimalist studio backdrop"
                                )
                                viewModel.setCreatePromptText(suggestions.random())
                            }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { viewModel.setCreatePromptText(it) },
                    placeholder = {
                        Text("Describe what to create or how to transform the image... e.g. \"Epic neon cyberpunk warrior in the rain, 8k resolution, cinematic lighting\"")
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_prompt_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Style Presets
                Text(
                    text = "Art Style Preset",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stylePresets.forEach { style ->
                        val isSelected = stylePreset == style
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCreateStylePreset(style) },
                            label = {
                                Text(
                                    text = style,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Aspect Ratio Selector
                Text(
                    text = "Aspect Ratio",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    aspectRatios.forEach { ratio ->
                        val isSelected = aspectRatio == ratio
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCreateAspectRatio(ratio) },
                            label = {
                                Text(
                                    text = when (ratio) {
                                        "1:1" -> "1:1 Square"
                                        "16:9" -> "16:9 Landscape"
                                        "9:16" -> "9:16 Portrait"
                                        "4:3" -> "4:3 Classic"
                                        else -> ratio
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Error message if any
        generationError?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main Submit Button: Generate with Gemini
        Button(
            onClick = { viewModel.generateGeminiImage() },
            enabled = !isGenerating && (promptText.isNotBlank() || inputImageUri != null),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("generate_gemini_button")
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Gemini is generating...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (inputImageUri != null) "Transform with Gemini" else "Generate with Gemini",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (isGenerating) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Sending image & prompt to $createModelName model...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        } // Close inner Column for studioSubTab == 0
    } // Close else
    } // Close outer Column
} // Close fun GeminiCreateScreen

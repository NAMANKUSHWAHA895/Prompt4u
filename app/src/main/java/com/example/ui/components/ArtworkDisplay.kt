package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun ArtworkDisplay(
    imageUrl: String,
    title: String,
    category: String,
    modifier: Modifier = Modifier
) {
    if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://") || imageUrl.startsWith("http")) {
        val context = LocalContext.current
        val imageRequest = remember(imageUrl) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = title,
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentScale = ContentScale.FillWidth
        )
    } else {
        // Render rich artistic stylized banner for presets
        PresetArtBanner(
            presetKey = imageUrl,
            title = title,
            category = category,
            modifier = modifier
        )
    }
}

@Composable
fun PresetArtBanner(
    presetKey: String,
    title: String,
    category: String,
    modifier: Modifier = Modifier
) {
    val (gradientColors, artIcon, badgeColor) = when (presetKey) {
        "preset_cyberpunk" -> Triple(
            listOf(Color(0xFF0D0221), Color(0xFF261447), Color(0xFF05D9E8)),
            Icons.Default.AutoAwesome,
            Color(0xFF05D9E8)
        )
        "preset_fantasy" -> Triple(
            listOf(Color(0xFF1E1B4B), Color(0xFF4C1D95), Color(0xFFEC4899), Color(0xFFFDE047)),
            Icons.Default.Castle,
            Color(0xFFF472B6)
        )
        "preset_anime" -> Triple(
            listOf(Color(0xFF065F46), Color(0xFF10B981), Color(0xFF6EE7B7), Color(0xFFFBBF24)),
            Icons.Default.Landscape,
            Color(0xFF34D399)
        )
        "preset_photorealistic" -> Triple(
            listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF0284C7), Color(0xFF38BDF8)),
            Icons.Default.CameraAlt,
            Color(0xFF38BDF8)
        )
        "preset_isometric" -> Triple(
            listOf(Color(0xFF18181B), Color(0xFF3F3F46), Color(0xFFA855F7), Color(0xFFF43F5E)),
            Icons.Default.Home,
            Color(0xFFA855F7)
        )
        "preset_architecture" -> Triple(
            listOf(Color(0xFF451A03), Color(0xFF78350F), Color(0xFFD97706), Color(0xFFFDE68A)),
            Icons.Default.LocationCity,
            Color(0xFFF59E0B)
        )
        else -> Triple(
            listOf(Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF6366F1)),
            Icons.Default.Palette,
            Color(0xFF818CF8)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(gradientColors))
    ) {
        // Decorative geometric shapes / subtle ambient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        // Center artistic badge & iconography
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.4f),
                shadowElevation = 8.dp,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = artIcon,
                        contentDescription = category,
                        tint = badgeColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = category.uppercase(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

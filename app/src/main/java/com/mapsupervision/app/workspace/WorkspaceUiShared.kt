package com.mapsupervision.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.MediaType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Re-export or import theme and core components from core module
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.core.ui.components.*

// Gradients
val NeonCyberOrangeGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFB074), // PrimaryPeach
        Color(0xFFFF8F00)  // PrimaryContainer
    )
)

val NeonCyberCyanGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    colors = listOf(
        Color(0xFF00FFCC), // SecondaryMint
        Color(0xFF00E5FF)  // TertiaryCyan
    )
)

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun toDriveDirectUrl(urlOrId: String, width: Int = 500): String? {
    val trimmed = urlOrId.trim()
    if (trimmed.isBlank()) return null
    val idMatch = Regex("[?&]id=([A-Za-z0-9_-]+)").find(trimmed)
        ?: Regex("/d/([A-Za-z0-9_-]+)").find(trimmed)
        ?: Regex("/files?/d/([A-Za-z0-9_-]+)").find(trimmed)
    val fileId = idMatch?.groupValues?.get(1) ?: if (trimmed.matches(Regex("^[A-Za-z0-9_-]{15,}$"))) trimmed else null
    return fileId?.let { "https://lh3.googleusercontent.com/d/$it=w$width" }
}

@Composable
fun SitePhotoThumb(
    photo: SitePhoto,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val thumbFile = File(photo.thumbnailPath.ifBlank { photo.filePath })
    val context = LocalContext.current
    val theme = MaterialTheme.colorScheme
    val remoteDriveUrl = photo.remoteUrl?.let { toDriveDirectUrl(it, 400) }
    val modelToLoad = when {
        thumbFile.exists() -> thumbFile
        !remoteDriveUrl.isNullOrBlank() -> remoteDriveUrl
        else -> null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(theme.surfaceVariant)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        if (modelToLoad != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(modelToLoad)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.objectCode,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = null
            )
        } else {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = theme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
            )
        }

        if (!thumbFile.exists() && !photo.remoteUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .background(Color(0xE6F97316), RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text("Drive", color = Color.White, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }

        val ts = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(photo.capturedAtEpochMs))
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(theme.scrim.copy(alpha = 0.68f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(ts, color = theme.onSurface, fontSize = 9.sp)
        }

        if (photo.mediaType == MediaType.VIDEO) {
            Icon(
                Icons.Outlined.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
            val durationText = formatDuration(photo.durationMs)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(theme.scrim.copy(alpha = 0.68f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(durationText, color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

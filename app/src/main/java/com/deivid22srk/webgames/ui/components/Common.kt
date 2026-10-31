package com.deivid22srk.webgames.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ícone de jogo carregado da rede (HTTPS) com decodificação de bitmap. */
@Composable
fun WebGameImage(
    url: String?,
    modifier: Modifier = Modifier,
    placeholderIcon: @Composable () -> Unit = {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    },
) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "WebGamesLauncher/0.1")
                BitmapFactory.decodeStream(conn.inputStream)
            }.getOrNull()
        }?.asImageBitmap()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) { placeholderIcon() }
    }
}

/**
 * Forma expressiva em morphing contínuo entre dois presets de [RoundedPolygon]
 * (MaterialShapes). Usada como `Modifier.clip(shape)` em cartões/decorações.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberMorphingShape(
    from: RoundedPolygon,
    to: RoundedPolygon,
    durationMs: Int = 3_200,
): Shape {
    val transition = rememberInfiniteTransition(label = "shape-morph")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "morph-progress",
    )
    val morph = remember(from, to) { Morph(from, to) }
    return remember(morph, progress) {
        val animated = morph.toPath(progress)
        object : Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density,
            ): Outline {
                val path = Path().apply { addPath(animated) }
                val matrix = androidx.compose.ui.graphics.Matrix().apply {
                    scale(size.width / 100f, size.height / 100f, 1f)
                }
                path.transform(matrix)
                return Outline.Generic(path)
            }
        }
    }
}

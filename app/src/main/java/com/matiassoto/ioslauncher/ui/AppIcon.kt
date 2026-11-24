package com.matiassoto.ioslauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matiassoto.ioslauncher.data.AppInfo

@Composable
fun SquircleIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    Image(
        bitmap = app.icon,
        contentDescription = app.label,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .shadow(3.dp, SquircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(SquircleShape),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconView(
    app: AppInfo,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    jiggle: Boolean = false,
    invisible: Boolean = false,
    showMinus: Boolean = false,
    onMinus: () -> Unit = {},
    labelColor: Color = Color.White,
    labelShadow: Boolean = true,
    onIconBounds: ((Rect) -> Unit)? = null,
    onLongClick: ((Rect) -> Unit)? = null,
    onClick: ((Rect) -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(stiffness = 600f), label = "press")
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val rotation = if (jiggle) {
        val inf = rememberInfiniteTransition(label = "jiggle")
        val period = 120 + (app.key.hashCode() and 0x3F)
        inf.animateFloat(
            initialValue = -1.8f, targetValue = 1.8f,
            animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Reverse),
            label = "rot",
        ).value
    } else 0f

    val clickMod = when {
        onClick == null -> Modifier
        onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interaction, indication = null,
            onLongClick = { onLongClick(bounds) }, onClick = { onClick(bounds) },
        )
        else -> Modifier.clickable(interactionSource = interaction, indication = null) { onClick(bounds) }
    }

    Column(
        modifier = modifier
            .graphicsLayer { alpha = if (invisible) 0f else 1f; rotationZ = rotation }
            .then(clickMod),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            SquircleIcon(
                app, iconSize,
                Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .onGloballyPositioned { val r = it.boundsInRoot(); bounds = r; onIconBounds?.invoke(r) },
            )
            if (showMinus) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset((-6).dp, (-6).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xE6D1D1D6))
                        .clickable { onMinus() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Remove, "Remove", tint = Color.Black, modifier = Modifier.size(16.dp)) }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                app.label,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = InterFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                    shadow = if (labelShadow) Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f) else null,
                ),
                modifier = Modifier.width(iconSize + 16.dp).padding(horizontal = 1.dp),
            )
        }
    }
}

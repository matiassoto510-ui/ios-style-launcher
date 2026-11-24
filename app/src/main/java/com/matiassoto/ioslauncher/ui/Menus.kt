package com.matiassoto.ioslauncher.ui

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matiassoto.ioslauncher.LauncherViewModel
import com.matiassoto.ioslauncher.MenuState
import kotlin.math.roundToInt

private data class MenuItem(val label: String, val icon: ImageVector, val destructive: Boolean = false, val action: () -> Unit)

/** iOS-style long-press "quick actions" menu. */
@Composable
fun ContextMenuOverlay(vm: LauncherViewModel, view: View) {
    var last by remember { mutableStateOf<MenuState?>(null) }
    vm.menu?.let { last = it }
    AnimatedVisibility(vm.menu != null, enter = fadeIn(), exit = fadeOut()) {
        val m = last ?: return@AnimatedVisibility
        val app = vm.apps[m.key] ?: return@AnimatedVisibility
        val density = LocalDensity.current
        fun dismiss() { vm.menu = null }
        fun rectOpts(): Pair<android.graphics.Rect, android.os.Bundle> {
            val r = m.bounds
            val rect = android.graphics.Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
            val o = ActivityOptions.makeScaleUpAnimation(view, rect.left, rect.top, rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1)).toBundle()
            return rect to o
        }
        val onHome = m.key in vm.grid || m.key in vm.dock
        val items = buildList {
            if (!m.fromLibrary) add(MenuItem("Edit Home Screen", Icons.Outlined.Apps) { dismiss(); vm.editMode = true })
            if (!onHome) add(MenuItem("Add to Home Screen", Icons.Outlined.AddBox) { dismiss(); vm.addToHome(m.key) })
            add(MenuItem("App Info", Icons.Outlined.Info) { dismiss(); val (r, o) = rectOpts(); vm.openAppInfo(m.key, r, o) })
            if (onHome) add(MenuItem("Remove App", Icons.Outlined.RemoveCircleOutline, destructive = true) { dismiss(); vm.removeDialogFor = m.key })
            else if (!app.isSystem) add(MenuItem("Delete App", Icons.Outlined.Delete, destructive = true) { dismiss(); uninstall(view, app.packageName) })
        }

        BoxWithConstraints(
            Modifier.fillMaxSize()
                .glass(RoundedCornerShape(0.dp), tint = Color.Black.copy(alpha = 0.28f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() }
        ) {
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            val menuW = with(density) { 250.dp.toPx() }
            val menuH = with(density) { (items.size * 48 + 4).dp.toPx() }
            val gap = with(density) { 12.dp.toPx() }
            val b = m.bounds
            val below = b.bottom + gap + menuH < screenH - gap * 4
            val mx = b.left.coerceIn(gap, screenW - menuW - gap)
            val my = if (below) b.bottom + gap else (b.top - gap - menuH).coerceAtLeast(gap)

            // Lifted icon
            SquircleIcon(
                app, with(density) { b.width.toDp() },
                Modifier
                    .offset { IntOffset(b.left.roundToInt(), b.top.roundToInt()) }
                    .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f },
            )

            AnimatedVisibility(
                visible = vm.menu != null,
                enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.6f,
                    transformOrigin = TransformOrigin(0.1f, if (below) 0f else 1f)) + fadeIn(),
                exit = scaleOut(targetScale = 0.7f) + fadeOut(),
                modifier = Modifier.offset { IntOffset(mx.roundToInt(), my.roundToInt()) },
            ) {
                Column(Modifier.width(250.dp).glass(RoundedCornerShape(14.dp), strong = true)) {
                    items.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(color = glassTextColor().copy(alpha = 0.12f), thickness = 0.5.dp)
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clickable { item.action() }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val c = if (item.destructive) IosRed else glassTextColor()
                            Text(item.label, color = c, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Icon(item.icon, null, tint = c, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

fun uninstall(view: View, pkg: String) {
    val i = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { view.context.startActivity(i) }
}

/** iOS-style alert: Delete App / Remove from Home Screen / Cancel. */
@Composable
fun RemoveAppDialog(vm: LauncherViewModel) {
    val key = vm.removeDialogFor
    val view = androidx.compose.ui.platform.LocalView.current
    AnimatedVisibility(key != null, enter = fadeIn() + scaleIn(initialScale = 1.1f), exit = fadeOut()) {
        var lastKey by remember { mutableStateOf(key) }
        if (key != null) lastKey = key
        val app = lastKey?.let { vm.apps[it] } ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize().glass(RoundedCornerShape(0.dp), tint = Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.removeDialogFor = null },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.width(280.dp).glass(RoundedCornerShape(16.dp), strong = true)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Remove \u201C${app.label}\u201D?", color = glassTextColor(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp),
                )
                Text(
                    if (app.isSystem) "This is a system app and can't be deleted. Removing it from the Home Screen keeps it in the App Library."
                    else "Removing from Home Screen will keep the app in your App Library.",
                    color = glassTextColor().copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                )
                if (!app.isSystem) Btn("Delete App", IosRed) { vm.removeDialogFor = null; uninstall(view, app.packageName) }
                Btn("Remove from Home Screen", IosBlue) { vm.removeDialogFor = null; vm.hideFromHome(app.key) }
                Btn("Cancel", IosBlue, bold = true) { vm.removeDialogFor = null }
            }
        }
    }
}

@Composable
private fun Btn(label: String, color: Color, bold: Boolean = false, onClick: () -> Unit) {
    HorizontalDivider(color = glassTextColor().copy(alpha = 0.15f), thickness = 0.5.dp)
    Text(
        label, color = color, fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

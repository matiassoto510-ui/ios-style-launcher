package com.matiassoto.ioslauncher.ui

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matiassoto.ioslauncher.LauncherViewModel
import com.matiassoto.ioslauncher.data.AppInfo

/** Spotlight-style search over installed apps (pull down on the Home Screen or tap "Search"). */
@Composable
fun SearchOverlay(vm: LauncherViewModel, visible: Boolean, onLaunch: (String, Rect) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 8 },
        exit = fadeOut() + slideOutVertically { -it / 8 },
    ) {
        val context = LocalContext.current
        val keyboard = LocalSoftwareKeyboardController.current
        var query by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() }; keyboard?.show() }
        fun close() { keyboard?.hide(); query = ""; vm.searchOpen = false }

        val results: List<AppInfo> = remember(query, vm.allApps) {
            if (query.isBlank()) emptyList()
            else vm.allApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
                .sortedWith(compareBy<AppInfo> { !it.label.startsWith(query.trim(), ignoreCase = true) }.thenBy { it.label.lowercase() })
        }
        val suggestions: List<AppInfo> = remember(vm.dock, vm.grid, vm.apps) {
            (vm.dock + vm.grid).mapNotNull { vm.apps[it] }.take(8)
        }

        Box(
            Modifier.fillMaxSize()
                .glass(RoundedCornerShape(0.dp), tint = Color.Black.copy(alpha = 0.38f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { keyboard?.hide() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy -> if (dy < -40) close() }
                }
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).height(44.dp).glass(RoundedCornerShape(14.dp), tint = Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search", color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp)
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontFamily = InterFamily),
                                cursorBrush = SolidColor(IosBlue),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    results.firstOrNull()?.let { close(); onLaunch(it.key, Rect.Zero) }
                                }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Filled.Cancel, "Clear", tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp).clickable { query = "" },
                            )
                        }
                    }
                    Text("Cancel", color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(start = 12.dp).clickable { close() })
                }
                Spacer(Modifier.height(18.dp))

                val topHits = if (query.isBlank()) suggestions else results.take(4)
                if (topHits.isNotEmpty()) {
                    Text(
                        if (query.isBlank()) "Suggestions" else "Top Hits",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), tint = Color.White.copy(alpha = 0.12f)).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        topHits.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                for (i in 0 until 4) {
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        row.getOrNull(i)?.let { app ->
                                            AppIconView(app, 56.dp, onClick = { r -> close(); onLaunch(app.key, r) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (query.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f, fill = false)
                            .glass(RoundedCornerShape(18.dp), tint = Color.White.copy(alpha = 0.12f)),
                    ) {
                        items(results.drop(4), key = { it.key }) { app ->
                            var b by remember { mutableStateOf(Rect.Zero) }
                            Row(
                                Modifier.fillMaxWidth().clickable { close(); onLaunch(app.key, b) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SquircleIcon(app, 36.dp, Modifier.onGloballyPositioned { b = it.boundsInRoot() })
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, color = Color.White, fontSize = 17.sp)
                            }
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val q = query.trim()
                                    close()
                                    val web = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    val ok = runCatching { context.startActivity(web) }.isSuccess
                                    if (!ok) runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Public, null, tint = IosBlue, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Search the web for \u201C${query.trim()}\u201D", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

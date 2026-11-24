package com.matiassoto.ioslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matiassoto.ioslauncher.LauncherViewModel
import com.matiassoto.ioslauncher.data.AppInfo

private data class LibraryGroup(val name: String, val apps: List<AppInfo>)

private val labelStyle = TextStyle(
    fontFamily = InterFamily, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
    shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 1f), 4f), textAlign = TextAlign.Center,
)

@Composable
fun AppLibraryPage(
    vm: LauncherViewModel,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onLaunch: (String, Rect) -> Unit,
    onLongPress: (String, Rect) -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<LibraryGroup?>(null) }
    val all = vm.allApps
    val groups = remember(all) {
        val recent = all.filter { it.installTime > 0 }.sortedByDescending { it.installTime }.take(4)
        val byCat = all.groupBy { it.category }.map { (k, v) -> LibraryGroup(k, v) }
            .sortedWith(compareByDescending<LibraryGroup> { it.apps.size }.thenBy { it.name })
        (if (recent.isNotEmpty()) listOf(LibraryGroup("Recently Added", recent)) else emptyList()) + byCat
    }

    BackHandler(enabled = searching || openGroup != null) {
        if (openGroup != null) openGroup = null else { searching = false; query = "" }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = topPadding)) {
            LibrarySearchBar(
                searching = searching, query = query,
                onQuery = { query = it },
                onStart = { searching = true },
                onCancel = { searching = false; query = "" },
            )
            Spacer(Modifier.height(12.dp))
            if (searching) {
                val filtered = remember(all, query) {
                    all.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                }
                AlphabeticalList(filtered, bottomPadding, onLaunch, onLongPress)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding + 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(groups, key = { it.name }) { g ->
                        CategoryCard(g, onLaunch, onLongPress, onOpen = { openGroup = g })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = openGroup != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
        ) {
            val g = openGroup
            if (g != null) {
                Box(
                    Modifier.fillMaxSize().glass(RoundedCornerShape(0.dp), tint = Color.Black.copy(alpha = 0.35f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openGroup = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Text(g.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(36.dp)).padding(vertical = 16.dp)) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth().height(((g.apps.size + 3) / 4 * 92).coerceAtMost(460).dp),
                            ) {
                                items(g.apps, key = { it.key }) { app ->
                                    Box(contentAlignment = Alignment.Center) {
                                        AppIconView(
                                            app, 58.dp,
                                            onLongClick = { r -> onLongPress(app.key, r) },
                                            onClick = { r -> openGroup = null; onLaunch(app.key, r) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchBar(
    searching: Boolean, query: String, onQuery: (String) -> Unit, onStart: () -> Unit, onCancel: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(40.dp).glass(RoundedCornerShape(12.dp))
                .clickable(enabled = !searching, onClick = onStart)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (searching) Arrangement.Start else Arrangement.Center,
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            if (searching) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("App Library", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                    BasicTextField(
                        value = query, onValueChange = onQuery, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = InterFamily),
                        cursorBrush = SolidColor(IosBlue),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            } else {
                Text("App Library", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (searching) {
            Text(
                "Cancel", color = Color.White, fontSize = 16.sp,
                modifier = Modifier.padding(start = 12.dp).clickable(onClick = onCancel),
            )
        }
    }
}

@Composable
private fun CategoryCard(
    g: LibraryGroup, onLaunch: (String, Rect) -> Unit, onLongPress: (String, Rect) -> Unit, onOpen: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(1f).glass(RoundedCornerShape(28.dp)).padding(14.dp)
        ) {
            val cell = (maxWidth - 14.dp) / 2
            val showCluster = g.apps.size > 4
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (r in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (c in 0 until 2) {
                            val idx = r * 2 + c
                            Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                if (idx == 3 && showCluster) {
                                    MiniCluster(g.apps.drop(3).take(4), cell, onOpen)
                                } else {
                                    g.apps.getOrNull(idx)?.let { app ->
                                        LibraryIcon(app, cell, onLaunch, onLongPress)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(g.name, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryIcon(app: AppInfo, size: androidx.compose.ui.unit.Dp, onLaunch: (String, Rect) -> Unit, onLongPress: (String, Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    SquircleIcon(
        app, size,
        Modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .combinedClickable(
                onClick = { onLaunch(app.key, bounds) },
                onLongClick = { onLongPress(app.key, bounds) },
            ),
    )
}

@Composable
private fun MiniCluster(apps: List<AppInfo>, size: androidx.compose.ui.unit.Dp, onOpen: () -> Unit) {
    val mini = (size - 6.dp) / 2
    Column(
        Modifier.size(size).clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 0 until 2) {
                val a = apps.getOrNull(r * 2 + c)
                if (a != null) SquircleIcon(a, mini) else Spacer(Modifier.size(mini))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlphabeticalList(
    apps: List<AppInfo>, bottomPadding: androidx.compose.ui.unit.Dp,
    onLaunch: (String, Rect) -> Unit, onLongPress: (String, Rect) -> Unit,
) {
    val grouped = remember(apps) {
        apps.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }.toSortedMap()
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).glass(RoundedCornerShape(20.dp), strong = true),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
    ) {
        grouped.forEach { (letter, list) ->
            stickyHeader(key = "h_$letter") {
                Text(
                    letter.toString(),
                    color = glassTextColor().copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(list, key = { it.key }) { app ->
                var bounds by remember { mutableStateOf(Rect.Zero) }
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { onLaunch(app.key, bounds) }, onLongClick = { onLongPress(app.key, bounds) })
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SquircleIcon(app, 40.dp, Modifier.onGloballyPositioned { bounds = it.boundsInRoot() })
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, color = glassTextColor(), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

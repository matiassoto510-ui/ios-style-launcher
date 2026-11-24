package com.matiassoto.ioslauncher.ui

import android.app.ActivityOptions
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.matiassoto.ioslauncher.DOCK_CAPACITY
import com.matiassoto.ioslauncher.LauncherViewModel
import com.matiassoto.ioslauncher.MenuState
import com.matiassoto.ioslauncher.data.WallpaperUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Tracks the on-screen rects of every home/dock cell, plus the current drag. */
class DragController {
    val cells = HashMap<String, Pair<Any, Rect>>()
    val iconRects = HashMap<String, Rect>()
    val pageAreas = HashMap<Int, Rect>()
    var dockArea: Rect = Rect.Zero
    var draggingKey by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var grabOffset: Offset = Offset.Zero
    var dragIconSize: Size = Size.Zero
    var lastMove = 0L

    fun hit(pos: Offset): String? = cells.entries.firstOrNull { it.value.second.contains(pos) }?.key
}

@Composable
private fun Modifier.registerCell(dc: DragController, key: String): Modifier {
    val token = remember(key) { Any() }
    DisposableEffect(key) { onDispose { if (dc.cells[key]?.first === token) dc.cells.remove(key) } }
    return this.onGloballyPositioned { dc.cells[key] = token to it.boundsInRoot() }
}

private val DockHeight = 92.dp
private val DotsHeight = 34.dp

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val defaultWp = remember(dark) { WallpaperUtils.defaultWallpaper(dark) }
    val wpBitmap = vm.customWallpaper ?: defaultWp
    val wallpaper = remember(wpBitmap) { wpBitmap.asImageBitmap() }
    val blurred = remember(wpBitmap) { WallpaperUtils.blur(wpBitmap).asImageBitmap() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val dc = remember { DragController() }

    val pagerState = rememberPagerState { vm.appPageCount + 1 }
    val libraryPage = vm.appPageCount
    val onLibrary by remember(libraryPage) {
        derivedStateOf { (pagerState.currentPage + pagerState.currentPageOffsetFraction) > libraryPage - 0.5f }
    }

    LaunchedEffect(Unit) { vm.homeEvents.collect { pagerState.animateScrollToPage(0) } }
    val zoom = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        vm.resumeEvents.collect {
            zoom.snapTo(1.06f)
            zoom.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 260f))
        }
    }

    BackHandler {
        when {
            vm.menu != null -> vm.menu = null
            vm.removeDialogFor != null -> vm.removeDialogFor = null
            vm.settingsOpen -> vm.settingsOpen = false
            vm.searchOpen -> vm.searchOpen = false
            vm.editMode -> vm.exitEdit()
            pagerState.currentPage != 0 -> scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    fun launchApp(key: String, r: Rect) {
        if (vm.editMode) return
        val opts = ActivityOptions.makeScaleUpAnimation(
            view, r.left.roundToInt(), r.top.roundToInt(), r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)
        ).toBundle()
        vm.launch(key, android.graphics.Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()), opts)
    }

    fun hover(pos: Offset) {
        val key = dc.draggingKey ?: return
        val now = System.currentTimeMillis()
        if (now - dc.lastMove < 220) return
        val beforeG = vm.grid; val beforeD = vm.dock
        val target = dc.cells.entries.firstOrNull { it.key != key && it.value.second.contains(pos) }?.key
        if (target != null) {
            if (target in vm.dock) vm.move(key, true, vm.dock.indexOf(target))
            else if (target in vm.grid) vm.move(key, false, vm.grid.indexOf(target))
        } else if (dc.dockArea.contains(pos)) {
            if (key !in vm.dock && vm.dock.size < DOCK_CAPACITY) vm.move(key, true, vm.dock.size)
        } else {
            val page = pagerState.currentPage
            if (page < vm.appPageCount && dc.pageAreas[page]?.contains(pos) == true &&
                dc.cells[key]?.second?.contains(pos) != true
            ) vm.moveToEndOfPage(key, page)
        }
        if (beforeG !== vm.grid || beforeD !== vm.dock) dc.lastMove = now
    }

    // Edge-of-screen page flipping while dragging an icon.
    LaunchedEffect(dc.draggingKey) {
        if (dc.draggingKey == null) return@LaunchedEffect
        val edge = with(density) { 28.dp.toPx() }
        var lastFlip = 0L
        while (true) {
            delay(90)
            val x = dc.pointer.x
            val now = System.currentTimeMillis()
            if (now - lastFlip < 900) continue
            val page = pagerState.currentPage
            if (x < edge && page > 0) { lastFlip = now; pagerState.animateScrollToPage(page - 1) }
            else if (x > rootSize.width - edge && page < vm.appPageCount - 1) { lastFlip = now; pagerState.animateScrollToPage(page + 1) }
        }
    }

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (vm.menu != null || vm.searchOpen || vm.settingsOpen || vm.removeDialogFor != null) return@awaitEachGesture
            if (pagerState.currentPage >= vm.appPageCount) {
                // App Library: only the dock area could be hit, and it's hidden there.
                return@awaitEachGesture
            }
            val key = dc.hit(down.position) ?: return@awaitEachGesture
            val slop = viewConfiguration.touchSlop

            fun startDrag(pos: Offset) {
                val r = dc.iconRects[key] ?: dc.cells[key]?.second ?: Rect(pos, Size(1f, 1f))
                dc.grabOffset = down.position - r.topLeft
                dc.dragIconSize = r.size
                dc.pointer = pos
                dc.lastMove = 0L
                dc.draggingKey = key
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            if (!vm.editMode) {
                val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed) break
                        if ((ch.position - down.position).getDistance() > slop) break
                    }
                }
                if (result != null) return@awaitEachGesture // released or moved before long-press
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.menu = MenuState(key, dc.iconRects[key] ?: dc.cells[key]!!.second, fromLibrary = false)
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    ev.changes.forEach { it.consume() }
                    if (ch == null || !ch.pressed) break
                    if ((ch.position - down.position).getDistance() > slop * 2) {
                        vm.menu = null
                        vm.editMode = true
                        startDrag(ch.position)
                        try { trackDrag(down.id) { dc.pointer = it; hover(it) } } finally { dc.draggingKey = null; vm.persist() }
                        break
                    }
                }
            } else {
                var drag = false
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    if ((ch.position - down.position).getDistance() > slop) { ch.consume(); drag = true; break }
                }
                if (drag) {
                    startDrag(down.position)
                    try { trackDrag(down.id) { dc.pointer = it; hover(it) } } finally { dc.draggingKey = null; vm.persist() }
                }
            }
        }
    }

    val glassInfo = GlassInfo(blurred, rootSize, dark)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomReserved = DockHeight + DotsHeight + navBottom + 10.dp

    CompositionLocalProvider(LocalGlass provides glassInfo) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = it }
                .drawBehind {
                    drawImage(
                        wallpaper, srcOffset = IntOffset.Zero, srcSize = IntSize(wallpaper.width, wallpaper.height),
                        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                }
                .then(gestureModifier)
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }) {
                if (vm.loaded) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondBoundsPageCount = 1,
                    ) { page ->
                        if (page < vm.appPageCount) {
                            HomePage(
                                vm = vm, page = page, dc = dc,
                                topPadding = statusTop + 10.dp, bottomPadding = bottomReserved,
                                onLaunch = ::launchApp,
                                onPullDown = { vm.searchOpen = true },
                                onLongPressEmpty = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.editMode = true },
                            )
                        } else {
                            AppLibraryPage(
                                vm = vm, topPadding = statusTop + 8.dp, bottomPadding = navBottom + 8.dp,
                                onLaunch = ::launchApp,
                                onLongPress = { key, r -> haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.menu = MenuState(key, r, true) },
                            )
                        }
                    }
                }

                // Dots / search pill + dock
                val dockAlpha by animateFloatAsState(if (onLibrary) 0f else 1f, tween(220), label = "dock")
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(bottom = navBottom + 10.dp)
                        .graphicsLayer { alpha = dockAlpha; translationY = (1f - dockAlpha) * 120f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PageIndicator(
                        pagerState, vm.appPageCount + 1,
                        showSearchPill = !pagerState.isScrollInProgress && !vm.editMode && dc.draggingKey == null,
                        onSearch = { vm.searchOpen = true },
                        modifier = Modifier.height(DotsHeight),
                    )
                    if (dockAlpha > 0.01f) Dock(vm, dc, onLaunch = ::launchApp)
                    else Spacer(Modifier.height(DockHeight))
                }
            }

            // Edit-mode top bar
            AnimatedVisibility(
                visible = vm.editMode,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = statusTop + 4.dp, start = 14.dp, end = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GlassPill(onClick = { vm.settingsOpen = true }) {
                        Icon(Icons.Outlined.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Settings", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    GlassPill(onClick = { vm.exitEdit() }) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }

            // Icon being dragged
            val dragKey = dc.draggingKey
            val dragApp = dragKey?.let { vm.apps[it] }
            if (dragApp != null) {
                val sizeDp = with(density) { dc.dragIconSize.width.toDp() }
                val p = dc.pointer - dc.grabOffset
                SquircleIcon(
                    dragApp, sizeDp,
                    Modifier
                        .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                        .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.95f }
                        .shadow(12.dp, SquircleShape),
                )
            }

            SearchOverlay(vm, visible = vm.searchOpen, onLaunch = ::launchApp)
            ContextMenuOverlay(vm, view)
            RemoveAppDialog(vm)
            SettingsOverlay(vm, visible = vm.settingsOpen)
        }
    }
}

private suspend fun AwaitPointerEventScope.trackDrag(id: PointerId, onMove: (Offset) -> Unit) {
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        val ch = ev.changes.firstOrNull { it.id == id }
        ev.changes.forEach { it.consume() }
        if (ch == null || !ch.pressed) break
        onMove(ch.position)
    }
}

@Composable
fun GlassPill(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .glass(RoundedCornerShape(50), tint = Color.Black.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun PageIndicator(
    pagerState: PagerState, count: Int, showSearchPill: Boolean, onSearch: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(showSearchPill, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            Row(
                Modifier
                    .glass(RoundedCornerShape(50), tint = Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onSearch)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Search", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        AnimatedVisibility(!showSearchPill, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier
                    .glass(RoundedCornerShape(50), tint = Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val current = pagerState.currentPage
                repeat(count) { i ->
                    Box(
                        Modifier.size(7.dp).background(
                            if (i == current) Color.White else Color.White.copy(alpha = 0.4f), CircleShape
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    vm: LauncherViewModel,
    page: Int,
    dc: DragController,
    topPadding: Dp,
    bottomPadding: Dp,
    onLaunch: (String, Rect) -> Unit,
    onPullDown: () -> Unit,
    onLongPressEmpty: () -> Unit,
) {
    var area by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(page) { onDispose { dc.pageAreas.remove(page) } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding, start = 10.dp, end = 10.dp)
            .onGloballyPositioned { area = it.boundsInRoot(); dc.pageAreas[page] = area }
            .pointerInput(page) {
                detectTapGestures(
                    onTap = { if (vm.editMode) vm.exitEdit() },
                    onLongPress = { off ->
                        if (!vm.editMode && dc.hit(area.topLeft + off) == null) onLongPressEmpty()
                    },
                )
            }
            .pointerInput(page) {
                var total = 0f
                var fired = false
                detectVerticalDragGestures(
                    onDragStart = { total = 0f; fired = false },
                    onVerticalDrag = { _, dy ->
                        total += dy
                        if (!fired && !vm.editMode && total > 90f) { fired = true; onPullDown() }
                    },
                )
            }
    ) {
        val rows = 6
        val cellH = maxHeight / rows
        val cellW = maxWidth / 4
        val iconSize = min(62.dp, min(cellW * 0.72f, cellH * 0.62f))
        val items = vm.pageItems(page)
        Column(Modifier.fillMaxSize()) {
            if (page == 0) {
                Row(Modifier.fillMaxWidth().height(cellH * 2)) {
                    Box(Modifier.weight(1f).fillMaxHeight().padding(10.dp), contentAlignment = Alignment.Center) {
                        ClockWidget(Modifier.aspectRatio(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().padding(10.dp), contentAlignment = Alignment.Center) {
                        BatteryWidget(Modifier.aspectRatio(1f))
                    }
                }
            }
            items.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().height(cellH)) {
                    for (i in 0 until 4) {
                        val key = row.getOrNull(i)
                        val app = key?.let { vm.apps[it] }
                        Box(
                            Modifier.weight(1f).fillMaxHeight().then(if (key != null) Modifier.registerCell(dc, key) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (app != null) {
                                AppIconView(
                                    app = app, iconSize = iconSize,
                                    jiggle = vm.editMode,
                                    invisible = dc.draggingKey == key,
                                    showMinus = vm.editMode && dc.draggingKey == null,
                                    onMinus = { vm.removeDialogFor = key },
                                    onIconBounds = { dc.iconRects[key] = it },
                                    onClick = { r -> onLaunch(key, r) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(vm: LauncherViewModel, dc: DragController, onLaunch: (String, Rect) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(DockHeight)
            .onGloballyPositioned { dc.dockArea = it.boundsInRoot() }
            .glass(RoundedCornerShape(34.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val cellW = maxWidth / 4
        val iconSize = min(62.dp, cellW * 0.72f)
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            vm.dock.forEach { key ->
                val app = vm.apps[key] ?: return@forEach
                Box(Modifier.width(cellW).fillMaxHeight().registerCell(dc, key), contentAlignment = Alignment.Center) {
                    AppIconView(
                        app = app, iconSize = iconSize, showLabel = false,
                        jiggle = vm.editMode,
                        invisible = dc.draggingKey == key,
                        showMinus = vm.editMode && dc.draggingKey == null,
                        onMinus = { vm.removeDialogFor = key },
                        onIconBounds = { dc.iconRects[key] = it },
                        onClick = { r -> onLaunch(key, r) },
                    )
                }
            }
        }
    }
}

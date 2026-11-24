package com.matiassoto.ioslauncher

import android.app.Application
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matiassoto.ioslauncher.data.AppInfo
import com.matiassoto.ioslauncher.data.AppRepository
import com.matiassoto.ioslauncher.data.LayoutStore
import com.matiassoto.ioslauncher.data.WallpaperUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val FIRST_PAGE_CAPACITY = 16   // first page has 2 rows of widgets
const val PAGE_CAPACITY = 24         // 4 columns x 6 rows
const val DOCK_CAPACITY = 4

data class MenuState(val key: String, val bounds: Rect, val fromLibrary: Boolean)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val store = LayoutStore(app)
    private var callback: LauncherApps.Callback? = null

    var apps by mutableStateOf<Map<String, AppInfo>>(emptyMap()); private set
    var allApps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    var grid by mutableStateOf<List<String>>(emptyList()); private set
    var dock by mutableStateOf<List<String>>(emptyList()); private set
    var hidden by mutableStateOf<Set<String>>(emptySet()); private set
    var loaded by mutableStateOf(false); private set

    var editMode by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var menu by mutableStateOf<MenuState?>(null)
    var removeDialogFor by mutableStateOf<String?>(null)

    var customWallpaper by mutableStateOf<Bitmap?>(null); private set

    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val homeEvents: SharedFlow<Unit> = _homeEvents
    private val _resumeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val resumeEvents: SharedFlow<Unit> = _resumeEvents

    private var reloadJob: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            customWallpaper = withContext(Dispatchers.IO) { WallpaperUtils.loadCustom(app) }
        }
        reload()
        callback = repo.registerChanges { reload() }
    }

    override fun onCleared() {
        callback?.let { repo.unregister(it) }
        super.onCleared()
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(150)
            val list = repo.loadApps()
            val map = list.associateBy { it.key }
            val saved = store.load()
            if (!saved.initialized) {
                val d = pickDefaultDock(list)
                dock = d
                grid = list.map { it.key }.filter { it !in d }
                hidden = emptySet()
            } else {
                val h = saved.hidden.filter { it in map }.toSet()
                val d = saved.dock.filter { it in map }.distinct().take(DOCK_CAPACITY)
                val g = saved.grid.filter { it in map && it !in d && it !in h }.distinct().toMutableList()
                val known = (g + d + h).toSet()
                list.forEach { if (it.key !in known) g.add(it.key) }
                dock = d; grid = g; hidden = h
            }
            apps = map
            allApps = list
            loaded = true
            persist()
        }
    }

    private fun pickDefaultDock(list: List<AppInfo>): List<String> {
        val wanted = listOf(
            Regex("dialer|\\.phone|^phone$"), Regex("messag|mms|sms"),
            Regex("chrome|browser|firefox|internet"), Regex("camera"),
        )
        val out = mutableListOf<String>()
        for (re in wanted) {
            list.firstOrNull { a -> a.key !in out && (re.containsMatchIn(a.packageName.lowercase()) || re.containsMatchIn(a.label.lowercase())) }
                ?.let { out += it.key }
        }
        for (a in list) { if (out.size >= DOCK_CAPACITY) break; if (a.key !in out) out += a.key }
        return out.take(DOCK_CAPACITY)
    }

    // ---- paging ----
    val appPageCount: Int
        get() = if (grid.size <= FIRST_PAGE_CAPACITY) 1 else 1 + (grid.size - FIRST_PAGE_CAPACITY + PAGE_CAPACITY - 1) / PAGE_CAPACITY

    fun pageStart(p: Int) = if (p == 0) 0 else FIRST_PAGE_CAPACITY + (p - 1) * PAGE_CAPACITY
    fun pageCapacity(p: Int) = if (p == 0) FIRST_PAGE_CAPACITY else PAGE_CAPACITY
    fun pageItems(p: Int): List<String> {
        val s = pageStart(p); if (s >= grid.size) return emptyList()
        return grid.subList(s, minOf(grid.size, s + pageCapacity(p)))
    }

    // ---- layout editing ----
    fun move(key: String, toDock: Boolean, index: Int) {
        val g = grid.toMutableList(); val d = dock.toMutableList()
        val fromDock = key in d
        val fromIndex = if (fromDock) d.indexOf(key) else g.indexOf(key)
        if (fromIndex < 0) return
        if (fromDock) d.remove(key) else g.remove(key)
        if (toDock) {
            if (d.size >= DOCK_CAPACITY) {
                val idx = index.coerceIn(0, d.size - 1)
                val displaced = d.removeAt(idx)
                g.add(fromIndex.coerceIn(0, g.size), displaced)
                d.add(idx.coerceIn(0, d.size), key)
            } else d.add(index.coerceIn(0, d.size), key)
        } else {
            g.add(index.coerceIn(0, g.size), key)
        }
        if (g == grid && d == dock) return
        grid = g; dock = d
    }

    fun moveToEndOfPage(key: String, page: Int) {
        val inGrid = key in grid
        val sizeAfter = grid.size - if (inGrid) 1 else 0
        val target = minOf(pageStart(page) + pageCapacity(page) - 1, sizeAfter)
        if (inGrid && grid.indexOf(key) == target) return
        move(key, false, target)
    }

    fun hideFromHome(key: String) {
        grid = grid - key; dock = dock - key; hidden = hidden + key
        persist()
    }

    fun addToHome(key: String) {
        if (key in grid || key in dock) return
        hidden = hidden - key; grid = grid + key
        persist()
    }

    fun persist() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(200)
            store.save(grid, dock, hidden)
        }
    }

    fun resetLayout() {
        viewModelScope.launch { store.reset(); reload() }
    }

    // ---- launching ----
    fun launch(key: String, bounds: android.graphics.Rect?, opts: Bundle?) {
        apps[key]?.let { repo.launch(it, bounds, opts) }
    }

    fun openAppInfo(key: String, bounds: android.graphics.Rect?, opts: Bundle?) {
        apps[key]?.let { repo.openAppInfo(it, bounds, opts) }
    }

    // ---- wallpaper ----
    fun setWallpaper(uri: Uri, w: Int, h: Int) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { WallpaperUtils.importFromUri(getApplication(), uri, w, h) }
            if (bmp != null) customWallpaper = bmp
        }
    }

    fun resetWallpaper() {
        WallpaperUtils.clearCustom(getApplication())
        customWallpaper = null
    }

    // ---- system events ----
    fun onHomePressed() {
        val hadOverlay = editMode || searchOpen || settingsOpen || menu != null || removeDialogFor != null
        menu = null; searchOpen = false; settingsOpen = false; removeDialogFor = null
        if (editMode) { editMode = false; persist() }
        if (!hadOverlay) _homeEvents.tryEmit(Unit)
    }

    fun onResumed() { _resumeEvents.tryEmit(Unit) }

    fun exitEdit() { editMode = false; persist() }
}

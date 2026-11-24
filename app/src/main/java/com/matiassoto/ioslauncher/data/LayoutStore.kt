package com.matiassoto.ioslauncher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.layoutDataStore by preferencesDataStore(name = "launcher_layout")

data class SavedLayout(
    val initialized: Boolean,
    val grid: List<String>,
    val dock: List<String>,
    val hidden: Set<String>,
)

class LayoutStore(private val context: Context) {
    private val kInit = booleanPreferencesKey("initialized")
    private val kGrid = stringPreferencesKey("grid")
    private val kDock = stringPreferencesKey("dock")
    private val kHidden = stringPreferencesKey("hidden")

    private fun split(s: String?): List<String> = s?.split('|')?.filter { it.isNotBlank() } ?: emptyList()

    suspend fun load(): SavedLayout {
        val p = context.layoutDataStore.data.first()
        return SavedLayout(
            initialized = p[kInit] ?: false,
            grid = split(p[kGrid]),
            dock = split(p[kDock]),
            hidden = split(p[kHidden]).toSet(),
        )
    }

    suspend fun save(grid: List<String>, dock: List<String>, hidden: Set<String>) {
        context.layoutDataStore.edit {
            it[kInit] = true
            it[kGrid] = grid.joinToString("|")
            it[kDock] = dock.joinToString("|")
            it[kHidden] = hidden.joinToString("|")
        }
    }

    suspend fun reset() {
        context.layoutDataStore.edit { it.clear() }
    }
}

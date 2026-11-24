package com.matiassoto.ioslauncher

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.matiassoto.ioslauncher.admin.LockManager
import com.matiassoto.ioslauncher.data.SecurityStore
import com.matiassoto.ioslauncher.lost.LostModeService
import com.matiassoto.ioslauncher.ui.LauncherScreen
import com.matiassoto.ioslauncher.ui.LauncherTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { LauncherTheme { LauncherScreen(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) vm.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        vm.onResumed()
        val store = SecurityStore(this)
        if (store.lostPhoneEnabled) LostModeService.start(this)
        // Re-enter kiosk (lock task) if the owner turned it on.
        val lock = LockManager(this)
        if (store.kioskMode && lock.isDeviceOwner) {
            val am = getSystemService(ActivityManager::class.java)
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) runCatching { startLockTask() }
        }
    }
}

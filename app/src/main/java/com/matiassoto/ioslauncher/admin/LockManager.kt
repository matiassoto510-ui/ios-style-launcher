package com.matiassoto.ioslauncher.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.matiassoto.ioslauncher.MainActivity
import com.matiassoto.ioslauncher.data.SecurityStore

/** Device-owner powered "Lock mode". Every action here can be undone from settings. */
class LockManager(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, AdminReceiver::class.java)
    private val store = SecurityStore(context)

    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)
    val isAdminActive: Boolean get() = dpm.isAdminActive(admin)

    fun homeFilter() = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    /** Returns null on success or an error message. */
    fun enableLockMode(): String? {
        if (!isDeviceOwner) return "Not device owner yet. See LOCK_MODE_SETUP.md (adb dpm set-device-owner)."
        return runCatching {
            dpm.setUninstallBlocked(admin, context.packageName, true)
            dpm.addPersistentPreferredActivity(admin, homeFilter(), ComponentName(context, MainActivity::class.java))
            store.lockMode = true
            null
        }.getOrElse { it.message ?: "Failed to enable lock mode" }
    }

    fun disableLockMode(activity: Activity?): String? {
        return runCatching {
            if (isDeviceOwner) {
                setKiosk(activity, false, emptyList())
                dpm.setUninstallBlocked(admin, context.packageName, false)
                dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
            }
            store.lockMode = false
            null
        }.getOrElse { it.message ?: "Failed to disable lock mode" }
    }

    /** Lock-task ("kiosk") with an allowlist: this launcher plus the given packages. */
    fun setKiosk(activity: Activity?, on: Boolean, allowPackages: List<String>): String? {
        if (!isDeviceOwner) return "Requires device owner"
        return runCatching {
            if (on) {
                val pkgs = (listOf(context.packageName) + allowPackages).distinct().toTypedArray()
                dpm.setLockTaskPackages(admin, pkgs)
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    dpm.setLockTaskFeatures(
                        admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                            DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                            DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                            DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                            DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                    )
                }
                store.kioskMode = true
                activity?.startLockTask()
            } else {
                store.kioskMode = false
                runCatching { activity?.stopLockTask() }
                dpm.setLockTaskPackages(admin, emptyArray())
            }
            null
        }.getOrElse { it.message ?: "Failed" }
    }

    /** Fully undoes everything and gives up device owner. */
    @Suppress("DEPRECATION")
    fun removeDeviceOwner(activity: Activity?): String? {
        disableLockMode(activity)
        return runCatching {
            if (isDeviceOwner) dpm.clearDeviceOwnerApp(context.packageName)
            null
        }.getOrElse { it.message ?: "Failed to remove device owner" }
    }
}

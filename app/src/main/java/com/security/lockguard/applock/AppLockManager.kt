package com.security.lockguard.applock

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import com.security.lockguard.security.KeystoreManager
import java.util.Collections

data class InstalledAppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isLocked: Boolean
)

class AppLockManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "secure_app_vault"
        private const val KEY_LOCKED_PACKAGES = "locked_package_names"

        // Static shared state across all AppLockManager instances (Service, Activity, Receivers)
        private val temporarilyUnlocked = Collections.synchronizedSet(mutableSetOf<String>())
    }

    private val masterKey = KeystoreManager.getOrCreateMasterKey(context)

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getLockedPackages(): Set<String> {
        return prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun isAppLocked(packageName: String): Boolean {
        if (packageName == context.packageName) return false
        val lockedSet = getLockedPackages()
        return lockedSet.contains(packageName) && !temporarilyUnlocked.contains(packageName)
    }

    fun toggleAppLock(packageName: String): Boolean {
        val currentSet = getLockedPackages().toMutableSet()
        val willLock = !currentSet.contains(packageName)
        if (willLock) {
            currentSet.add(packageName)
            temporarilyUnlocked.remove(packageName)
        } else {
            currentSet.remove(packageName)
            temporarilyUnlocked.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, currentSet).apply()
        return willLock
    }

    fun setAppTemporarilyUnlocked(packageName: String) {
        temporarilyUnlocked.add(packageName)
    }

    fun lockApp(packageName: String) {
        temporarilyUnlocked.remove(packageName)
    }

    fun lockAllSessionApps() {
        temporarilyUnlocked.clear()
    }

    fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Retrieves all user-accessible launchable installed applications.
     */
    fun getInstalledLaunchableApps(): List<InstalledAppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        val lockedSet = getLockedPackages()

        val appList = mutableListOf<InstalledAppItem>()
        val seenPackages = mutableSetOf<String>()

        for (info in resolveInfoList) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || seenPackages.contains(pkg)) continue
            seenPackages.add(pkg)

            val appName = info.loadLabel(pm).toString()
            val appIcon = info.loadIcon(pm)
            val isLocked = lockedSet.contains(pkg)

            appList.add(
                InstalledAppItem(
                    name = appName,
                    packageName = pkg,
                    icon = appIcon,
                    isLocked = isLocked
                )
            )
        }

        // Sort alphabetically, with locked apps prioritized at the top
        return appList.sortedWith(
            compareByDescending<InstalledAppItem> { it.isLocked }
                .thenBy { it.name.lowercase() }
        )
    }
}

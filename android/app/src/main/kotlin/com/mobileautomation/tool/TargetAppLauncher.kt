package com.mobileautomation.tool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Discovery and launching of the target UAT application.
 *
 * Package visibility is granted by the explicit `<queries><package .../></queries>`
 * entry in AndroidManifest.xml for the single allowlisted package.
 * QUERY_ALL_PACKAGES is not requested, so this class cannot enumerate anything
 * else installed on the device.
 */
class TargetAppLauncher(private val context: Context) {

    data class TargetInfo(
        val packageName: String,
        val installed: Boolean,
        val allowlisted: Boolean,
        val mainActivity: String?,
        val versionName: String?,
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "packageName" to packageName,
            "installed" to installed,
            "allowlisted" to allowlisted,
            "mainActivity" to mainActivity,
            "versionName" to versionName,
        )
    }

    fun inspect(packageName: String): TargetInfo {
        val allowlisted = AutomationConfig.ALLOWED_PACKAGES.contains(packageName)
        if (!allowlisted) {
            // Do not probe packages outside the allowlist at all.
            return TargetInfo(packageName, installed = false, allowlisted = false, mainActivity = null, versionName = null)
        }
        val pm = context.packageManager
        val launchIntent = try {
            pm.getLaunchIntentForPackage(packageName)
        } catch (_: Throwable) {
            null
        }
        val versionName = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
        val installed = launchIntent != null || versionName != null
        return TargetInfo(
            packageName = packageName,
            installed = installed,
            allowlisted = true,
            mainActivity = launchIntent?.component?.className,
            versionName = versionName,
        )
    }

    fun isInstalled(packageName: String): Boolean = inspect(packageName).installed

    /**
     * Brings the target app to the foreground using its declared launcher
     * activity. Returns null on success or a human readable reason on failure.
     */
    fun launch(packageName: String): String? {
        if (!AutomationConfig.ALLOWED_PACKAGES.contains(packageName)) {
            return "Package $packageName is not in the automation allowlist."
        }
        val intent = try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (t: Throwable) {
            return "Could not resolve a launch intent: ${t.javaClass.simpleName}"
        } ?: return "Target application is not installed"

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        return try {
            context.startActivity(intent)
            null
        } catch (t: Throwable) {
            "Could not start the target application: ${t.javaClass.simpleName}"
        }
    }
}

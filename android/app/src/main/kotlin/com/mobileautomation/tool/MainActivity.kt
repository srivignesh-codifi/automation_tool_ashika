package com.mobileautomation.tool

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream

/**
 * Bridge between the Flutter UI and the native automation driver.
 *
 * Nothing here logs a credential. `startLoginAutomation` reads the credential
 * values (client / user id, OTP, and the MPIN or password the target app asks
 * for) out of the method call, hands them to the engine, and keeps no
 * reference of its own.
 */
class MainActivity : FlutterActivity() {

    private companion object {
        const val COMMAND_CHANNEL = "com.mobileautomation.tool/commands"
        const val EVENT_CHANNEL = "com.mobileautomation.tool/events"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            })

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, COMMAND_CHANNEL)
            .setMethodCallHandler { call, resultHandler -> handle(call, resultHandler) }
    }

    /** Events originate on the engine's worker thread; channels need the main thread. */
    private fun sendEvent(payload: Map<String, Any?>) {
        mainHandler.post { eventSink?.success(payload) }
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "isAccessibilityEnabled" -> result.success(
                mapOf(
                    "enabledInSettings" to AutomationAccessibilityService.isEnabledInSettings(this),
                    "connected" to (AutomationAccessibilityService.connected() != null),
                    "ready" to AutomationAccessibilityService.isReady(this),
                ),
            )

            "openAccessibilitySettings" -> result.success(openAccessibilitySettings())

            "openAppInfoSettings" -> result.success(openAppInfoSettings())

            "isTargetAppInstalled" -> {
                val packageName = call.argument<String>("packageName")
                    ?: AutomationConfig.DEFAULT_TARGET_PACKAGE
                result.success(TargetAppLauncher(this).inspect(packageName).toMap())
            }

            "startLoginAutomation" -> {
                val request = AutomationEngine.StartRequest(
                    targetPackage = call.argument<String>("targetPackage")
                        ?: AutomationConfig.DEFAULT_TARGET_PACKAGE,
                    clientId = call.argument<String>("clientId").orEmpty(),
                    otp = call.argument<String>("otp").orEmpty(),
                    mpin = call.argument<String>("mpin").orEmpty(),
                    password = call.argument<String>("password").orEmpty(),
                )
                val error = AutomationEngine.start(applicationContext, request) { sendEvent(it) }
                result.success(mapOf("started" to (error == null), "error" to error))
            }

            "stopAutomation" -> result.success(AutomationEngine.stop())

            "isAutomationRunning" -> result.success(AutomationEngine.isRunning())

            "getLastReport" -> result.success(AutomationEngine.lastResult)

            "downloadReportFiles" -> downloadReportFiles(call, result)

            "getDeviceInfo" -> result.success(
                mapOf(
                    "model" to (Build.MODEL ?: "unknown"),
                    "manufacturer" to (Build.MANUFACTURER ?: "unknown"),
                    "androidVersion" to (Build.VERSION.RELEASE ?: "unknown"),
                    "sdkInt" to Build.VERSION.SDK_INT,
                ),
            )

            "getAutomationConfig" -> result.success(
                mapOf(
                    "allowedPackages" to AutomationConfig.ALLOWED_PACKAGES.toList(),
                    "defaultTargetPackage" to AutomationConfig.DEFAULT_TARGET_PACKAGE,
                    "targets" to TargetApp.values().map {
                        mapOf(
                            "id" to it.id,
                            "displayName" to it.displayName,
                            "packageName" to it.packageName,
                            "testName" to it.testName,
                            "secret" to it.secret.name.lowercase(),
                        )
                    },
                    "testName" to AutomationConfig.TEST_NAME,
                    "requiresRestrictedSettingsGuidance" to
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU),
                ),
            )

            else -> result.notImplemented()
        }
    }

    /**
     * Copies the already-generated report files into public Downloads through
     * MediaStore. The app targets Android 10+, so this needs no broad storage
     * permission and never exposes an arbitrary filesystem destination.
     */
    private fun downloadReportFiles(call: MethodCall, result: MethodChannel.Result) {
        val requested = call.argument<List<*>>("files").orEmpty()
        if (requested.isEmpty()) {
            result.error("no_report_files", "No report files were supplied.", null)
            return
        }

        Thread {
            val resolver = contentResolver
            val saved = mutableListOf<String>()
            val failed = mutableListOf<String>()
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Automation Tool Reports"

            for (entry in requested) {
                val item = entry as? Map<*, *>
                val sourcePath = item?.get("path") as? String
                val requestedName = item?.get("name") as? String
                val mimeType = (item?.get("mimeType") as? String).orEmpty()
                    .ifEmpty { "application/octet-stream" }
                val source = sourcePath?.let(::File)
                val displayName = requestedName?.let(::File)?.name
                    ?: source?.name
                    ?: "report"

                if (source == null || !source.isFile) {
                    failed += displayName
                    continue
                }

                var destination: android.net.Uri? = null
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("MediaStore refused the destination")
                    resolver.openOutputStream(destination, "w").use { output ->
                        requireNotNull(output) { "The destination could not be opened" }
                        FileInputStream(source).use { input -> input.copyTo(output) }
                    }
                    val published = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    if (resolver.update(destination, published, null, null) <= 0) {
                        throw IllegalStateException("The downloaded file could not be published")
                    }
                    saved += displayName
                } catch (_: Throwable) {
                    destination?.let { uri ->
                        try {
                            resolver.delete(uri, null, null)
                        } catch (_: Throwable) {
                            // Best effort cleanup of an incomplete MediaStore row.
                        }
                    }
                    failed += displayName
                }
            }

            mainHandler.post {
                result.success(
                    mapOf(
                        "savedNames" to saved,
                        "failedNames" to failed,
                        "directory" to "Downloads/Automation Tool Reports",
                    ),
                )
            }
        }.start()
    }

    private fun openAccessibilitySettings(): Boolean = try {
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * Opens this app's own App info page. On Android 13+ the accessibility
     * toggle for a sideloaded app is greyed out until "Allow restricted
     * settings" is enabled from the overflow menu there. The tool only takes the
     * user to that screen — it never attempts to work around the restriction.
     */
    private fun openAppInfoSettings(): Boolean = try {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Throwable) {
        false
    }
}

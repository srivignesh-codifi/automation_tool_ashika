package com.mobileautomation.tool

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Bridge between the Flutter UI and the native automation driver.
 *
 * Nothing here logs a credential. `startLoginAutomation` reads the three values
 * out of the method call, hands them to the engine, and keeps no reference of
 * its own.
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
                )
                val error = AutomationEngine.start(applicationContext, request) { sendEvent(it) }
                result.success(mapOf("started" to (error == null), "error" to error))
            }

            "stopAutomation" -> result.success(AutomationEngine.stop())

            "isAutomationRunning" -> result.success(AutomationEngine.isRunning())

            "getLastReport" -> result.success(AutomationEngine.lastResult)

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
                    "testName" to AutomationConfig.TEST_NAME,
                    "requiresRestrictedSettingsGuidance" to
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU),
                ),
            )

            else -> result.notImplemented()
        }
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

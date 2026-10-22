package com.mobileautomation.tool

/**
 * The applications this tool is permitted to drive.
 *
 * This is the allowlist: a package that is not listed here is refused before it
 * is inspected or launched (see [AutomationConfig.ALLOWED_PACKAGES]). Each entry
 * also selects the test flow that runs against it.
 *
 * The AccessibilityService is additionally scoped to these packages via
 * `android:packageNames` in res/xml/accessibility_service_config.xml, and the
 * manifest's `<queries>` block names them, so the three lists must be kept in
 * step.
 */
enum class TargetApp(
    /** Stable id used on the platform channel and in the report. */
    val id: String,
    val displayName: String,
    val packageName: String,
    val testName: String,
    /** What the tester types after the one-time code. */
    val secret: Secret,
) {
    ASHIKA(
        id = "ashika",
        displayName = "Ashika",
        packageName = "com.codifi.dhanush",
        testName = "Phase 1 - Login Automation",
        secret = Secret.MPIN,
    ),
    TRADION(
        id = "tradion",
        displayName = "Tradion",
        packageName = "com.codifi.rmoney",
        testName = "Tradion - Regression Automation",
        secret = Secret.PASSWORD,
    );

    /** The credential the app asks for besides the user id and the OTP. */
    enum class Secret { MPIN, PASSWORD }

    companion object {
        fun forPackage(packageName: String): TargetApp? =
            values().firstOrNull { it.packageName == packageName }
    }
}

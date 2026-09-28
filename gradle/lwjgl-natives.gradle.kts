// The OS this build is running on, as LWJGL's native artifacts name it.
//
// Copied from Larsons-Game-Engine (gradle/lwjgl-natives.gradle.kts) so the two
// projects resolve the same LWJGL version with the same classifier rules.

extra["lwjglNatives"] = when {
    org.gradle.internal.os.OperatingSystem.current().isMacOsX ->
        if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    org.gradle.internal.os.OperatingSystem.current().isWindows -> "natives-windows"
    else -> "natives-linux"
}

// The LWJGL version the game resolves against (same as the engine's).
extra["lwjglVersion"] = "3.3.3"

import java.io.File

plugins {
    id("java")
    id("application")
}

group = "com.larsons"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// The OS this build is running on, as LWJGL's native artifacts name it —
// copied from Larsons-Game-Engine so both projects resolve the same natives.
apply(from = "gradle/lwjgl-natives.gradle.kts")
val lwjglNatives: String by extra
val lwjglVersion: String by extra

dependencies {
    // The whole game draws through OpenGL 3.3 core, so unlike the engine's core
    // (which keeps LWJGL test-only and ships a Java2D fallback), LWJGL is a real
    // runtime dependency here. That is what "GPU acceleration for the entire
    // game" means in practice: the 3D void, every sprite layer and every menu
    // are GPU draw calls. AWT is only used off-screen, to decode PNGs and to
    // rasterise the font atlas and the fallback sprites.
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-glfw")
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
val isLinux = System.getProperty("os.name", "").lowercase().contains("linux")

application {
    mainClass = "com.larsons.game.Main"

    // Headless AWT is deliberate: GLFW owns the only window, and on macOS AWT
    // and GLFW cannot both have the first thread. Everything AWT does in this
    // game (ImageIO, Graphics2D into BufferedImages, font rasterisation) works
    // headless. A heap proportional to the machine, as in the engine, because
    // 512x512 sprite sheets are what memory is spent on.
    applicationDefaultJvmArgs = buildList {
        add("-XX:MaxRAMPercentage=60")
        add("-Djava.awt.headless=true")
        // GLFW must run on the process's first thread on macOS.
        if (isMac) add("-XstartOnFirstThread")
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

// Every launched JVM sees the `-Dlarsons.*` flags on the Gradle command line.
// Gradle forks the game and the fork inherits none of them, so without this
//   ./gradlew run -Dlarsons.sprites.scale=0.5
// would look like the flag did nothing. Same rule as the engine's build.
tasks.withType<JavaExec>().configureEach {
    System.getProperties().forEach { key, value ->
        val name = key.toString()
        if (name.startsWith("larsons.")) systemProperty(name, value.toString())
    }
    // Relative paths (assets/, config/, screenshots/) mean the repository root,
    // which is what makes a drag-and-dropped sprite sheet land in the repo.
    workingDir = projectDir
}

/**
 * Environment that asks a hybrid-graphics machine for its discrete GPU.
 *
 * Linux (Mesa): DRI_PRIME=1 selects the secondary (usually discrete) GPU and
 * is ignored on a single-GPU machine. Linux (NVIDIA PRIME render offload):
 * both NVIDIA variables are needed, and the vendor one would break GL on a
 * machine without the NVIDIA driver — so it is only set when that driver is
 * actually loaded. Windows and macOS cannot be steered from the environment:
 * Windows picks per executable (Settings > System > Display > Graphics >
 * java.exe > High performance) and macOS switches to the discrete GPU by
 * itself for an OpenGL context.
 */
fun discreteGpuEnvironment(): Map<String, String> {
    if (!isLinux) return emptyMap()
    val env = linkedMapOf("DRI_PRIME" to "1")
    if (File("/proc/driver/nvidia/version").exists()) {
        env["__NV_PRIME_RENDER_OFFLOAD"] = "1"
        env["__GLX_VENDOR_LIBRARY_NAME"] = "nvidia"
    }
    return env
}

// The launch the IntelliJ profile "Run Game (GPU)" uses:
//   ./gradlew runGpu
//
// Same game, same classpath as `run`, plus: the discrete GPU requested where
// the OS lets us ask, vsync and 4x MSAA on, and `larsons.gpu=required`, which
// makes the game say so loudly (console and HUD) if the driver it got is a
// software rasteriser rather than real GPU acceleration.
tasks.register<JavaExec>("runGpu") {
    group = "application"
    description = "Run the game with GPU acceleration on (discrete GPU preferred, vsync, MSAA)"
    mainClass = "com.larsons.game.Main"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs(application.applicationDefaultJvmArgs)

    systemProperty("larsons.gpu", System.getProperty("larsons.gpu") ?: "required")
    systemProperty("larsons.gpu.msaa", System.getProperty("larsons.gpu.msaa") ?: "4")
    systemProperty("larsons.gpu.vsync", System.getProperty("larsons.gpu.vsync") ?: "on")
    environment(discreteGpuEnvironment())

    doFirst {
        val env = discreteGpuEnvironment()
        println(
            """
            |
            |  Larson's Game — GPU launch
            |    renderer : OpenGL 3.3 core (LWJGL $lwjglVersion, $lwjglNatives)
            |    gpu hint : ${if (env.isEmpty()) "left to the OS" else env.entries.joinToString(" ")}
            |    assets   : $projectDir/assets/sprites  (drop sprite sheets on the window to add more)
            |
            """.trimMargin()
        )
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.larsons.game.Main"
    }
}

// A runnable jar with LWJGL and this OS's natives inside, for sharing:
//   ./gradlew gameJar && java -jar build/libs/larsons-game.jar
// Put the `assets/` folder next to the jar (the game looks for it in the
// working directory, then beside the jar).
tasks.register<Jar>("gameJar") {
    group = "distribution"
    description = "Runnable jar with the game, LWJGL and the natives for this OS"
    archiveBaseName = "larsons-game"
    archiveVersion = ""
    manifest {
        attributes["Main-Class"] = "com.larsons.game.Main"
    }
    from(sourceSets["main"].output)
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// A stand-in "Blender" sprite set rendered from the fallback puppet — body, a
// cap and the sword on separate held-out layers — for trying the real-sheet
// path and the drag-and-drop importer before the real renders exist.
//   ./gradlew sampleSprites            → build/sample-sprites/ (256 px frames)
//   ./gradlew sampleSprites -Psize=512
tasks.register<JavaExec>("sampleSprites") {
    group = "application"
    description = "Render a sample layered sprite set into build/sample-sprites"
    mainClass = "com.larsons.game.tools.SampleSprites"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Djava.awt.headless=true")
    args(layout.buildDirectory.dir("sample-sprites").get().asFile.path,
            (findProperty("size") as String?) ?: "256")
}

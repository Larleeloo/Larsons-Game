import java.io.File
import org.apache.tools.ant.filters.FixCrLfFilter

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
    // The system's own "open file" dialog, for choosing a sound file (Pause > Sounds > Choose...).
    implementation("org.lwjgl:lwjgl-tinyfd")
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::$lwjglNatives")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
val isLinux = System.getProperty("os.name", "").lowercase().contains("linux")
val isWindows = System.getProperty("os.name", "").lowercase().contains("windows")
// ZGC runs on 64-bit x86 everywhere and on ARM64 Linux and macOS.
val zgcSupported = System.getProperty("os.arch", "").lowercase().let { arch ->
    arch == "amd64" || arch == "x86_64" || (arch == "aarch64" && !isWindows)
}

application {
    mainClass = "com.larsons.game.Main"

    // Headless AWT is deliberate: GLFW owns the only window, and on macOS AWT
    // and GLFW cannot both have the first thread. Everything AWT does in this
    // game (ImageIO, Graphics2D into BufferedImages, font rasterisation) works
    // headless. A heap proportional to the machine, as in the engine, because
    // 512x512 sprite sheets are what memory is spent on.
    applicationDefaultJvmArgs = buildList {
        add("-XX:MaxRAMPercentage=60")
        // A collector that never stops the game for more than a fraction of a
        // millisecond. The default (G1) paused the render thread for up to
        // ~50 ms while sprite sheets decoded - six frames at 120 fps.
        // Generational ZGC is a flag on JDK 21-22 and the default after.
        if (zgcSupported) {
            add("-XX:+UseZGC")
            if (JavaVersion.current() < JavaVersion.VERSION_23) add("-XX:+ZGenerational")
        }
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
// the OS lets us ask, 4x MSAA on, and `larsons.gpu=required`, which
// makes the game say so loudly (console and HUD) if the driver it got is a
// software rasteriser rather than real GPU acceleration.
tasks.register<JavaExec>("runGpu") {
    group = "application"
    description = "Run the game with GPU acceleration on (discrete GPU preferred, MSAA)"
    mainClass = "com.larsons.game.Main"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs(application.applicationDefaultJvmArgs)

    systemProperty("larsons.gpu", System.getProperty("larsons.gpu") ?: "required")
    systemProperty("larsons.gpu.msaa", System.getProperty("larsons.gpu.msaa") ?: "4")
    // vsync is the player's choice (Pause > Settings, saved); -Dlarsons.gpu.vsync=off sets it for one run
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

// Every OS's LWJGL natives, for the jar that is shared: the build's own
// runtime classpath only has this machine's. LWJGL keeps each OS's and
// architecture's libraries under their own folder in its natives jars
// (windows/x64/..., macos/arm64/...), so all of them sit side by side in one
// jar and LWJGL picks the right one where the game starts.
val shareNatives: Configuration by configurations.creating
dependencies {
    for (os in listOf("windows", "windows-arm64", "macos", "macos-arm64", "linux", "linux-arm64")) {
        for (module in listOf("lwjgl", "lwjgl-opengl", "lwjgl-glfw", "lwjgl-tinyfd")) {
            shareNatives("org.lwjgl:$module:$lwjglVersion:natives-$os")
        }
    }
}

// A runnable jar with the game, LWJGL and its natives for Windows, macOS and
// Linux (x64 and ARM64) inside:
//   ./gradlew gameJar && java -jar build/libs/larsons-game.jar
// The game looks for `assets/` in the working directory, then beside the jar.
// From a jar it relaunches itself once with the build's JVM flags, in the
// jar's folder (core/Launcher), so a double-click runs it as runGpu does.
val gameJar = tasks.register<Jar>("gameJar") {
    group = "distribution"
    description = "Runnable jar with the game, LWJGL and its natives for Windows, macOS and Linux"
    archiveBaseName = "larsons-game"
    archiveVersion = ""
    manifest {
        attributes["Main-Class"] = "com.larsons.game.Main"
        // LWJGL's core jar has Java 9+ versions of some classes
        attributes["Multi-Release"] = "true"
        attributes["Implementation-Title"] = "Larson's Game"
        attributes["Implementation-Version"] = project.version
    }
    from(sourceSets["main"].output)
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { if (it.isDirectory) it else zipTree(it) }
    })
    from(shareNatives.elements.map { natives -> natives.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class",
            "META-INF/versions/*/module-info.class", "META-INF/INDEX.LIST")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// --- the copy of the game to share ---------------------------------------------------
//
//   ./gradlew shareZip     build/distributions/Larsons-Game.zip
//
// One folder, Larsons-Game/, with the jar, a launcher to double-click for
// Windows, macOS and Linux, HOW TO PLAY.txt (all three from dist/), and the
// assets: every folder as it is, except that the sprites folders (some two
// million small PNGs, which nobody wants to unzip) go into one file,
// assets/sprites.pack, that the game reads in place (core/SpritePack). The
// pack travels in parts of 1 GB (sprites.pack.001, .002 ... and the list of
// them, sprites.pack.parts) - Windows' own unzip is not to be trusted with
// one file of several gigabytes - and the game joins them the first time it
// starts. The zip is stored, not compressed: the pack is nearly all of it
// and PNGs are compressed already.

// The pack, in a folder of its own that no other task writes to or cleans.
// Not up-to-date checked: snapshotting two million files for Gradle would
// cost about as much as packing them again.
val spritePackDir = layout.buildDirectory.dir("sprite-pack")
val spritePack = tasks.register<JavaExec>("spritePack") {
    group = "distribution"
    description = "Pack the sprites folders into build/sprite-pack/sprites.pack.001, .002 ... (1 GB parts)"
    mainClass = "com.larsons.game.tools.PackSprites"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Djava.awt.headless=true")
    args(file("assets").path, spritePackDir.get().file("sprites.pack").asFile.path, "1000")
    outputs.dir(spritePackDir)
    doNotTrackState("two million files; Gradle would hash every one to check")
}

tasks.register<Zip>("shareZip") {
    group = "distribution"
    description = "build/distributions/Larsons-Game.zip: the game to share (unzip, double-click Play)"
    archiveFileName = "Larsons-Game.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    into("Larsons-Game") {
        from(gameJar)
        // the launchers and the instructions with the line endings their
        // systems need, whatever git checked out; the scripts executable on
        // macOS and Linux, even when the zip is made on Windows
        from("dist") {
            filesMatching(listOf("*.bat", "*.cmd", "*.txt")) {
                filter<FixCrLfFilter>("eol" to FixCrLfFilter.CrLf.newInstance("crlf"),
                        "eof" to FixCrLfFilter.AddAsisRemove.newInstance("remove"))
            }
            filesMatching(listOf("*.sh", "*.command")) {
                filter<FixCrLfFilter>("eol" to FixCrLfFilter.CrLf.newInstance("lf"),
                        "eof" to FixCrLfFilter.AddAsisRemove.newInstance("remove"))
                permissions { unix("rwxr-xr-x") }
            }
        }
        from("assets") {
            into("assets")
            exclude("sprites", "sprites/**", "sprites_*", "sprites_*/**", "animations_v1", "animations_v1/**",
                    "**/.gitkeep", "**/.DS_Store", "**/Thumbs.db")
        }
        from(spritePack) { into("assets") }
    }
    isZip64 = true
    entryCompression = ZipEntryCompression.STORED
    isPreserveFileTimestamps = true
    // never a zip without its sprites: every part the list names, as long as it says
    val packDir = spritePackDir
    doFirst {
        val dir = packDir.get().asFile
        val list = dir.resolve("sprites.pack.parts")
        val again = "run shareZip again (without -x spritePack) to pack the sprites"
        if (!list.isFile) throw GradleException("There is no sprite pack in $dir: $again")
        val parts = list.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (parts.isEmpty()) throw GradleException("$list lists no parts: $again")
        parts.forEach { line ->
            val name = line.substringBeforeLast(' ').trim()
            val bytes = line.substringAfterLast(' ').toLong()
            val part = dir.resolve(name)
            if (!part.isFile || part.length() != bytes) {
                throw GradleException("The sprite pack in $dir is not complete ($name): $again")
            }
        }
        println("[share] the sprite pack: ${parts.size} parts, %.1f GB".format(
                parts.sumOf { it.substringAfterLast(' ').toLong() } / 1e9))
    }
    // where it is, for whoever ran it from IntelliJ's run dropdown
    val zip = archiveFile
    doLast {
        val file = zip.get().asFile
        println(
            """
            |
            |  The game to share is ready (%.1f GB):
            |    %s
            |  Upload it to Google Drive, OneDrive or Dropbox and send your friends the link.
            |  HOW TO PLAY.txt inside the zip tells them how to install Java and start it.
            |
            """.trimMargin().format(file.length() / 1e9, file.absolutePath)
        )
    }
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

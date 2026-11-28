package com.larsons.game.core;

import com.larsons.game.gfx.GpuInfo;
import com.larsons.game.util.Json;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * What the game is running on, and whether it is a low-end machine that
 * should get the Lite graphics ({@link GraphicsProfile}): the system, the
 * processor, the memory, and - once the window is open - the graphics chip
 * the driver names ({@code GL_RENDERER}).
 *
 * <p><b>Low-end</b> ({@link #verdict}) is any of: graphics drawn in software
 * (no GPU driver); a graphics chip built into the processor - Intel's HD, UHD,
 * Iris and Arc-without-a-number, AMD's Radeon Graphics and Vega and 680M
 * kind, the plain Apple M1/M2/M3/M4 (the Pro, Max and Ultra are not), a
 * phone's Mali or Adreno - or an entry-level card (GeForce MX, GT 1030);
 * 8 GB of memory or less (what the game and the graphics share on a MacBook
 * Air); four processor threads or fewer; an Intel Java translated by Rosetta
 * on an Apple Silicon Mac. Everything else gets the full graphics.
 *
 * <p>Before the window is open the graphics chip is the one this machine
 * had last time ({@code config/hardware.json}), and on a Mac the processor's
 * name says it (an Apple M1's graphics are the M1's) - which is what
 * decides, before the window is made, whether it is made multisampled.
 *
 * <p>Plain data and string rules: the rules are tested without a machine.
 */
public record Hardware(String os, String arch, int threads, long memory, String cpu, boolean rosetta,
                       String gpuVendor, String gpu, boolean software) {

    /** Bytes of memory at or under which a machine is low-end: 8 GB, with room for what the system keeps back. */
    static final long LOW_MEMORY = 9L << 30;
    /** Processor threads at or under which a machine is low-end. */
    static final int FEW_THREADS = 4;

    /** Which graphics suit the machine, and why. */
    public record Verdict(GraphicsProfile profile, List<String> reasons) {

        public boolean lite() {
            return profile == GraphicsProfile.LITE;
        }

        /** "Lite: Apple M1 graphics, built into the processor; 8 GB of memory" - or "Full". */
        public String describe() {
            return profile.label + (reasons.isEmpty() ? "" : ": " + String.join("; ", reasons));
        }
    }

    public Hardware {
        os = os == null ? "" : os;
        arch = arch == null ? "" : arch;
        cpu = cpu == null ? "" : cpu.trim();
        gpuVendor = gpuVendor == null ? "" : gpuVendor;
        gpu = gpu == null ? "" : gpu;
    }

    // --- what makes it low-end ------------------------------------------------------------

    /** Whether this machine is low-end, and why ({@link Hardware the rules}). */
    public Verdict verdict() {
        List<String> why = new ArrayList<>();
        if (software || GpuInfo.isSoftware(gpu) || GpuInfo.isSoftware(gpuVendor)) {
            why.add("the graphics are drawn in software, with no graphics driver");
        } else if (!gpu.isBlank() && lowPowerGpu(gpu, gpuVendor)) {
            why.add(entryCard(gpu) ? shortGpu() + ", an entry-level graphics card"
                    : gpuName() + ", built into the processor");
        } else if (gpu.isBlank() && appleBase(cpu)) {
            why.add(cpu + " graphics, built into the processor");
        }
        if (rosetta) why.add("Java is the Intel version, translated by Rosetta");
        if (memory > 0 && memory <= LOW_MEMORY) why.add(gigabytes(memory) + " of memory");
        if (threads > 0 && threads <= FEW_THREADS) why.add(threads + " processor threads");
        return new Verdict(why.isEmpty() ? GraphicsProfile.FULL : GraphicsProfile.LITE, List.copyOf(why));
    }

    private static final Pattern APPLE_BASE = Pattern.compile("apple m\\d+( \\(.*\\))?");
    private static final Pattern AMD_MOBILE = Pattern.compile(".*radeon(\\(tm\\))? (\\d{3}m|vega)\\b.*");
    private static final Pattern AMD_APU = Pattern.compile(
            ".*\\b(renoir|cezanne|lucienne|rembrandt|phoenix|raven|raven2|picasso|vangogh|mendocino|barcelo"
                    + "|strix|krackan|carrizo|stoney|kabini|mullins|beema|dali|pollock|custom gpu)\\b.*");
    private static final Pattern INTEL_DISCRETE = Pattern.compile(".*arc(\\(tm\\))? [ab]\\d{3}.*");
    private static final Pattern NVIDIA_ENTRY = Pattern.compile(
            ".*\\b(geforce mx ?\\d{2,3}|mx ?\\d{3}|gt ?\\d{3,4}|geforce 9\\d{2}m|quadro (k|p)\\d{3,4}m?)\\b.*");

    /** Whether the graphics chip {@code renderer} (from {@code vendor}) is built in, or a phone's, or entry-level. */
    static boolean lowPowerGpu(String renderer, String vendor) {
        String r = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT).trim();
        String v = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);
        if (r.isEmpty()) return false;
        if (r.startsWith("apple")) {
            // the plain M-series chips (M1 ... M4) are a MacBook Air's; the Pro, Max and Ultra are not
            return APPLE_BASE.matcher(r).matches() || !r.matches("apple m\\d+ (pro|max|ultra).*");
        }
        if (r.contains("intel") || v.contains("intel")) {
            // Arc A770, B580: cards of their own; Iris, UHD, HD and plain "Arc Graphics": in the processor
            return !INTEL_DISCRETE.matcher(r).matches();
        }
        // (the vendor "ATI Technologies Inc." - not the "ati" in "NVIDIA Corporation")
        if (r.contains("radeon") || r.contains("amd") || v.contains("amd") || v.startsWith("ati")) {
            if (r.contains("radeon rx") || r.contains("radeon pro") || r.contains("radeon r9")
                    || r.contains("radeon vii") || r.contains("firepro")) {
                return false;
            }
            return r.contains("radeon(tm) graphics") || r.contains("radeon graphics")
                    || AMD_MOBILE.matcher(r).matches() || AMD_APU.matcher(r).matches();
        }
        if (r.contains("mali") || r.contains("adreno") || r.contains("powervr") || r.contains("videocore")
                || r.contains("v3d") || r.contains("tegra") || r.contains("vivante")) {
            return true;
        }
        return entryCard(r);
    }

    /** Whether {@code renderer} is an entry-level card of its own (GeForce MX, GT 1030). */
    static boolean entryCard(String renderer) {
        String r = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT);
        return r.contains("geforce") || r.contains("nvidia") || r.contains("quadro")
                ? NVIDIA_ENTRY.matcher(r).matches() : false;
    }

    /** Whether {@code cpu} is a plain Apple M-series chip (the graphics in it a MacBook Air's). */
    static boolean appleBase(String cpu) {
        return cpu != null && APPLE_BASE.matcher(cpu.toLowerCase(Locale.ROOT).trim()).matches();
    }

    /** The graphics chip's name without the driver's details ("Apple M1", "Intel(R) UHD Graphics 620"). */
    String shortGpu() {
        String g = gpu.split(" \\(")[0].split("/")[0].trim();
        return g.isEmpty() ? gpu : g;
    }

    /** "Apple M1 graphics", "Intel(R) UHD Graphics 620": the chip, said as graphics. */
    String gpuName() {
        String g = shortGpu();
        return g.toLowerCase(Locale.ROOT).contains("graphics") ? g : g + " graphics";
    }

    static String gigabytes(long bytes) {
        return Math.round(bytes / (double) (1L << 30)) + " GB";
    }

    /** One line: "Apple M1, Apple M1 graphics, 8 GB, 8 threads, Mac OS X aarch64". */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (!cpu.isBlank()) sb.append(cpu).append(", ");
        if (!gpu.isBlank()) sb.append(gpuName()).append(", ");
        if (memory > 0) sb.append(gigabytes(memory)).append(", ");
        if (threads > 0) sb.append(threads).append(" threads, ");
        sb.append(os).append(' ').append(arch);
        if (rosetta) sb.append(" (Rosetta)");
        return sb.toString();
    }

    /** The same machine, its graphics chip as the driver names it. */
    public Hardware withGpu(GpuInfo info) {
        if (info == null) return this;
        return new Hardware(os, arch, threads, memory, cpu, rosetta, info.vendor(), info.renderer(), info.software());
    }

    // --- finding out ------------------------------------------------------------------------

    /**
     * This machine, before its window is open: the system, the processor and
     * the memory, and the graphics chip it had last time ({@code
     * hardware.json} in {@code config}, if the processor is the same).
     */
    public static Hardware detect(Path config) {
        String os = System.getProperty("os.name", "");
        String arch = System.getProperty("os.arch", "");
        int threads = Runtime.getRuntime().availableProcessors();
        long memory = totalMemory();
        String o = os.toLowerCase(Locale.ROOT);
        String cpu = "";
        boolean rosetta = false;
        try {
            if (o.contains("mac")) {
                cpu = run("sysctl", "-n", "machdep.cpu.brand_string");
                rosetta = "1".equals(run("sysctl", "-in", "sysctl.proc_translated"));
            } else if (o.contains("linux")) {
                cpu = linuxCpu();
            } else if (o.contains("windows")) {
                cpu = windowsCpu();
            }
        } catch (RuntimeException e) {
            cpu = "";
        }
        String vendor = "", gpu = "";
        Map<String, Object> last = remembered(config);
        if (last != null && cpu.equals(Json.str(last, "cpu", ""))) {
            vendor = Json.str(last, "vendor", "");
            gpu = Json.str(last, "renderer", "");
        }
        return new Hardware(os, arch, threads, memory, cpu, rosetta, vendor, gpu, false);
    }

    /** Keep the graphics chip for the next start's first guess ({@code hardware.json} in {@code config}). */
    public void remember(Path config) {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("about", "The machine the game last ran on, so the next start knows its graphics before the window "
                + "opens (Lite graphics on a low-end machine). Safe to delete.");
        j.put("cpu", cpu);
        j.put("vendor", gpuVendor);
        j.put("renderer", gpu);
        j.put("memoryGB", Math.round(memory / (double) (1L << 30)));
        j.put("threads", threads);
        j.put("verdict", verdict().describe());
        try {
            Files.createDirectories(config);
            Files.writeString(config.resolve("hardware.json"), Json.stringify(j), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            System.err.println("[hardware] cannot write " + config.resolve("hardware.json") + ": " + e.getMessage());
        }
    }

    private static Map<String, Object> remembered(Path config) {
        Path f = config.resolve("hardware.json");
        if (!Files.isRegularFile(f)) return null;
        try {
            return Json.asObject(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The machine's memory, bytes (0: cannot tell). */
    static long totalMemory() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean b) {
                return b.getTotalMemorySize();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // not this JVM's to say
        }
        return 0;
    }

    private static String linuxCpu() {
        try {
            return cpuName(Files.readAllLines(Path.of("/proc/cpuinfo"), StandardCharsets.ISO_8859_1));
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }

    /**
     * The processor's name in {@code /proc/cpuinfo}: its "model name" (a PC's),
     * else its "Hardware" or "Model" (a Raspberry Pi's or a phone's) - never the
     * bare model number an x86 also lists as "model".
     */
    static String cpuName(List<String> cpuinfo) {
        String hardware = "", model = "";
        for (String line : cpuinfo) {
            int c = line.indexOf(':');
            if (c <= 0) continue;
            String key = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(c + 1).trim();
            if (value.isEmpty()) continue;
            if (key.equals("model name")) return value;
            if (key.equals("hardware") && hardware.isEmpty()) hardware = value;
            if (key.equals("model") && model.isEmpty() && !value.matches("\\d+")) model = value;
        }
        return !hardware.isEmpty() ? hardware : model;
    }

    private static String windowsCpu() {
        String out = run("reg", "query", "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0",
                "/v", "ProcessorNameString");
        int at = out.indexOf("REG_SZ");
        if (at >= 0) return out.substring(at + 6).trim();
        String id = System.getenv("PROCESSOR_IDENTIFIER");
        return id == null ? "" : id.trim();
    }

    /** What a small system command prints (its first line, trimmed), or "" - never waiting more than two seconds. */
    static String run(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            p.getOutputStream().close();
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.exitValue() != 0) return "";
            int nl = text.indexOf('\n');
            return (command[0].equals("reg") || nl < 0 ? text : text.substring(0, nl)).trim();
        } catch (IOException | RuntimeException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}

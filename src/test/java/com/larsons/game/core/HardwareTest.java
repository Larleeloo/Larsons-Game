package com.larsons.game.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which machines get the Lite graphics: the graphics chips the drivers name,
 * the memory, the processor threads, Rosetta - each by the strings a real
 * machine of that kind reports. No GPU.
 */
class HardwareTest {

    private static final long GB = 1L << 30;

    private static Hardware mac(String chip, long memory, int threads) {
        return new Hardware("Mac OS X", "aarch64", threads, memory, chip, false, "Apple", chip, false);
    }

    private static Hardware pc(String gpu, String vendor, long memory, int threads) {
        return new Hardware("Windows 11", "amd64", threads, memory, "AMD Ryzen 7 5800X 8-Core Processor", false,
                vendor, gpu, false);
    }

    @Test
    void aMacBookAirWithAnM1IsLiteForItsGraphicsAndItsMemory() {
        Hardware.Verdict v = mac("Apple M1", 8 * GB, 8).verdict();
        assertTrue(v.lite());
        assertEquals(List.of("Apple M1 graphics, built into the processor", "8 GB of memory"), v.reasons());
        assertEquals("Lite: Apple M1 graphics, built into the processor; 8 GB of memory", v.describe());
    }

    @Test
    void anM1WithMoreMemoryIsStillLiteForItsGraphics() {
        assertTrue(mac("Apple M1", 16 * GB, 8).verdict().lite());
        assertTrue(mac("Apple M2", 24 * GB, 8).verdict().lite());
        assertTrue(mac("Apple M3", 16 * GB, 8).verdict().lite());
    }

    @Test
    void theProMaxAndUltraChipsGetTheFullGraphics() {
        assertFalse(mac("Apple M1 Pro", 16 * GB, 10).verdict().lite());
        assertFalse(mac("Apple M2 Max", 32 * GB, 12).verdict().lite());
        assertFalse(mac("Apple M1 Ultra", 64 * GB, 20).verdict().lite());
    }

    @Test
    void beforeTheWindowIsOpenAMacSaysItsGraphicsByItsProcessorsName() {
        Hardware air = new Hardware("Mac OS X", "aarch64", 8, 16 * GB, "Apple M1", false, "", "", false);
        assertTrue(air.verdict().lite());
        assertEquals(List.of("Apple M1 graphics, built into the processor"), air.verdict().reasons());
        Hardware pro = new Hardware("Mac OS X", "aarch64", 10, 16 * GB, "Apple M1 Pro", false, "", "", false);
        assertFalse(pro.verdict().lite());
    }

    @Test
    void anIntelJavaUnderRosettaIsLite() {
        Hardware h = new Hardware("Mac OS X", "x86_64", 10, 16 * GB, "Apple M1 Pro", true, "Apple", "Apple M1 Pro",
                false);
        assertTrue(h.verdict().lite());
        assertTrue(h.verdict().reasons().get(0).contains("Rosetta"));
        assertTrue(h.describe().endsWith("(Rosetta)"));
    }

    @Test
    void aGamingPcGetsTheFullGraphics() {
        Hardware.Verdict v = pc("NVIDIA GeForce RTX 3070/PCIe/SSE2", "NVIDIA Corporation", 32 * GB, 16).verdict();
        assertFalse(v.lite());
        assertEquals("Full", v.describe());
        assertFalse(pc("AMD Radeon RX 6800 XT", "ATI Technologies Inc.", 32 * GB, 16).verdict().lite());
        assertFalse(pc("Intel(R) Arc(TM) A770 Graphics", "Intel", 32 * GB, 16).verdict().lite());
        assertFalse(pc("NVIDIA GeForce GTX 1060 6GB/PCIe/SSE2", "NVIDIA Corporation", 16 * GB, 8).verdict().lite());
    }

    @Test
    void graphicsBuiltIntoTheProcessorAreLowPower() {
        assertTrue(Hardware.lowPowerGpu("Intel(R) UHD Graphics 620", "Intel"));
        assertTrue(Hardware.lowPowerGpu("Intel(R) Iris(R) Xe Graphics", "Intel"));
        assertTrue(Hardware.lowPowerGpu("Intel Iris Plus Graphics OpenGL Engine", "Intel Inc."));
        assertTrue(Hardware.lowPowerGpu("Intel(R) HD Graphics 6000", "Intel"));
        assertTrue(Hardware.lowPowerGpu("Intel(R) Arc(TM) Graphics", "Intel"), "a Core Ultra's, in the processor");
        assertTrue(Hardware.lowPowerGpu("Mesa Intel(R) UHD Graphics 620 (KBL GT2)", "Intel"));
        assertTrue(Hardware.lowPowerGpu("AMD Radeon(TM) Graphics", "ATI Technologies Inc."));
        assertTrue(Hardware.lowPowerGpu("AMD Radeon Graphics (renoir, LLVM 15.0.7, DRM 3.49, 6.1.0)", "AMD"));
        assertTrue(Hardware.lowPowerGpu("AMD Radeon 680M", "ATI Technologies Inc."));
        assertTrue(Hardware.lowPowerGpu("AMD Radeon(TM) Vega 8 Graphics", "ATI Technologies Inc."));
        assertTrue(Hardware.lowPowerGpu("AMD Custom GPU 0405 (vangogh, LLVM 15.0.7)", "AMD"), "a Steam Deck");
        assertTrue(Hardware.lowPowerGpu("Apple M1", "Apple"));
        assertTrue(Hardware.lowPowerGpu("V3D 4.2", "Broadcom"), "a Raspberry Pi");
        assertTrue(Hardware.lowPowerGpu("Mali-G78", "ARM"));
    }

    @Test
    void entryLevelCardsAreLowPowerAndTheRestAreNot() {
        assertTrue(Hardware.lowPowerGpu("NVIDIA GeForce MX450/PCIe/SSE2", "NVIDIA Corporation"));
        assertTrue(Hardware.lowPowerGpu("NVIDIA GeForce GT 1030/PCIe/SSE2", "NVIDIA Corporation"));
        assertTrue(Hardware.entryCard("NVIDIA GeForce MX450/PCIe/SSE2"));
        assertFalse(Hardware.lowPowerGpu("NVIDIA GeForce RTX 4090/PCIe/SSE2", "NVIDIA Corporation"));
        assertFalse(Hardware.lowPowerGpu("AMD Radeon Pro 5500M OpenGL Engine", "ATI Technologies Inc."));
        assertFalse(Hardware.lowPowerGpu("", ""), "not known yet is not low-end");
        Hardware laptop = pc("NVIDIA GeForce MX450/PCIe/SSE2", "NVIDIA Corporation", 16 * GB, 8);
        assertEquals(List.of("NVIDIA GeForce MX450, an entry-level graphics card"), laptop.verdict().reasons());
    }

    @Test
    void softwareGraphicsAreLowEnd() {
        Hardware h = new Hardware("Linux", "amd64", 16, 32 * GB, "", false, "Mesa",
                "llvmpipe (LLVM 15.0.7, 256 bits)", false);
        assertEquals(List.of("the graphics are drawn in software, with no graphics driver"), h.verdict().reasons());
        Hardware said = new Hardware("Linux", "amd64", 16, 32 * GB, "", false, "", "Some GPU", true);
        assertTrue(said.verdict().lite());
    }

    @Test
    void littleMemoryOrFewThreadsAreLowEndWhateverTheGraphics() {
        String rtx = "NVIDIA GeForce RTX 3060/PCIe/SSE2";
        assertTrue(pc(rtx, "NVIDIA Corporation", 8 * GB, 12).verdict().lite());
        assertTrue(pc(rtx, "NVIDIA Corporation", 7_900_000_000L, 12).verdict().lite(), "8 GB less what is kept back");
        assertEquals(List.of("4 processor threads"), pc(rtx, "NVIDIA Corporation", 16 * GB, 4).verdict().reasons());
        assertFalse(pc(rtx, "NVIDIA Corporation", 0, 0).verdict().lite(), "what cannot be told does not count");
    }

    @Test
    void oneLineSaysWhatTheMachineIs() {
        assertEquals("Apple M1, Apple M1 graphics, 8 GB, 8 threads, Mac OS X aarch64", mac("Apple M1", 8 * GB, 8).describe());
        Hardware pc = new Hardware("Windows 11", "amd64", 8, 16 * GB, "Intel(R) Core(TM) i5-8250U CPU @ 1.60GHz", false,
                "Intel", "Intel(R) UHD Graphics 620", false);
        assertEquals("Intel(R) Core(TM) i5-8250U CPU @ 1.60GHz, Intel(R) UHD Graphics 620, 16 GB, 8 threads, "
                + "Windows 11 amd64", pc.describe());
        assertFalse(pc.describe().contains("·"), "the console of some systems cannot print it");
    }

    @Test
    void theProcessorsNameIsTheModelNameNotItsNumber() {
        assertEquals("Intel(R) Xeon(R) CPU @ 2.20GHz", Hardware.cpuName(List.of(
                "processor\t: 0", "vendor_id\t: GenuineIntel", "cpu family\t: 6", "model\t\t: 207",
                "model name\t: Intel(R) Xeon(R) CPU @ 2.20GHz", "stepping\t: 2")));
        assertEquals("Raspberry Pi 4 Model B Rev 1.4", Hardware.cpuName(List.of(
                "processor\t: 0", "BogoMIPS\t: 108.00", "CPU part\t: 0xd08", "", "Revision\t: c03114",
                "Model\t\t: Raspberry Pi 4 Model B Rev 1.4")));
        assertEquals("", Hardware.cpuName(List.of("model\t\t: 207")));
    }

    @Test
    void theGraphicsChipsNameLosesTheDriversDetails() {
        assertEquals("NVIDIA GeForce MX450", pc("NVIDIA GeForce MX450/PCIe/SSE2", "", 0, 0).shortGpu());
        assertEquals("AMD Radeon Graphics",
                pc("AMD Radeon Graphics (renoir, LLVM 15.0.7, DRM 3.49, 6.1.0)", "", 0, 0).shortGpu());
    }
}
